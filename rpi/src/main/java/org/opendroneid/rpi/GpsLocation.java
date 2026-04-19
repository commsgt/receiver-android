package org.opendroneid.rpi;

public class GpsLocation {
    private double latitude;
    private double longitude;
    private double altitude;

    public GpsLocation() {}

    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }

    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }

    public double getAltitude() { return altitude; }
    public void setAltitude(double altitude) { this.altitude = altitude; }

    public float distanceTo(GpsLocation dest) {
        double R = 6371000.0;
        double lat1 = Math.toRadians(this.latitude);
        double lat2 = Math.toRadians(dest.latitude);
        double dLat = Math.toRadians(dest.latitude - this.latitude);
        double dLon = Math.toRadians(dest.longitude - this.longitude);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return (float) (R * c);
    }
}
