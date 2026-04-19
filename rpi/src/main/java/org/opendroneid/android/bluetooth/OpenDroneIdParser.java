/*
 * Copyright (C) 2019 Intel Corporation
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opendroneid.android.bluetooth;

import org.opendroneid.android.Constants;
import org.opendroneid.android.log.LogMessageEntry;
import org.opendroneid.rpi.GpsLocation;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.logging.Logger;

public class OpenDroneIdParser {
    private static final Logger LOGGER = Logger.getLogger(OpenDroneIdParser.class.getName());
    private static final String TAG = "OpenDroneIdParser";
    private static final String DELIM = Constants.DELIM;

    public enum Type {
        BASIC_ID(0), LOCATION(1), AUTH(2), SELFID(3), SYSTEM(4), OPERATOR_ID(5), MESSAGE_PACK(0xF);
        Type(int id) { this.id = id; }
        public final int id;
        public static Type fromId(int id) {
            for (Type t : values()) if (t.id == id) return t;
            return null;
        }
    }

    public static class Header {
        public Type type;
        public int version;
        @Override
        public String toString() { return "Header{type=" + type + ", version=" + version + '}'; }
    }

    public interface Payload { String toCsvString(); }

    private static final double LAT_LONG_MULTIPLIER = 1e-7;
    private static final double SPEED_VERTICAL_MULTIPLIER = 0.5;

    public static class BasicId implements Payload {
        int idType;
        int uaType;
        final byte[] uasId = new byte[Constants.MAX_ID_BYTE_SIZE];

        public static String csvHeader() {
            return "idType" + DELIM + "uaType" + DELIM + "uasId" + DELIM;
        }
        @Override public String toCsvString() {
            return idType + DELIM + uaType + DELIM + new String(uasId) + DELIM;
        }
        @Override public String toString() {
            return "BasicId{idType=" + idType + ", uaType=" + uaType + ", uasId='" + Arrays.toString(uasId) + "'}";
        }
    }

    public static class Location implements Payload {
        int status, heightType, EWDirection, speedMult, Direction, speedHori, speedVert;
        int droneLat, droneLon, altitudePressure, altitudeGeodetic, height;
        int horizontalAccuracy, verticalAccuracy, baroAccuracy, speedAccuracy, timestamp, timeAccuracy;
        float distance;

        static double calcSpeed(int value, int mult) {
            return (mult == 0) ? value * 0.25 : (value * 0.75) + (255 * 0.25);
        }
        static double calcDirection(int value, int EW) {
            return (EW == 0) ? value : value + 180;
        }
        double getDirection() { return calcDirection(Direction, EWDirection); }
        double getSpeedHori() { return calcSpeed(speedHori, speedMult); }
        double getSpeedVert() { return SPEED_VERTICAL_MULTIPLIER * speedVert; }
        double getLatitude() { return LAT_LONG_MULTIPLIER * droneLat; }
        double getLongitude() { return LAT_LONG_MULTIPLIER * droneLon; }
        static double calcAltitude(int value) { return (double) value / 2 - 1000; }
        double getAltitudePressure() { return calcAltitude(altitudePressure); }
        double getAltitudeGeodetic() { return calcAltitude(altitudeGeodetic); }
        double getHeight() { return calcAltitude(height); }
        double getTimeAccuracy() { return timeAccuracy * 0.1; }

        public static String csvHeader() {
            return "status" + DELIM + "heightType" + DELIM + "EWDirection" + DELIM + "speedMult" + DELIM
                    + "direction" + DELIM + "speedHori" + DELIM + "speedVert" + DELIM
                    + "droneLat" + DELIM + "droneLon" + DELIM + "altitudePressure" + DELIM
                    + "altitudeGeodetic" + DELIM + "height" + DELIM + "horizontalAccuracy" + DELIM
                    + "verticalAccuracy" + DELIM + "baroAccuracy" + DELIM + "speedAccuracy" + DELIM
                    + "timestamp" + DELIM + "timeAccuracy" + DELIM + "distance" + DELIM;
        }
        @Override public String toCsvString() {
            return status + DELIM + heightType + DELIM + EWDirection + DELIM + speedMult + DELIM
                    + Direction + DELIM + speedHori + DELIM + speedVert + DELIM
                    + droneLat + DELIM + droneLon + DELIM + altitudePressure + DELIM
                    + altitudeGeodetic + DELIM + height + DELIM + horizontalAccuracy + DELIM
                    + verticalAccuracy + DELIM + baroAccuracy + DELIM + speedAccuracy + DELIM
                    + timestamp + DELIM + timeAccuracy + DELIM + distance + DELIM;
        }
        @Override public String toString() {
            return "Location{status=" + status + ", droneLat=" + droneLat + ", droneLon=" + droneLon + '}';
        }
    }

    public static class Authentication implements Payload {
        int authType, authDataPage, authLastPageIndex, authLength;
        long authTimestamp;
        final byte[] authData = new byte[Constants.MAX_AUTH_DATA];

        public int getAuthDataPage() { return authDataPage; }

        public static String csvHeader() {
            return "authType" + DELIM + "authDataPage" + DELIM + "authLastPageIndex" + DELIM
                    + "authLength" + DELIM + "authTimestamp" + DELIM + "authData" + DELIM;
        }
        private String authDataToString() {
            StringBuilder sb = new StringBuilder();
            for (byte b : authData) sb.append(String.format("%02X ", b));
            return sb.toString();
        }
        @Override public String toCsvString() {
            return authType + DELIM + authDataPage + DELIM + authLastPageIndex + DELIM
                    + authLength + DELIM + authTimestamp + DELIM + authDataToString() + DELIM;
        }
        @Override public String toString() {
            return "Authentication{authType=" + authType + ", authDataPage=" + authDataPage + '}';
        }
    }

    public static class SelfID implements Payload {
        int descriptionType;
        final byte[] operationDescription = new byte[Constants.MAX_STRING_BYTE_SIZE];

        public static String csvHeader() {
            return "descriptionType" + DELIM + "operationDescription" + DELIM;
        }
        @Override public String toCsvString() {
            return descriptionType + DELIM + new String(operationDescription) + DELIM;
        }
        @Override public String toString() {
            return "SelfID{descriptionType=" + descriptionType + '}';
        }
    }

    public static class SystemMsg implements Payload {
        int operatorLocationType, classificationType, operatorLatitude, operatorLongitude;
        int areaCount, areaRadius, areaCeiling, areaFloor, category, classValue, operatorAltitudeGeo;
        long systemTimestamp;

        double getLatitude() { return LAT_LONG_MULTIPLIER * operatorLatitude; }
        double getLongitude() { return LAT_LONG_MULTIPLIER * operatorLongitude; }
        int getAreaRadius() { return areaRadius * 10; }
        static double calcAltitude(int value) { return (double) value / 2 - 1000; }
        double getAreaCeiling() { return calcAltitude(areaCeiling); }
        double getAreaFloor() { return calcAltitude(areaFloor); }
        double getOperatorAltitudeGeo() { return calcAltitude(operatorAltitudeGeo); }

        public static String csvHeader() {
            return "operatorLocationType" + DELIM + "classificationType" + DELIM
                    + "operatorLatitude" + DELIM + "operatorLongitude" + DELIM
                    + "areaCount" + DELIM + "areaRadius" + DELIM + "areaCeiling" + DELIM
                    + "areaFloor" + DELIM + "category" + DELIM + "classValue" + DELIM
                    + "operatorAltitudeGeo" + DELIM + "systemTimestamp" + DELIM;
        }
        @Override public String toCsvString() {
            return operatorLocationType + DELIM + classificationType + DELIM
                    + operatorLatitude + DELIM + operatorLongitude + DELIM
                    + areaCount + DELIM + areaRadius + DELIM + areaCeiling + DELIM
                    + areaFloor + DELIM + category + DELIM + classValue + DELIM
                    + operatorAltitudeGeo + DELIM + systemTimestamp + DELIM;
        }
        @Override public String toString() {
            return "SystemMsg{operatorLat=" + operatorLatitude + ", operatorLon=" + operatorLongitude + '}';
        }
    }

    public static class OperatorID implements Payload {
        int operatorIdType;
        final byte[] operatorId = new byte[Constants.MAX_ID_BYTE_SIZE];

        public static String csvHeader() {
            return "operatorIdType" + DELIM + "operatorId" + DELIM;
        }
        @Override public String toCsvString() {
            return operatorIdType + DELIM + new String(operatorId) + DELIM;
        }
        @Override public String toString() {
            return "OperatorID{operatorIdType=" + operatorIdType + '}';
        }
    }

    public static class MessagePack implements Payload {
        int messageSize, messagesInPack;
        final byte[] messages = new byte[Constants.MAX_MESSAGE_PACK_SIZE];
        @Override public String toCsvString() { return null; }
        @Override public String toString() {
            return "MessagePack{messageSize=" + messageSize + ", messagesInPack=" + messagesInPack + '}';
        }
    }

    public static class Message<T extends Payload> implements Comparable<Message<T>> {
        final int msgCounter;
        final long timestamp;
        public final Header header;
        public final T payload;

        Message(Header header, T payload, long timestamp, int msgCounter) {
            this.msgCounter = msgCounter;
            this.header = header;
            this.payload = payload;
            this.timestamp = timestamp;
        }

        @Override
        public int compareTo(Message o) {
            if (this.header.type == Type.AUTH && o.header.type == Type.AUTH) {
                Authentication authThis = (Authentication) this.payload;
                Authentication authO = (Authentication) o.payload;
                return authThis.authDataPage - authO.authDataPage;
            } else {
                return this.header.type.compareTo(o.header.type);
            }
        }
    }

    static Message<Payload> parseData(byte[] payload, int offset, long timestamp,
                                      LogMessageEntry logMessageEntry,
                                      GpsLocation receiverLocation) {
        if (offset <= 0 || payload.length < offset + Constants.MAX_MESSAGE_SIZE)
            return null;
        int msgCounter = payload[offset - 1] & 0xFF;
        return parseMessage(payload, offset, timestamp, logMessageEntry, receiverLocation, msgCounter);
    }

    static Message<Payload> parseMessage(byte[] payload, int offset, long timestamp,
                                         LogMessageEntry logMessageEntry,
                                         GpsLocation receiverLocation, int msgCounter) {
        if (payload.length < offset + Constants.MAX_MESSAGE_SIZE)
            return null;

        ByteBuffer byteBuffer = ByteBuffer.wrap(payload, offset, Constants.MAX_MESSAGE_SIZE);
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN);

        Header header = new Header();
        int b = byteBuffer.get() & 0xFF;
        int type = (b & 0xF0) >> 4;
        header.type = Type.fromId(type);
        if (header.type == null) {
            LOGGER.severe("Header type unknown: " + type);
            return null;
        }
        header.version = b & 0x0F;

        Payload payloadObj = null;
        switch (header.type) {
            case BASIC_ID:    payloadObj = parseBasicId(byteBuffer); break;
            case LOCATION:    payloadObj = parseLocation(byteBuffer, receiverLocation); break;
            case AUTH:        payloadObj = parseAuthentication(byteBuffer); break;
            case SELFID:      payloadObj = parseSelfID(byteBuffer); break;
            case SYSTEM:      payloadObj = parseSystem(byteBuffer); break;
            case OPERATOR_ID: payloadObj = parseOperatorID(byteBuffer); break;
            case MESSAGE_PACK: payloadObj = parseMessagePack(payload, offset); break;
            default:
                LOGGER.warning("Received unhandled message type: id=" + type);
        }
        Message<Payload> message = new Message<>(header, payloadObj, timestamp, msgCounter);
        logMessageEntry.setMsgVersion(message.header.version);
        if (header.type != Type.MESSAGE_PACK)
            logMessageEntry.add(message);
        return message;
    }

    private static BasicId parseBasicId(ByteBuffer byteBuffer) {
        BasicId basicId = new BasicId();
        int type = byteBuffer.get();
        basicId.idType = (type & 0xF0) >> 4;
        basicId.uaType = type & 0x0F;
        byteBuffer.get(basicId.uasId, 0, Constants.MAX_ID_BYTE_SIZE);
        return basicId;
    }

    private static Location parseLocation(ByteBuffer byteBuffer, GpsLocation receiverLocation) {
        Location location = new Location();
        int b = byteBuffer.get();
        location.status = (b & 0xF0) >> 4;
        location.heightType = (b & 0x04) >> 2;
        location.EWDirection = (b & 0x02) >> 1;
        location.speedMult = b & 0x01;
        location.Direction = byteBuffer.get() & 0xFF;
        location.speedHori = byteBuffer.get() & 0xFF;
        location.speedVert = byteBuffer.get();
        location.droneLat = byteBuffer.getInt();
        location.droneLon = byteBuffer.getInt();
        location.altitudePressure = byteBuffer.getShort() & 0xFFFF;
        location.altitudeGeodetic = byteBuffer.getShort() & 0xFFFF;
        location.height = byteBuffer.getShort() & 0xFFFF;
        int horiVertAccuracy = byteBuffer.get();
        location.horizontalAccuracy = horiVertAccuracy & 0x0F;
        location.verticalAccuracy = (horiVertAccuracy & 0xF0) >> 4;
        int speedBaroAccuracy = byteBuffer.get();
        location.baroAccuracy = (speedBaroAccuracy & 0xF0) >> 4;
        location.speedAccuracy = speedBaroAccuracy & 0x0F;
        location.timestamp = byteBuffer.getShort() & 0xFFFF;
        location.timeAccuracy = byteBuffer.get() & 0x0F;

        if (location.droneLat != 0 && location.droneLon != 0) {
            GpsLocation droneLoc = new GpsLocation();
            droneLoc.setLatitude(location.getLatitude());
            droneLoc.setLongitude(location.getLongitude());
            if (receiverLocation != null)
                location.distance = receiverLocation.distanceTo(droneLoc);
        }
        return location;
    }

    private static Authentication parseAuthentication(ByteBuffer byteBuffer) {
        Authentication authentication = new Authentication();
        int type = byteBuffer.get();
        authentication.authType = (type & 0xF0) >> 4;
        authentication.authDataPage = type & 0x0F;
        int offset = 0;
        int amount = Constants.MAX_AUTH_PAGE_ZERO_SIZE;
        if (authentication.authDataPage == 0) {
            authentication.authLastPageIndex = byteBuffer.get() & 0xFF;
            authentication.authLength = byteBuffer.get() & 0xFF;
            authentication.authTimestamp = byteBuffer.getInt() & 0xFFFFFFFFL;
            int len = authentication.authLastPageIndex * Constants.MAX_AUTH_PAGE_NON_ZERO_SIZE
                    + Constants.MAX_AUTH_PAGE_ZERO_SIZE;
            if (authentication.authLastPageIndex >= Constants.MAX_AUTH_DATA_PAGES
                    || authentication.authLength > len) {
                authentication.authLastPageIndex = 0;
                authentication.authLength = 0;
                authentication.authTimestamp = 0;
            } else {
                authentication.authLength = len;
            }
        } else {
            offset = Constants.MAX_AUTH_PAGE_ZERO_SIZE
                    + (authentication.authDataPage - 1) * Constants.MAX_AUTH_PAGE_NON_ZERO_SIZE;
            amount = Constants.MAX_AUTH_PAGE_NON_ZERO_SIZE;
        }
        if (authentication.authDataPage >= 0 && authentication.authDataPage < Constants.MAX_AUTH_DATA_PAGES)
            for (int i = offset; i < offset + amount; i++)
                authentication.authData[i] = byteBuffer.get();
        return authentication;
    }

    private static SelfID parseSelfID(ByteBuffer byteBuffer) {
        SelfID selfID = new SelfID();
        selfID.descriptionType = byteBuffer.get() & 0xFF;
        byteBuffer.get(selfID.operationDescription, 0, Constants.MAX_STRING_BYTE_SIZE);
        return selfID;
    }

    private static SystemMsg parseSystem(ByteBuffer byteBuffer) {
        SystemMsg s = new SystemMsg();
        int b = byteBuffer.get();
        s.operatorLocationType = b & 0x03;
        s.classificationType = (b & 0x1C) >> 2;
        s.operatorLatitude = byteBuffer.getInt();
        s.operatorLongitude = byteBuffer.getInt();
        s.areaCount = byteBuffer.getShort() & 0xFFFF;
        s.areaRadius = byteBuffer.get() & 0xFF;
        s.areaCeiling = byteBuffer.getShort() & 0xFFFF;
        s.areaFloor = byteBuffer.getShort() & 0xFFFF;
        b = byteBuffer.get();
        s.category = (b & 0xF0) >> 4;
        s.classValue = b & 0x0F;
        s.operatorAltitudeGeo = byteBuffer.getShort() & 0xFFFF;
        s.systemTimestamp = byteBuffer.getInt() & 0xFFFFFFFFL;
        return s;
    }

    private static OperatorID parseOperatorID(ByteBuffer byteBuffer) {
        OperatorID operatorID = new OperatorID();
        operatorID.operatorIdType = byteBuffer.get() & 0xFF;
        byteBuffer.get(operatorID.operatorId, 0, Constants.MAX_ID_BYTE_SIZE);
        return operatorID;
    }

    private static MessagePack parseMessagePack(byte[] payload, int offset) {
        ByteBuffer byteBuffer = ByteBuffer.wrap(payload, offset + 1, 2);
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
        MessagePack messagePack = new MessagePack();
        messagePack.messageSize = byteBuffer.get() & 0xFF;
        messagePack.messagesInPack = byteBuffer.get() & 0xFF;
        if (messagePack.messageSize != Constants.MAX_MESSAGE_SIZE
                || messagePack.messagesInPack <= 0
                || messagePack.messagesInPack > Constants.MAX_MESSAGES_IN_PACK
                || payload.length < offset + 1 + 2 + messagePack.messageSize * messagePack.messagesInPack)
            return null;
        byteBuffer = ByteBuffer.wrap(payload, offset + 1 + 2, messagePack.messageSize * messagePack.messagesInPack);
        byteBuffer.get(messagePack.messages, 0, messagePack.messageSize * messagePack.messagesInPack);
        return messagePack;
    }
}
