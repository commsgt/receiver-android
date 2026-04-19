package org.opendroneid.android.log;

import org.opendroneid.android.Constants;
import org.opendroneid.android.bluetooth.OpenDroneIdParser;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Logger;

public class LogWriter {
    private static final Logger LOGGER = Logger.getLogger(LogWriter.class.getName());
    private final BufferedWriter writer;
    private static int session = 0;
    public static void bumpSession() { session++; }
    private final BlockingQueue<String> logQueue = new LinkedBlockingQueue<>();
    private boolean loggingActive = false;

    public LogWriter(File file) throws IOException {
        writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8));
        ExecutorService exec = Executors.newSingleThreadExecutor();

        LOGGER.info("starting logging to " + file);
        exec.submit(() -> {
            try {
                loggingActive = true;
                long last = System.currentTimeMillis();

                writer.write(String.join(",", LogEntry.HEADER));
                writer.write("," + OpenDroneIdParser.BasicId.csvHeader());
                writer.write(OpenDroneIdParser.BasicId.csvHeader());
                writer.write(OpenDroneIdParser.Location.csvHeader());
                writer.write(OpenDroneIdParser.SelfID.csvHeader());
                writer.write(OpenDroneIdParser.SystemMsg.csvHeader());
                writer.write(OpenDroneIdParser.OperatorID.csvHeader());
                for (int i = 0; i < Constants.MAX_AUTH_DATA_PAGES; i++)
                    writer.write(OpenDroneIdParser.Authentication.csvHeader());
                writer.newLine();

                while (loggingActive) {
                    String log;
                    try {
                        log = logQueue.take();
                    } catch (InterruptedException e) {
                        break;
                    }
                    writer.write(log);
                    writer.newLine();
                    long time = System.currentTimeMillis();
                    if (time - last > 1000) {
                        writer.flush();
                        last = time;
                    }
                }
            } catch (IOException e) {
                LOGGER.severe("error writing log: " + e.getMessage());
            } finally {
                try { writer.flush(); writer.close(); } catch (IOException e) { e.printStackTrace(); }
            }
        });
    }

    public void logBluetooth(int msgVersion, long timestampNanos, String macAddress,
                             int rssi, byte[] rawBytes, String transportType, StringBuilder csvLog) {
        LogEntry entry = new LogEntry();
        entry.session = session;
        entry.timestamp = timestampNanos;
        entry.transportType = transportType;
        entry.macAddress = macAddress;
        entry.msgVersion = msgVersion;
        entry.rssi = rssi;
        if (rawBytes != null) entry.data = rawBytes;
        entry.csvLog = csvLog;
        logQueue.add(entry.toString());
    }

    public void logBeacon(int msgVersion, long timeNano, String bssid,
                          int level, byte[] data, String transportType, StringBuilder csvLog) {
        LogEntry entry = new LogEntry();
        entry.session = session;
        entry.timestamp = timeNano;
        entry.transportType = transportType;
        entry.macAddress = bssid;
        entry.msgVersion = msgVersion;
        entry.rssi = level;
        if (data != null) entry.data = data;
        entry.csvLog = csvLog;
        logQueue.add(entry.toString());
    }

    public void close() {
        loggingActive = false;
        try { writer.flush(); writer.close(); } catch (IOException e) { e.printStackTrace(); }
    }
}
