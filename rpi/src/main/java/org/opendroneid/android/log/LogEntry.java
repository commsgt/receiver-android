package org.opendroneid.android.log;

public class LogEntry {
    int session;
    long timestamp;
    String transportType;
    String macAddress;
    int msgVersion;
    int rssi;
    byte[] data;
    StringBuilder csvLog;

    final static String[] HEADER = new String[]{
            "session", "timestamp (nanos)", "transportType",
            "macAddress", "msgVersion", "rssi", "payload"
    };

    static final String DELIM = ",";

    @Override
    public String toString() {
        return session + DELIM
                + timestamp + DELIM
                + transportType + DELIM
                + macAddress + DELIM
                + msgVersion + DELIM
                + rssi + DELIM
                + toHexString(data, data.length) + DELIM
                + csvLog;
    }

    public static String toHexString(byte[] bytes, int len) {
        StringBuilder sb = new StringBuilder(len * 3);
        int i = 0;
        for (byte b : bytes) {
            if (++i > len) break;
            sb.append(String.format("%02X ", b & 0xFF));
        }
        return sb.toString();
    }
}
