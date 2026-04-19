package org.opendroneid.android.data;

import java.sql.Timestamp;
import java.util.Locale;

public class SystemData extends MessageData {

    private operatorLocationTypeEnum operatorLocationType;
    private classificationTypeEnum classificationType;
    private double operatorLatitude;
    private double operatorLongitude;
    private int areaCount;
    private int areaRadius;
    private double areaCeiling;
    private double areaFloor;
    private categoryEnum category;
    private classValueEnum classValue;
    private double operatorAltitudeGeo;
    private long systemTimestamp;

    public SystemData() {
        super();
        operatorLocationType = operatorLocationTypeEnum.Invalid;
        classificationType = classificationTypeEnum.Undeclared;
        operatorLatitude = 0;
        operatorLongitude = 0;
        areaCount = 0;
        areaRadius = 0;
        areaCeiling = -1000;
        areaFloor = -1000;
        category = categoryEnum.Undeclared;
        classValue = classValueEnum.Undeclared;
        operatorAltitudeGeo = -1000;
        systemTimestamp = 0;
    }

    public enum operatorLocationTypeEnum { TakeOff, Dynamic, Fixed, Invalid }
    public operatorLocationTypeEnum getOperatorLocationType() { return operatorLocationType; }
    public void setOperatorLocationType(int v) {
        switch (v) {
            case 0: operatorLocationType = operatorLocationTypeEnum.TakeOff; break;
            case 1: operatorLocationType = operatorLocationTypeEnum.Dynamic; break;
            case 2: operatorLocationType = operatorLocationTypeEnum.Fixed; break;
            default: operatorLocationType = operatorLocationTypeEnum.Invalid; break;
        }
    }

    public enum classificationTypeEnum { Undeclared, EU }
    public classificationTypeEnum getClassificationType() { return classificationType; }
    public void setClassificationType(int v) {
        switch (v) {
            case 1: classificationType = classificationTypeEnum.EU; break;
            default: classificationType = classificationTypeEnum.Undeclared; break;
        }
    }

    public double getOperatorLatitude() { return operatorLatitude; }
    public void setOperatorLatitude(double v) { operatorLatitude = v; }
    public double getOperatorLongitude() { return operatorLongitude; }
    public void setOperatorLongitude(double v) { operatorLongitude = v; }

    public int getAreaCount() { return areaCount; }
    public void setAreaCount(int areaCount) { this.areaCount = areaCount; }
    public int getAreaRadius() { return areaRadius; }
    public void setAreaRadius(int areaRadius) { this.areaRadius = areaRadius; }

    public double getAreaCeiling() { return areaCeiling; }
    public void setAreaCeiling(double v) { areaCeiling = v; }
    public double getAreaFloor() { return areaFloor; }
    public void setAreaFloor(double v) { areaFloor = v; }

    public enum categoryEnum { Undeclared, EU_Open, EU_Specific, EU_Certified }
    public categoryEnum getCategory() { return category; }
    public void setCategory(int v) {
        switch (v) {
            case 1: category = categoryEnum.EU_Open; break;
            case 2: category = categoryEnum.EU_Specific; break;
            case 3: category = categoryEnum.EU_Certified; break;
            default: category = categoryEnum.Undeclared; break;
        }
    }

    public enum classValueEnum {
        Undeclared, EU_Class_0, EU_Class_1, EU_Class_2, EU_Class_3, EU_Class_4, EU_Class_5, EU_Class_6
    }
    public classValueEnum getClassValue() { return classValue; }
    public void setClassValue(int v) {
        switch (v) {
            case 1: classValue = classValueEnum.EU_Class_0; break;
            case 2: classValue = classValueEnum.EU_Class_1; break;
            case 3: classValue = classValueEnum.EU_Class_2; break;
            case 4: classValue = classValueEnum.EU_Class_3; break;
            case 5: classValue = classValueEnum.EU_Class_4; break;
            case 6: classValue = classValueEnum.EU_Class_5; break;
            case 7: classValue = classValueEnum.EU_Class_6; break;
            default: classValue = classValueEnum.Undeclared; break;
        }
    }

    public double getOperatorAltitudeGeo() { return operatorAltitudeGeo; }
    public void setOperatorAltitudeGeo(double v) { operatorAltitudeGeo = v; }

    public long getSystemTimestamp() { return systemTimestamp; }
    public void setSystemTimestamp(long v) { systemTimestamp = v; }
    public String getSystemTimestampAsString() {
        if (systemTimestamp == 0) return "Unknown";
        return new Timestamp(systemTimestamp * 1000L).toString();
    }
}
