package org.opendroneid.rpi.scanner;

import org.opendroneid.android.bluetooth.OpenDroneIdDataManager;
import org.opendroneid.rpi.GpsLocation;

import java.io.*;
import java.net.Socket;
import java.util.logging.Logger;

/**
 * GPS reader that connects to gpsd daemon (localhost:2947) or falls back
 * to reading NMEA from a serial device (e.g. /dev/ttyAMA0).
 *
 * gpsd must be running: sudo gpsd /dev/ttyAMA0 -F /var/run/gpsd.sock
 *
 * Updates dataManager.receiverLocation whenever a valid fix is received.
 */
public class GpsReader {
    private static final Logger LOGGER = Logger.getLogger(GpsReader.class.getName());

    private static final String GPSD_HOST = "localhost";
    private static final int GPSD_PORT = 2947;

    private final OpenDroneIdDataManager dataManager;
    private volatile boolean running = false;
    private Thread gpsThread;

    public GpsReader(OpenDroneIdDataManager dataManager) {
        this.dataManager = dataManager;
    }

    public void start() {
        running = true;
        gpsThread = new Thread(this::runGps, "GpsReader");
        gpsThread.setDaemon(true);
        gpsThread.start();
        LOGGER.info("GPS reader started");
    }

    public void stop() {
        running = false;
        if (gpsThread != null) gpsThread.interrupt();
        LOGGER.info("GPS reader stopped");
    }

    private void runGps() {
        while (running) {
            try {
                tryGpsd();
            } catch (Exception e) {
                LOGGER.warning("gpsd connection failed: " + e.getMessage() + " — retrying in 5s");
                try { Thread.sleep(5000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); return; }
            }
        }
    }

    private void tryGpsd() throws Exception {
        try (Socket socket = new Socket(GPSD_HOST, GPSD_PORT);
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter writer = new PrintWriter(socket.getOutputStream(), true)) {

            // Enable JSON output with TPV (Time-Position-Velocity) reports
            writer.println("?WATCH={\"enable\":true,\"json\":true}");
            LOGGER.info("Connected to gpsd at " + GPSD_HOST + ":" + GPSD_PORT);

            String line;
            while (running && (line = reader.readLine()) != null) {
                parseTpv(line);
            }
        }
    }

    private void parseTpv(String json) {
        if (!json.contains("\"class\":\"TPV\"")) return;

        Double lat = extractJsonDouble(json, "lat");
        Double lon = extractJsonDouble(json, "lon");
        Double alt = extractJsonDouble(json, "alt");

        if (lat == null || lon == null) return;
        if (lat == 0.0 && lon == 0.0) return;

        GpsLocation loc = new GpsLocation();
        loc.setLatitude(lat);
        loc.setLongitude(lon);
        if (alt != null) loc.setAltitude(alt);
        dataManager.receiverLocation = loc;

        LOGGER.fine(String.format("GPS fix: lat=%.6f lon=%.6f alt=%.1f", lat, lon, alt != null ? alt : 0.0));
    }

    private Double extractJsonDouble(String json, String key) {
        String search = "\"" + key + "\":";
        int idx = json.indexOf(search);
        if (idx < 0) return null;
        int start = idx + search.length();
        int end = start;
        while (end < json.length() && (Character.isDigit(json.charAt(end))
                || json.charAt(end) == '.' || json.charAt(end) == '-')) {
            end++;
        }
        if (end == start) return null;
        try {
            return Double.parseDouble(json.substring(start, end));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
