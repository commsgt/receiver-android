package org.opendroneid.android.data;

import java.util.Locale;

public class LocationData extends MessageData {

    private StatusEnum status;
    private heightTypeEnum heightType;
    private double direction;
    private double speedHorizontal;
    private double speedVertical;
    private double latitude;
    private double longitude;
    private double altitudePressure;
    private double altitudeGeodetic;
    private double height;
    private HorizontalAccuracyEnum horizontalAccuracy;
    private VerticalAccuracyEnum verticalAccuracy;
    private VerticalAccuracyEnum baroAccuracy;
    private SpeedAccuracyEnum speedAccuracy;
    private double locationTimestamp;
    private double timeAccuracy;
    private float distance;

    public LocationData() {
        super();
        status = StatusEnum.Undeclared;
        heightType = heightTypeEnum.Takeoff;
        direction = 361;
        speedHorizontal = 255;
        speedVertical = 63;
        latitude = 0;
        longitude = 0;
        altitudePressure = -1000;
        altitudeGeodetic = -1000;
        height = -1000;
        horizontalAccuracy = HorizontalAccuracyEnum.Unknown;
        verticalAccuracy = VerticalAccuracyEnum.Unknown;
        baroAccuracy = VerticalAccuracyEnum.Unknown;
        speedAccuracy = SpeedAccuracyEnum.Unknown;
        locationTimestamp = 0xFFFF;
        timeAccuracy = 0;
    }

    public enum StatusEnum {
        Undeclared, Ground, Airborne, Emergency,
        Remote_ID_System_Failure {
            public String toString() { return "Rem_ID_Sys_Fail"; }
        },
    }
    public StatusEnum getStatus() { return status; }
    public void setStatus(int status) {
        switch (status) {
            case 1: this.status = StatusEnum.Ground; break;
            case 2: this.status = StatusEnum.Airborne; break;
            case 3: this.status = StatusEnum.Emergency; break;
            case 4: this.status = StatusEnum.Remote_ID_System_Failure; break;
            default: this.status = StatusEnum.Undeclared; break;
        }
    }

    public enum heightTypeEnum { Takeoff, Ground }
    public heightTypeEnum getHeightType() { return heightType; }
    public void setHeightType(int heightType) {
        this.heightType = (heightType == 1) ? heightTypeEnum.Ground : heightTypeEnum.Takeoff;
    }

    public double getDirection() { return direction; }
    public String getDirectionAsString() {
        return (direction != 361) ? String.format(Locale.US, "%3.0f deg", direction) : "Unknown";
    }
    public void setDirection(double direction) {
        if (direction < 0 || direction > 360) direction = 361;
        this.direction = direction;
    }

    public double getSpeedHorizontal() { return speedHorizontal; }
    public String getSpeedHorizontalAsString() {
        return (speedHorizontal != 255) ? String.format(Locale.US, "%3.2f m/s", speedHorizontal) : "Unknown";
    }
    public void setSpeedHorizontal(double speedHorizontal) {
        if (speedHorizontal < 0 || speedHorizontal > 254.25) speedHorizontal = 255;
        this.speedHorizontal = speedHorizontal;
    }

    public double getSpeedVertical() { return speedVertical; }
    public String getSpeedVerticalAsString() {
        return (speedVertical != 63) ? String.format(Locale.US, "%3.2f m/s", speedVertical) : "Unknown";
    }
    public void setSpeedVertical(double speedVertical) {
        if (speedVertical < -62 || speedVertical > 62) speedVertical = 63;
        this.speedVertical = speedVertical;
    }

    public double getLatitude() { return latitude; }
    public String getLatitudeAsString() {
        return (latitude == 0 && longitude == 0) ? "Unknown" : String.format(Locale.US, "%3.7f", latitude);
    }
    public void setLatitude(double latitude) {
        if (latitude < -90 || latitude > 90) { latitude = 0; this.longitude = 0; }
        this.latitude = latitude;
    }

    public double getLongitude() { return longitude; }
    public String getLongitudeAsString() {
        return (latitude == 0 && longitude == 0) ? "Unknown" : String.format(Locale.US, "%3.7f", longitude);
    }
    public void setLongitude(double longitude) {
        if (longitude < -180 || longitude > 180) { this.latitude = 0; longitude = 0; }
        this.longitude = longitude;
    }

    private String getAltitudeAsString(double altitude) {
        return (altitude == -1000) ? "Unknown" : String.format(Locale.US, "%3.1f m", altitude);
    }
    public double getAltitudePressure() { return altitudePressure; }
    public String getAltitudePressureAsString() { return getAltitudeAsString(altitudePressure); }
    public void setAltitudePressure(double v) {
        if (v < -1000 || v > 31767) v = -1000;
        this.altitudePressure = v;
    }
    public double getAltitudeGeodetic() { return altitudeGeodetic; }
    public String getAltitudeGeodeticAsString() { return getAltitudeAsString(altitudeGeodetic); }
    public void setAltitudeGeodetic(double v) {
        if (v < -1000 || v > 31767) v = -1000;
        this.altitudeGeodetic = v;
    }
    public double getHeight() { return height; }
    public String getHeightAsString() { return getAltitudeAsString(height); }
    public void setHeight(double v) {
        if (v < -1000 || v > 31767) v = -1000;
        this.height = v;
    }

