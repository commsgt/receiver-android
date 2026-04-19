package org.opendroneid.rpi.scanner;

import org.opendroneid.android.bluetooth.OpenDroneIdDataManager;
import org.opendroneid.android.log.LogMessageEntry;
import org.opendroneid.android.log.LogWriter;
import org.pcap4j.core.*;
import org.pcap4j.packet.IllegalRawDataException;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.logging.Logger;

/**
 * WiFi beacon scanner for Linux using pcap4j in monitor mode.
 *
 * Prerequisites on RPi:
 *   sudo ip link set wlan1 down
 *   sudo iw dev wlan1 set type monitor
 *   sudo ip link set wlan1 up
 *
 * Captures 802.11 beacon frames and extracts vendor-specific IEs (ID=221)
 * with DRI CID 0xFA:0x0B:0xBC and type 0x0D per the ASTM F3411-22 spec.
 */
public class WiFiScanner {
    private static final Logger LOGGER = Logger.getLogger(WiFiScanner.class.getName());

    private static final int VENDOR_IE_ID = 221;
    private static final int[] DRI_CID = {0xFA, 0x0B, 0xBC};
    private static final int DRI_VENDOR_TYPE = 0x0D;
    private static final int DRI_START_BYTE_OFFSET = 4; // skip 3-byte CID + 1-byte type

    private final String interfaceName;
    private final OpenDroneIdDataManager dataManager;
    private final LogWriter logger;
    private volatile boolean running = false;
    private Thread captureThread;
    private PcapHandle handle;

    public WiFiScanner(OpenDroneIdDataManager dataManager, LogWriter logger, String interfaceName) {
        this.dataManager = dataManager;
        this.logger = logger;
        this.interfaceName = interfaceName;
    }

    public void start() {
        running = true;
        captureThread = new Thread(this::runCapture, "WiFiScanner");
        captureThread.setDaemon(true);
        captureThread.start();
        LOGGER.info("WiFi scanner started on " + interfaceName);
    }

    public void stop() {
        running = false;
        if (handle != null && handle.isOpen()) handle.breakLoop();
        if (captureThread != null) captureThread.interrupt();
        LOGGER.info("WiFi scanner stopped");
    }

    private void runCapture() {
        try {
            PcapNetworkInterface nif = Pcaps.getDevByName(interfaceName);
            if (nif == null) {
                LOGGER.severe("WiFi interface not found: " + interfaceName + " (is it in monitor mode?)");
                return;
            }
            handle = nif.openLive(65536, PcapNetworkInterface.PromiscuousMode.PROMISCUOUS, 100);
            // Capture only management frames (beacon = type 0, subtype 8 → frame control byte 0 = 0x80)
            handle.setFilter("type mgt subtype beacon", BpfProgram.BpfCompileMode.OPTIMIZE);

            LOGGER.info("WiFi capture running on " + interfaceName + " (monitor mode required)");

            handle.loop(-1, (RawPacketListener) rawPacket -> {
                if (!running) return;
                try {
                    processFrame(rawPacket.getRawData());
                } catch (Exception e) {
                    LOGGER.fine("Frame processing error: " + e.getMessage());
                }
            });
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            if (running) LOGGER.severe("WiFi scanner error: " + e.getMessage());
        } finally {
            if (handle != null && handle.isOpen()) handle.close();
        }
    }

    private void processFrame(byte[] rawData) {
        // Skip radiotap header to reach 802.11 frame
        // Radiotap header: first 2 bytes = revision+pad, bytes 2-3 = length (little endian)
        if (rawData.length < 4) return;
        int radiotapLen = ((rawData[2] & 0xFF)) | ((rawData[3] & 0xFF) << 8);
        int rssi = extractRssi(rawData, radiotapLen);

        if (rawData.length < radiotapLen + 36) return; // min beacon frame size
        int dot11Start = radiotapLen;

        // 802.11 beacon: 2 frame ctrl + 2 duration + 6 DA + 6 SA + 6 BSSID + 2 seq + 8 timestamp
        //                + 2 beacon interval + 2 cap info = 36 bytes fixed header
        // IE elements start at dot11Start + 36
        String bssid = extractBssid(rawData, dot11Start);
        int ieOffset = dot11Start + 36;
        parseInformationElements(rawData, ieOffset, bssid, rssi);
    }

