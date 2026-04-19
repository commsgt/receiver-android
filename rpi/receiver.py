#!/usr/bin/env python3
"""
OpenDroneID receiver for Raspberry Pi 4
Supports: BLE (bleak/BlueZ), WiFi beacon (scapy monitor mode), GPS (gpsd)
Protocol: ASTM F3411-22 / OpenDroneID — parsing is identical to the Android app

Usage:
  sudo python3 receiver.py [--wifi wlan1] [--no-ble] [--no-wifi] [--log]

Install deps once:
  sudo pip3 install --break-system-packages bleak scapy gpsd-py3
"""

import argparse
import asyncio
import csv
import math
import struct
import sys
import threading
import time
from datetime import datetime

# ─── Constants (ASTM F3411-22) ───────────────────────────────────────────────

REMOTE_ID_UUID    = "0000fffa-0000-1000-8000-00805f9b34fb"
VENDOR_CID        = bytes([0xFA, 0x0B, 0xBC])
VENDOR_TYPE       = 0x0D
DRI_PAYLOAD_OFFSET = 4      # skip 3-byte CID + 1-byte type

MAX_MESSAGE_SIZE         = 25
MAX_ID_BYTE_SIZE         = 20
MAX_STRING_BYTE_SIZE     = 23
MAX_AUTH_DATA_PAGES      = 16
MAX_AUTH_PAGE_ZERO_SIZE  = 17
MAX_AUTH_PAGE_NON_ZERO   = 23
MAX_AUTH_DATA            = MAX_AUTH_PAGE_ZERO_SIZE + (MAX_AUTH_DATA_PAGES - 1) * MAX_AUTH_PAGE_NON_ZERO
LAT_LON_MULT             = 1e-7
SPEED_VERT_MULT          = 0.5

# ─── Parser (ASTM F3411-22) ──────────────────────────────────────────────────

def parse_data(data: bytes, offset: int, receiver_loc=None):
    """
    offset: data[offset-1] = msg_counter, data[offset:] = 25-byte message
    BlueZ ServiceData  → offset 2  (data = [0x0D][counter][25-byte msg])
    WiFi vendor IE     → offset 1  (data = [counter][25-byte msg])
    """
    if offset <= 0 or len(data) < offset + MAX_MESSAGE_SIZE:
        return None
    counter = data[offset - 1]
    return _parse_message(data, offset, counter, receiver_loc)


def _parse_message(data: bytes, offset: int, counter: int, receiver_loc):
    if len(data) < offset + MAX_MESSAGE_SIZE:
        return None
    b = data[offset]
    msg_type = (b & 0xF0) >> 4
    version  = b & 0x0F
    payload  = data[offset + 1 : offset + MAX_MESSAGE_SIZE]

    parsers = {
        0x0: _parse_basic_id,
        0x1: lambda p: _parse_location(p, receiver_loc),
        0x2: _parse_authentication,
        0x3: _parse_self_id,
        0x4: _parse_system,
        0x5: _parse_operator_id,
        0xF: lambda p: _parse_message_pack(data, offset, counter, receiver_loc),
    }
    fn = parsers.get(msg_type)
    if fn is None:
        return None
    result = fn(payload)
    if result is None:
        return None
    return {'type': msg_type, 'version': version, 'counter': counter, 'data': result}


def _parse_basic_id(p: bytes):
    t = p[0]
    return {
        'id_type': (t & 0xF0) >> 4,
        'ua_type': t & 0x0F,
        'uas_id':  p[1:1 + MAX_ID_BYTE_SIZE].rstrip(b'\x00').decode('utf-8', errors='replace'),
    }


def _calc_altitude(raw: int) -> float:
    return raw / 2.0 - 1000.0

def _calc_speed(value: int, mult: int) -> float:
    return value * 0.25 if mult == 0 else (value * 0.75) + (255 * 0.25)

def _haversine(lat1, lon1, lat2, lon2) -> float:
    R = 6371000.0
    phi1, phi2 = math.radians(lat1), math.radians(lat2)
    dphi = math.radians(lat2 - lat1)
    dlam = math.radians(lon2 - lon1)
    a = math.sin(dphi/2)**2 + math.cos(phi1)*math.cos(phi2)*math.sin(dlam/2)**2
    return R * 2 * math.atan2(math.sqrt(a), math.sqrt(1 - a))