    public enum HorizontalAccuracyEnum {
        Unknown, kilometers_18_52, kilometers_7_408, kilometers_3_704, kilometers_1_852,
        meters_926, meters_555_6, meters_185_2, meters_92_6, meters_30, meters_10, meters_3, meters_1,
    }
    public HorizontalAccuracyEnum getHorizontalAccuracy() { return horizontalAccuracy; }
    public void setHorizontalAccuracy(int v) {
        switch (v) {
            case 1: horizontalAccuracy = HorizontalAccuracyEnum.kilometers_18_52; break;
            case 2: horizontalAccuracy = HorizontalAccuracyEnum.kilometers_7_408; break;
            case 3: horizontalAccuracy = HorizontalAccuracyEnum.kilometers_3_704; break;
            case 4: horizontalAccuracy = HorizontalAccuracyEnum.kilometers_1_852; break;
            case 5: horizontalAccuracy = HorizontalAccuracyEnum.meters_926; break;
            case 6: horizontalAccuracy = HorizontalAccuracyEnum.meters_555_6; break;
            case 7: horizontalAccuracy = HorizontalAccuracyEnum.meters_185_2; break;
            case 8: horizontalAccuracy = HorizontalAccuracyEnum.meters_92_6; break;
            case 9: horizontalAccuracy = HorizontalAccuracyEnum.meters_30; break;
            case 10: horizontalAccuracy = HorizontalAccuracyEnum.meters_10; break;
            case 11: horizontalAccuracy = HorizontalAccuracyEnum.meters_3; break;
            case 12: horizontalAccuracy = HorizontalAccuracyEnum.meters_1; break;
            default: horizontalAccuracy = HorizontalAccuracyEnum.Unknown; break;
        }
    }

    public enum VerticalAccuracyEnum { Unknown, meters_150, meters_45, meters_25, meters_10, meters_3, meters_1 }
    public VerticalAccuracyEnum getVerticalAccuracy() { return verticalAccuracy; }
    public VerticalAccuracyEnum getBaroAccuracy() { return baroAccuracy; }
    private VerticalAccuracyEnum intToVerticalAccuracy(int v) {
        switch (v) {
            case 1: return VerticalAccuracyEnum.meters_150;
            case 2: return VerticalAccuracyEnum.meters_45;
            case 3: return VerticalAccuracyEnum.meters_25;
            case 4: return VerticalAccuracyEnum.meters_10;
            case 5: return VerticalAccuracyEnum.meters_3;
            case 6: return VerticalAccuracyEnum.meters_1;
            default: return VerticalAccuracyEnum.Unknown;
        }
    }
    public void setVerticalAccuracy(int v) { this.verticalAccuracy = intToVerticalAccuracy(v); }
    public void setBaroAccuracy(int v) { this.baroAccuracy = intToVerticalAccuracy(v); }

    public enum SpeedAccuracyEnum {
        Unknown, meter_per_second_10, meter_per_second_3, meter_per_second_1, meter_per_second_0_3,
    }
    public SpeedAccuracyEnum getSpeedAccuracy() { return speedAccuracy; }
    public void setSpeedAccuracy(int v) {
        switch (v) {
            case 1: speedAccuracy = SpeedAccuracyEnum.meter_per_second_10; break;
            case 2: speedAccuracy = SpeedAccuracyEnum.meter_per_second_3; break;
            case 3: speedAccuracy = SpeedAccuracyEnum.meter_per_second_1; break;
            case 4: speedAccuracy = SpeedAccuracyEnum.meter_per_second_0_3; break;
            default: speedAccuracy = SpeedAccuracyEnum.Unknown; break;
        }
    }

    public double getLocationTimestamp() { return locationTimestamp; }
    public String getLocationTimestampAsString() {
        if (locationTimestamp == 0xFFFF) return "--:--";
        double totalSeconds = locationTimestamp / 10.0;
        int minutes = (int) (totalSeconds / 60);
        int seconds = (int) Math.round(totalSeconds % 60);
        if (seconds == 60) { seconds = 0; minutes++; }
        return String.format(Locale.US, "%02d:%02d", minutes, seconds);
    }
    public void setLocationTimestamp(double v) {
        if (v < 0) v = 0;
        if (v != 0xFFFF && v > 36000) v = 36000;
        this.locationTimestamp = v;
    }

    public double getTimeAccuracy() { return timeAccuracy; }
    public String getTimeAccuracyAsString() {
        return (timeAccuracy == 0) ? "Unknown" : String.format(Locale.US, "<= %1.1f s", timeAccuracy);
    }
    public void setTimeAccuracy(double v) {
        if (v < 0) v = 0;
        if (v > 1.5) v = 1.5;
        this.timeAccuracy = v;
    }

    public float getDistance() { return distance; }
    public String getDistanceAsString() { return String.format(Locale.US, "~%.0f m", distance); }
    public void setDistance(float distance) { this.distance = distance; }
}
