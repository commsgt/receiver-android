#!/usr/bin/env python3
"""
BLE scanner helper for OpenDroneID RPi receiver.
Scans for Remote ID advertisements (UUID 0xFFFA) using bleak (BlueZ backend).

Install: pip3 install bleak
Run via BleScanner.java subprocess — do not run directly.

Output format per line (stdout, flushed):
  <MAC>  <RSSI>  <HEX_SERVICE_DATA>
  e.g.:  AA:BB:CC:DD:EE:FF  -65  0d00120100...

The service data bytes are the raw payload after the UUID:
  [0x0D][msgCounter][25-byte ASTM F3411-22 message]
"""
import asyncio
import sys

try:
    from bleak import BleakScanner
except ImportError:
    print("ERROR: bleak not installed. Run: pip3 install bleak", file=sys.stderr, flush=True)
    sys.exit(1)

REMOTE_ID_UUID = "0000fffa-0000-1000-8000-00805f9b34fb"

def on_advertisement(device, adv):
    if REMOTE_ID_UUID not in (adv.service_data or {}):
        return
    data = adv.service_data[REMOTE_ID_UUID]
    if len(data) < 27:   # 1 (AD code) + 1 (counter) + 25 (message)
        return
    rssi = adv.rssi if adv.rssi is not None else 0
    print(f"{device.address} {rssi} {data.hex()}", flush=True)

async def main():
    async with BleakScanner(on_advertisement,
                            service_uuids=[REMOTE_ID_UUID]) as scanner:
        while True:
            await asyncio.sleep(3600)

if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        pass
