package org.opendroneid.rpi.scanner;

import org.opendroneid.android.bluetooth.OpenDroneIdDataManager;
import org.opendroneid.android.log.LogMessageEntry;
import org.opendroneid.android.log.LogWriter;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.logging.Logger;

/**
 * BLE scanner for Linux using a Python/bleak subprocess.
 *
 * Spawns ble_scan.py which uses BlueZ via the bleak library.
 * Each line from the subprocess is:  <MAC>  <RSSI>  <HEX_SERVICE_DATA>
 *
 * Service data bytes from bleak = [0x0D][msgCounter][25-byte F3411-22 msg]
 * → passed to parseData() at offset 2.
 *
 * Prerequisites:
 *   pip3 install bleak
 *   ble_scan.py must be in the same directory as the JAR, or set BLE_SCRIPT env var.
 */
public class BleScanner {
    private static final Logger LOGGER = Logger.getLogger(BleScanner.class.getName());

    private final OpenDroneIdDataManager dataManager;
    private final LogWriter logger;
    private final String scriptPath;
    private volatile boolean running = false;
    private Thread scanThread;
    private Process proc;

    public BleScanner(OpenDroneIdDataManager dataManager, LogWriter logger) {
        this.dataManager = dataManager;
        this.logger = logger;
        // Allow override via env var; default to ble_scan.py next to the JAR
        String env = System.getenv("BLE_SCRIPT");
        this.scriptPath = (env != null) ? env : locateScript();
    }

    private String locateScript() {
        // Look next to the running JAR first, then current working directory
        String jarDir = new File(BleScanner.class.getProtectionDomain()
                .getCodeSource().getLocation().getPath()).getParent();
        File candidate = new File(jarDir, "ble_scan.py");
        if (candidate.exists()) return candidate.getAbsolutePath();
        return "ble_scan.py"; // fall back to CWD
    }

    public void start() {
        if (!new File(scriptPath).exists() && !scriptPath.equals("ble_scan.py")) {
            LOGGER.severe("BLE script not found: " + scriptPath);
            LOGGER.severe("Copy ble_scan.py next to the JAR, or set BLE_SCRIPT env var.");
            return;
        }
        running = true;
        scanThread = new Thread(this::runScan, "BleScanner");
        scanThread.setDaemon(true);
        scanThread.start();
        LOGGER.info("BLE scanner started (script: " + scriptPath + ")");
    }

    public void stop() {
        running = false;
        if (proc != null) proc.destroyForcibly();
        if (scanThread != null) scanThread.interrupt();
        LOGGER.info("BLE scanner stopped");
    }

    private void runScan() {
        while (running) {
            try {
                proc = new ProcessBuilder("python3", scriptPath)
                        .redirectErrorStream(false)
                        .start();

                // Forward stderr to our logger
                Thread errThread = new Thread(() -> {
                    try (BufferedReader err = new BufferedReader(
                            new InputStreamReader(proc.getErrorStream()))) {
                        String line;
                        while ((line = err.readLine()) != null) {
                            LOGGER.warning("[ble_scan] " + line);
                        }
                    } catch (Exception ignored) {}
                }, "BleScanner-stderr");
                errThread.setDaemon(true);
                errThread.start();

                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(proc.getInputStream()))) {
                    String line;
                    while (running && (line = reader.readLine()) != null) {
                        parseLine(line.trim());
                    }
                }

                int exit = proc.waitFor();
                if (running) {
                    LOGGER.warning("ble_scan.py exited with code " + exit + " — restarting in 3s");
                    Thread.sleep(3000);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                if (running) {
                    LOGGER.severe("BLE scanner error: " + e.getMessage() + " — restarting in 3s");
                    try { Thread.sleep(3000); } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt(); return;
                    }
                }
            }
        }
    }

    private void parseLine(String line) {
        if (line.isEmpty()) return;
        // Format: AA:BB:CC:DD:EE:FF -65 0d00120100...
        String[] parts = line.split("\\s+");
        if (parts.length < 3) return;

        String macAddress = parts[0];
        int rssi;
        try {
            rssi = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            return;
        }

        byte[] data = hexToBytes(parts[2]);
        if (data == null || data.length < 27) return; // need 0x0D + counter + 25-byte msg

        long timestampNanos = System.nanoTime();
        LogMessageEntry logEntry = new LogMessageEntry();

        dataManager.receiveDataBluetooth(data, macAddress, rssi, timestampNanos, logEntry, "BT5");

        if (logger != null) {
            StringBuilder csvLog = logEntry.getMessageLogEntry();
            logger.logBluetooth(logEntry.getMsgVersion(), timestampNanos, macAddress, rssi, data, "BT5", csvLog);
        }
    }

    private static byte[] hexToBytes(String hex) {
        if (hex == null || hex.length() % 2 != 0) return null;
        byte[] result = new byte[hex.length() / 2];
        try {
            for (int i = 0; i < result.length; i++) {
                result[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
            }
        } catch (NumberFormatException e) {
            return null;
        }
        return result;
    }
}