def _parse_location(p: bytes, receiver_loc):
    b = p[0]
    ew   = (b & 0x02) >> 1
    mult = b & 0x01
    direction_raw = p[1]
    speed_h_raw   = p[2]
    speed_v_raw   = struct.unpack_from('<b', p, 3)[0]
    lat_raw, lon_raw = struct.unpack_from('<ii', p, 4)
    alt_press_raw, alt_geo_raw, height_raw = struct.unpack_from('<HHH', p, 12)
    hori_vert = p[18]
    speed_baro = p[19]
    ts_raw = struct.unpack_from('<H', p, 20)[0]
    time_acc = p[22] & 0x0F

    lat = LAT_LON_MULT * lat_raw
    lon = LAT_LON_MULT * lon_raw
    direction = direction_raw + (180 if ew else 0)

    distance = None
    if receiver_loc and lat != 0 and lon != 0:
        distance = _haversine(receiver_loc['lat'], receiver_loc['lon'], lat, lon)

    return {
        'status':       (b & 0xF0) >> 4,
        'height_type':  (b & 0x04) >> 2,
        'direction':    direction,
        'speed_h':      _calc_speed(speed_h_raw, mult),
        'speed_v':      SPEED_VERT_MULT * speed_v_raw,
        'lat':          lat,
        'lon':          lon,
        'alt_pressure': _calc_altitude(alt_press_raw),
        'alt_geodetic': _calc_altitude(alt_geo_raw),
        'height':       _calc_altitude(height_raw),
        'h_accuracy':   hori_vert & 0x0F,
        'v_accuracy':   (hori_vert & 0xF0) >> 4,
        'baro_accuracy':(speed_baro & 0xF0) >> 4,
        'spd_accuracy': speed_baro & 0x0F,
        'timestamp':    ts_raw,
        'time_accuracy':time_acc * 0.1,
        'distance':     distance,
    }


def _parse_authentication(p: bytes):
    t = p[0]
    auth_type = (t & 0xF0) >> 4
    page      = t & 0x0F
    if page == 0:
        last_page = p[1]
        length    = p[2]
        timestamp = struct.unpack_from('<I', p, 3)[0]
        auth_data = p[7:7 + MAX_AUTH_PAGE_ZERO_SIZE]
    else:
        last_page = timestamp = length = None
        auth_data = p[1:1 + MAX_AUTH_PAGE_NON_ZERO]
    return {'auth_type': auth_type, 'page': page, 'last_page': last_page,
            'length': length, 'auth_ts': timestamp, 'auth_data': auth_data.hex()}


def _parse_self_id(p: bytes):
    desc_type = p[0]
    desc = p[1:1 + MAX_STRING_BYTE_SIZE].rstrip(b'\x00').decode('utf-8', errors='replace')
    return {'desc_type': desc_type, 'description': desc}


def _parse_system(p: bytes):
    b = p[0]
    op_lat_raw, op_lon_raw = struct.unpack_from('<ii', p, 1)
    area_count = struct.unpack_from('<H', p, 9)[0]
    area_radius = p[11]
    area_ceil_raw, area_floor_raw = struct.unpack_from('<HH', p, 12)
    cat_class = p[16]
    op_alt_raw = struct.unpack_from('<H', p, 17)[0]
    sys_ts = struct.unpack_from('<I', p, 19)[0]
    return {
        'op_loc_type':   b & 0x03,
        'class_type':   (b & 0x1C) >> 2,
        'op_lat':        LAT_LON_MULT * op_lat_raw,
        'op_lon':        LAT_LON_MULT * op_lon_raw,
        'area_count':    area_count,
        'area_radius':   area_radius * 10,
        'area_ceiling':  _calc_altitude(area_ceil_raw),
        'area_floor':    _calc_altitude(area_floor_raw),
        'category':     (cat_class & 0xF0) >> 4,
        'class_value':   cat_class & 0x0F,
        'op_alt_geo':    _calc_altitude(op_alt_raw),
        'sys_timestamp': sys_ts,
    }


def _parse_operator_id(p: bytes):
    op_id_type = p[0]
    op_id = p[1:1 + MAX_ID_BYTE_SIZE].rstrip(b'\x00').decode('utf-8', errors='replace')
    return {'op_id_type': op_id_type, 'operator_id': op_id}


def _parse_message_pack(data: bytes, offset: int, counter: int, receiver_loc):
    p = data[offset + 1:]
    if len(p) < 2:
        return None
    msg_size = p[0]
    count    = p[1]
    if msg_size != MAX_MESSAGE_SIZE or count <= 0 or count > 9:
        return None
    messages = []
    for i in range(count):
        start = 2 + i * msg_size
        if start + msg_size > len(p):
            break
        chunk = p[start:start + msg_size]
        sub_counter = chunk[0] if len(chunk) > 0 else 0
        msg = _parse_message(chunk, 0, counter, receiver_loc)
        if msg:
            messages.append(msg)
    return {'messages': messages}


