package org.opendroneid.rpi;

import org.opendroneid.android.bluetooth.OpenDroneIdDataManager;
import org.opendroneid.android.data.AircraftObject;
import org.opendroneid.android.data.LocationData;
import org.opendroneid.android.log.LogWriter;
import org.opendroneid.rpi.scanner.BleScanner;
import org.opendroneid.rpi.scanner.GpsReader;
import org.opendroneid.rpi.scanner.WiFiScanner;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.logging.Logger;

/**
 * OpenDroneID receiver for Raspberry Pi 4.
 *
 * Usage:
 *   sudo java -jar receiver-rpi.jar [--wifi wlan1mon] [--no-ble] [--no-wifi] [--log]
 *
 * Default WiFi interface: wlan0mon (put your Alfa adapter in monitor mode first)
 * BLE uses hci0 (your BLE dongle)
 *
 * Prerequisites: see setup.sh
 */
public class Main {
    private static final Logger LOGGER = Logger.getLogger(Main.class.getName());

    public static void main(String[] args) throws Exception {
        String wifiIface = "wlan0mon";
        boolean enableBle = true;
        boolean enableWifi = true;
        boolean enableLog = false;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--wifi":
                    if (i + 1 < args.length) wifiIface = args[++i];
                    break;
                case "--no-ble":   enableBle = false; break;
                case "--no-wifi":  enableWifi = false; break;
                case "--log":      enableLog = true; break;
            }
        }

        LogWriter logWriter = null;
        if (enableLog) {
            String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            File logFile = new File("opendroneID_" + ts + ".csv");
            try {
                logWriter = new LogWriter(logFile);
                LOGGER.info("Logging to " + logFile.getAbsolutePath());
            } catch (IOException e) {
                LOGGER.warning("Could not open log file: " + e.getMessage());
            }
        }

        final LogWriter finalLogger = logWriter;

        OpenDroneIdDataManager dataManager = new OpenDroneIdDataManager(new OpenDroneIdDataManager.Callback() {
            @Override
            public void onNewAircraft(AircraftObject ac) {
                System.out.println("[NEW] Aircraft detected via " +
                        (ac.getConnection() != null ? ac.getConnection().transportType : "?") +
                        "  MAC=" + (ac.getConnection() != null ? ac.getConnection().macAddress : "?"));
                // Register observer so every location update prints a line
                ac.location.observe(loc -> printAircraft(ac, loc));
            }
        });

        GpsReader gpsReader = new GpsReader(dataManager);
        gpsReader.start();

        BleScanner bleScanner = null;
        if (enableBle) {
            bleScanner = new BleScanner(dataManager, finalLogger);
            bleScanner.start();
        }

        WiFiScanner wifiScanner = null;
        if (enableWifi) {
            wifiScanner = new WiFiScanner(dataManager, finalLogger, wifiIface);
            wifiScanner.start();
        }

        System.out.println("OpenDroneID receiver running. Press Ctrl+C to stop.");
        System.out.println("  BLE:  " + (enableBle ? "hci0" : "disabled"));
        System.out.println("  WiFi: " + (enableWifi ? wifiIface : "disabled"));
        System.out.println("  GPS:  gpsd @ localhost:2947");

        final BleScanner finalBle = bleScanner;
        final WiFiScanner finalWifi = wifiScanner;
        final LogWriter finalLog = logWriter;

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\nShutting down...");
            if (finalBle != null) finalBle.stop();
            if (finalWifi != null) finalWifi.stop();
            gpsReader.stop();
            if (finalLog != null) finalLog.close();
        }));

        // Keep main thread alive
        Thread.currentThread().join();
    }

    private static void printAircraft(AircraftObject ac, LocationData loc) {
        if (loc == null) return;
        System.out.printf("[UPDATE] MAC=%-17s  lat=%10.6f  lon=%11.6f  alt=%7.1fm  spd=%.1fm/s  dist=%.0fm%n",
                ac.getConnection() != null ? ac.getConnection().macAddress : "?",
                loc.getLatitude(), loc.getLongitude(),
                loc.getAltitudeGeodetic(), loc.getSpeedHorizontal(), loc.getDistance());
    }
}