    private void parseInformationElements(byte[] data, int offset, String bssid, int rssi) {
        while (offset + 2 <= data.length) {
            int id = data[offset] & 0xFF;
            int len = data[offset + 1] & 0xFF;
            if (offset + 2 + len > data.length) break;

            if (id == VENDOR_IE_ID && len >= DRI_START_BYTE_OFFSET + 1) {
                checkVendorIE(data, offset + 2, len, bssid, rssi);
            }
            offset += 2 + len;
        }
    }

    private void checkVendorIE(byte[] data, int start, int len, String bssid, int rssi) {
        if (len < DRI_START_BYTE_OFFSET) return;
        if ((data[start] & 0xFF) != DRI_CID[0]) return;
        if ((data[start + 1] & 0xFF) != DRI_CID[1]) return;
        if ((data[start + 2] & 0xFF) != DRI_CID[2]) return;
        if ((data[start + 3] & 0xFF) != DRI_VENDOR_TYPE) return;

        // DRI payload: from DriStartByteOffset → [msgCounter][25-byte message]
        byte[] arr = Arrays.copyOfRange(data, start + DRI_START_BYTE_OFFSET, start + len);
        if (arr.length < 26) return; // need counter + 25-byte message

        long timeNano = System.nanoTime();
        long macLong = macToLong(bssid);
        LogMessageEntry logEntry = new LogMessageEntry();

        dataManager.receiveDataWiFiBeacon(arr, bssid, macLong, rssi, timeNano, logEntry, "Beacon");

        if (logger != null) {
            StringBuilder csvLog = logEntry.getMessageLogEntry();
            logger.logBeacon(logEntry.getMsgVersion(), timeNano, bssid, rssi, arr, "Beacon", csvLog);
        }
    }

    private String extractBssid(byte[] data, int dot11Start) {
        // BSSID is at offset dot11Start + 16 (after FC+Dur+DA+SA)
        if (data.length < dot11Start + 22) return "00:00:00:00:00:00";
        int base = dot11Start + 16;
        return String.format("%02X:%02X:%02X:%02X:%02X:%02X",
                data[base] & 0xFF, data[base + 1] & 0xFF, data[base + 2] & 0xFF,
                data[base + 3] & 0xFF, data[base + 4] & 0xFF, data[base + 5] & 0xFF);
    }

    private int extractRssi(byte[] radiotap, int headerLen) {
        // Radiotap RSSI is in the ANTENNA_SIGNAL field (type 5)
        // Present flags start at byte 4 (32-bit LE). Bit 5 = ANTENNA_SIGNAL.
        if (radiotap.length < 8) return 0;
        int presentFlags = ByteBuffer.wrap(radiotap, 4, 4).order(java.nio.ByteOrder.LITTLE_ENDIAN).getInt();
        int offset = 8;
        // Skip fields before ANTENNA_SIGNAL (bit 5):
        // TSFT (bit 0) = 8 bytes, FLAGS (bit 1) = 1 byte, RATE (bit 2) = 1 byte,
        // CHANNEL (bit 3) = 4 bytes, FHSS (bit 4) = 2 bytes
        if ((presentFlags & 0x01) != 0) offset += 8; // TSFT
        if ((presentFlags & 0x02) != 0) offset += 1; // FLAGS
        if ((presentFlags & 0x04) != 0) offset += 1; // RATE
        if ((presentFlags & 0x08) != 0) { offset = (offset + 1) & ~1; offset += 4; } // CHANNEL (align 2)
        if ((presentFlags & 0x10) != 0) offset += 2; // FHSS
        if ((presentFlags & 0x20) != 0 && offset < headerLen) {
            return (int) ((byte) radiotap[offset]); // ANTENNA_SIGNAL (signed byte, dBm)
        }
        return 0;
    }

    private long macToLong(String mac) {
        return Long.parseLong(mac.replace(":", ""), 16);
    }
}