# ─── Aircraft store ──────────────────────────────────────────────────────────

class Aircraft:
    def __init__(self, mac: str):
        self.mac        = mac
        self.transport  = None
        self.rssi       = 0
        self.first_seen = time.time()
        self.last_seen  = time.time()
        self.basic_id   = None
        self.location   = None
        self.system     = None
        self.self_id    = None
        self.operator_id= None
        self.auth       = {}

    def update(self, msg: dict, rssi: int, transport: str):
        self.last_seen = time.time()
        self.rssi      = rssi
        self.transport = transport
        t = msg['type']
        d = msg['data']
        if t == 0x0: self.basic_id    = d
        elif t == 0x1: self.location  = d
        elif t == 0x2: self.auth[d.get('page', 0)] = d
        elif t == 0x3: self.self_id   = d
        elif t == 0x4: self.system    = d
        elif t == 0x5: self.operator_id = d
        elif t == 0xF:
            for sub in (d.get('messages') or []):
                self.update(sub, rssi, transport)

    def print_update(self):
        loc = self.location
        if not loc:
            return
        print(f"[{self.transport:7}] {self.mac}  "
              f"lat={loc['lat']:10.6f}  lon={loc['lon']:11.6f}  "
              f"alt={loc['alt_geodetic']:7.1f}m  "
              f"spd={loc['speed_h']:5.1f}m/s  "
              f"rssi={self.rssi:4}dBm"
              + (f"  dist={loc['distance']:.0f}m" if loc.get('distance') else ""),
              flush=True)


class DataManager:
    def __init__(self):
        self.aircraft       = {}          # mac → Aircraft
        self.receiver_loc   = None
        self._lock          = threading.Lock()

    def receive(self, data: bytes, offset: int, mac: str, rssi: int,
                transport: str, logger=None):
        msg = parse_data(data, offset, self.receiver_loc)
        if msg is None:
            return
        with self._lock:
            ac = self.aircraft.get(mac)
            if ac is None:
                ac = Aircraft(mac)
                self.aircraft[mac] = ac
                print(f"[NEW] {mac} via {transport}", flush=True)
            ac.update(msg, rssi, transport)
            ac.print_update()
            if logger:
                logger.write(ac, msg, transport)


# ─── GPS reader (gpsd) ───────────────────────────────────────────────────────

def gps_reader(data_manager: DataManager):
    try:
        import gpsd
    except ImportError:
        print("[GPS] gpsd-py3 not installed — GPS disabled", flush=True)
        return
    while True:
        try:
            gpsd.connect()
            print("[GPS] Connected to gpsd", flush=True)
            while True:
                fix = gpsd.get_current()
                if fix.mode >= 2:
                    data_manager.receiver_loc = {
                        'lat': fix.lat,
                        'lon': fix.lon,
                        'alt': fix.alt if fix.mode >= 3 else 0,
                    }
                time.sleep(1)
        except Exception as e:
            print(f"[GPS] Error: {e} — retrying in 5s", flush=True)
            time.sleep(5)


# ─── BLE scanner (bleak) ─────────────────────────────────────────────────────

def ble_scanner(data_manager: DataManager, logger=None):
    try:
        from bleak import BleakScanner
    except ImportError:
        print("[BLE] bleak not installed — BLE disabled", flush=True)
        return

    async def run():
        def on_advertisement(device, adv):
            sd = adv.service_data or {}
            if REMOTE_ID_UUID not in sd:
                return
            raw = bytes(sd[REMOTE_ID_UUID])
            if len(raw) < 27:
                return
            # raw = [0x0D][counter][25-byte msg]  → offset=2
            data_manager.receive(raw, 2, device.address,
                                 adv.rssi or 0, "BT5", logger)

        print("[BLE] Scanning for Remote ID advertisements...", flush=True)
        async with BleakScanner(on_advertisement,
                                service_uuids=[REMOTE_ID_UUID]):
            while True:
                await asyncio.sleep(3600)

    asyncio.run(run())


# ─── WiFi beacon scanner (scapy) ─────────────────────────────────────────────

