package org.opendroneid.rpi.scanner;

import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder;
import org.freedesktop.dbus.interfaces.Properties;
import org.freedesktop.dbus.types.Variant;
import org.opendroneid.android.bluetooth.OpenDroneIdDataManager;
import org.opendroneid.android.log.LogMessageEntry;
import org.opendroneid.android.log.LogWriter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * BLE scanner for Linux using BlueZ D-Bus API.
 *
 * Requires BlueZ 5.x and a BLE-capable adapter (e.g., CSR dongle).
 * The adapter is set to scan for advertisements with service UUID 0xFFFA
 * (ASTM Remote ID). BlueZ delivers ServiceData as a byte array containing:
 *   [0x0D][msgCounter][25-byte ASTM F3411-22 message]
 * which is passed to the data manager at offset 2.
 *
 * Run with: sudo java -jar receiver-rpi.jar --ble-adapter hci0
 */
public class BleScanner {
    private static final Logger LOGGER = Logger.getLogger(BleScanner.class.getName());

    private static final String BLUEZ_BUS_NAME = "org.bluez";
    private static final String REMOTE_ID_UUID = "0000fffa-0000-1000-8000-00805f9b34fb";
    private static final String ADAPTER_PATH = "/org/bluez/hci0";

    private final OpenDroneIdDataManager dataManager;
    private final LogWriter logger;
    private DBusConnection dbusConnection;
    private volatile boolean running = false;
    private Thread scanThread;

    public BleScanner(OpenDroneIdDataManager dataManager, LogWriter logger) {
        this.dataManager = dataManager;
        this.logger = logger;
    }

    public void start() {
        running = true;
        scanThread = new Thread(this::runScan, "BleScanner");
        scanThread.setDaemon(true);
        scanThread.start();
        LOGGER.info("BLE scanner started on " + ADAPTER_PATH);
    }

    public void stop() {
        running = false;
        if (dbusConnection != null) {
            try {
                callBluezMethod(ADAPTER_PATH, "org.bluez.Adapter1", "StopDiscovery");
                dbusConnection.disconnect();
            } catch (Exception e) {
                LOGGER.warning("Error stopping BLE scan: " + e.getMessage());
            }
        }
        if (scanThread != null) scanThread.interrupt();
        LOGGER.info("BLE scanner stopped");
    }

    private void runScan() {
        try {
            dbusConnection = DBusConnectionBuilder.forSystemBus().build();

            // Set discovery filter for Remote ID UUID
            Map<String, Variant<?>> filter = Map.of(
                    "UUIDs", new Variant<>(new String[]{REMOTE_ID_UUID}, "as"),
                    "Transport", new Variant<>("le")
            );
            callBluezMethodWithArgs(ADAPTER_PATH, "org.bluez.Adapter1", "SetDiscoveryFilter", filter);
            callBluezMethod(ADAPTER_PATH, "org.bluez.Adapter1", "StartDiscovery");

            LOGGER.info("BLE discovery started. Listening for Remote ID advertisements...");

            // Poll for device properties changes
            while (running) {
                pollDevices();
                Thread.sleep(100);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            LOGGER.severe("BLE scanner error: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void pollDevices() {
        try {
            // Get all managed objects from BlueZ object manager
            var objectManager = dbusConnection.getRemoteObject(
                    BLUEZ_BUS_NAME, "/", org.freedesktop.dbus.interfaces.DBusInterface.class);

            // Use GetManagedObjects to enumerate devices
            // For each Device1 object with ServiceData containing our UUID, extract bytes
            // This is a simplified polling approach; production use should use signal handlers
            var managedObjects = dbusConnection.callWithReply(
                    BLUEZ_BUS_NAME, "/",
                    "org.freedesktop.DBus.ObjectManager", "GetManagedObjects",
                    false);
            if (managedObjects == null) return;

            // Process response - extract Device1 objects with our ServiceData
            Object replyObj = managedObjects.getReply();
            if (!(replyObj instanceof Object[])) return;
            Object[] reply = (Object[]) replyObj;
            if (reply.length == 0) return;

            // reply[0] is Map<Path, Map<Interface, Map<Property, Variant>>>
            if (!(reply[0] instanceof Map)) return;
            Map<?, ?> objects = (Map<?, ?>) reply[0];

            for (Map.Entry<?, ?> entry : objects.entrySet()) {
                String path = entry.getKey().toString();
                if (!path.contains("/dev_")) continue; // only device objects

                if (!(entry.getValue() instanceof Map)) continue;
                Map<?, ?> interfaces = (Map<?, ?>) entry.getValue();

                Object device1Props = interfaces.get("org.bluez.Device1");
                if (!(device1Props instanceof Map)) continue;
                Map<String, Variant<?>> props = (Map<String, Variant<?>>) device1Props;

                Variant<?> serviceDataVariant = props.get("ServiceData");
                if (serviceDataVariant == null) continue;

                Object serviceDataObj = serviceDataVariant.getValue();
                if (!(serviceDataObj instanceof Map)) continue;
                Map<?, ?> serviceDataMap = (Map<?, ?>) serviceDataObj;

                Object uuidBytes = serviceDataMap.get(REMOTE_ID_UUID);
                if (uuidBytes == null) continue;

                byte[] bytes = extractBytes(uuidBytes);
                if (bytes == null || bytes.length < 27) continue; // 1 + 1 + 25

                Variant<?> addressVariant = props.get("Address");
                Variant<?> rssiVariant = props.get("RSSI");
                if (addressVariant == null) continue;

                String macAddress = addressVariant.getValue().toString();
                int rssi = (rssiVariant != null) ? ((Number) rssiVariant.getValue()).intValue() : 0;
                long timestampNanos = System.nanoTime();

                LogMessageEntry logEntry = new LogMessageEntry();
                dataManager.receiveDataBluetooth(bytes, macAddress, rssi, timestampNanos, logEntry, "BT5");

                if (logger != null) {
                    StringBuilder csvLog = logEntry.getMessageLogEntry();
                    logger.logBluetooth(logEntry.getMsgVersion(), timestampNanos, macAddress, rssi, bytes, "BT5", csvLog);
                }
            }
        } catch (Exception e) {
            if (running) LOGGER.fine("Poll error (normal if no devices): " + e.getMessage());
        }
    }

    private byte[] extractBytes(Object obj) {
        if (obj instanceof byte[]) return (byte[]) obj;
        if (obj instanceof Byte[]) {
            Byte[] boxed = (Byte[]) obj;
            byte[] result = new byte[boxed.length];
            for (int i = 0; i < boxed.length; i++) result[i] = boxed[i];
            return result;
        }
        if (obj instanceof List) {
            List<?> list = (List<?>) obj;
            byte[] result = new byte[list.size()];
            for (int i = 0; i < list.size(); i++) result[i] = ((Number) list.get(i)).byteValue();
            return result;
        }
        return null;
    }

    private void callBluezMethod(String path, String iface, String method) throws Exception {
        dbusConnection.callNoReply(BLUEZ_BUS_NAME, path, iface, method);
    }

    private void callBluezMethodWithArgs(String path, String iface, String method, Object... args) throws Exception {
        dbusConnection.callNoReply(BLUEZ_BUS_NAME, path, iface, method, args);
    }
}