def wifi_scanner(data_manager: DataManager, iface: str, logger=None):
    try:
        from scapy.all import sniff, Dot11, Dot11Beacon, RadioTap
        from scapy.layers.dot11 import Dot11EltVendorSpecific
    except ImportError:
        print("[WiFi] scapy not installed — WiFi disabled", flush=True)
        return

    print(f"[WiFi] Capturing on {iface} (monitor mode required)...", flush=True)

    def handle_packet(pkt):
        if not pkt.haslayer(Dot11Beacon):
            return
        bssid = pkt[Dot11].addr3 or "00:00:00:00:00:00"
        rssi  = pkt[RadioTap].dBm_AntSignal if pkt.haslayer(RadioTap) else 0

        # Walk information elements looking for Vendor Specific (ID=221)
        elt = pkt.getlayer('Dot11Elt')
        while elt:
            if elt.ID == 221 and len(elt.info) > DRI_PAYLOAD_OFFSET:
                info = bytes(elt.info)
                if (info[0] == VENDOR_CID[0] and info[1] == VENDOR_CID[1]
                        and info[2] == VENDOR_CID[2] and info[3] == VENDOR_TYPE):
                    # DRI payload: [counter][25-byte msg]  → offset=1
                    payload = info[DRI_PAYLOAD_OFFSET:]
                    if len(payload) >= 26:
                        data_manager.receive(payload, 1, bssid.upper(),
                                             rssi, "Beacon", logger)
            elt = elt.payload.getlayer('Dot11Elt') if elt.payload else None

    sniff(iface=iface, prn=handle_packet, store=False,
          lfilter=lambda p: p.haslayer(Dot11Beacon))


# ─── CSV logger ──────────────────────────────────────────────────────────────

class CsvLogger:
    def __init__(self, path: str):
        self._f = open(path, 'w', newline='')
        self._w = csv.writer(self._f)
        self._w.writerow(['timestamp', 'mac', 'transport', 'rssi',
                          'lat', 'lon', 'alt_geodetic', 'speed_h', 'speed_v',
                          'direction', 'height', 'uas_id', 'ua_type',
                          'operator_id', 'description', 'op_lat', 'op_lon', 'distance'])

    def write(self, ac: Aircraft, msg: dict, transport: str):
        loc = ac.location or {}
        bid = ac.basic_id or {}
        sys = ac.system or {}
        sid = ac.self_id or {}
        oid = ac.operator_id or {}
        self._w.writerow([
            datetime.utcnow().isoformat(), ac.mac, transport, ac.rssi,
            loc.get('lat',''), loc.get('lon',''), loc.get('alt_geodetic',''),
            loc.get('speed_h',''), loc.get('speed_v',''), loc.get('direction',''),
            loc.get('height',''), bid.get('uas_id',''), bid.get('ua_type',''),
            oid.get('operator_id',''), sid.get('description',''),
            sys.get('op_lat',''), sys.get('op_lon',''), loc.get('distance',''),
        ])
        self._f.flush()

    def close(self):
        self._f.close()


# ─── Main ─────────────────────────────────────────────────────────────────────

def main():
    ap = argparse.ArgumentParser(description='OpenDroneID receiver for RPi4')
    ap.add_argument('--wifi',     default='wlan0',  help='WiFi interface in monitor mode')
    ap.add_argument('--no-ble',   action='store_true')
    ap.add_argument('--no-wifi',  action='store_true')
    ap.add_argument('--no-gps',   action='store_true')
    ap.add_argument('--log',      action='store_true', help='Write CSV log file')
    args = ap.parse_args()

    dm = DataManager()

    logger = None
    if args.log:
        ts = datetime.utcnow().strftime('%Y%m%d_%H%M%S')
        path = f'opendroneID_{ts}.csv'
        logger = CsvLogger(path)
        print(f"Logging to {path}", flush=True)

    threads = []

    if not args.no_gps:
        t = threading.Thread(target=gps_reader, args=(dm,), daemon=True)
        t.start(); threads.append(t)

    if not args.no_ble:
        t = threading.Thread(target=ble_scanner, args=(dm, logger), daemon=True)
        t.start(); threads.append(t)

    if not args.no_wifi:
        t = threading.Thread(target=wifi_scanner, args=(dm, args.wifi, logger), daemon=True)
        t.start(); threads.append(t)

    print(f"OpenDroneID receiver running.")
    print(f"  BLE:  {'disabled' if args.no_ble  else 'hci0'}")
    print(f"  WiFi: {'disabled' if args.no_wifi else args.wifi}")
    print(f"  GPS:  {'disabled' if args.no_gps  else 'gpsd @ localhost:2947'}")
    print("Press Ctrl+C to stop.\n", flush=True)

    try:
        while True:
            time.sleep(1)
    except KeyboardInterrupt:
        print("\nShutting down...")
        if logger:
            logger.close()


if __name__ == '__main__':
    main()
