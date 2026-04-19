package org.opendroneid.android;

public class Constants {
    public static final String DELIM = ",";

    public static final int MAX_ID_BYTE_SIZE = 20;
    public static final int MAX_STRING_BYTE_SIZE = 23;
    public static final int MAX_AUTH_DATA_PAGES = 16;
    public static final int MAX_AUTH_PAGE_ZERO_SIZE = 17;
    public static final int MAX_AUTH_PAGE_NON_ZERO_SIZE = 23;
    public static final int MAX_AUTH_DATA = MAX_AUTH_PAGE_ZERO_SIZE + (MAX_AUTH_DATA_PAGES - 1) * MAX_AUTH_PAGE_NON_ZERO_SIZE;
    public static final int MAX_MESSAGE_SIZE = 25;
    public static final int MAX_MESSAGES_IN_PACK = 9;
    public static final int MAX_MESSAGE_PACK_SIZE = MAX_MESSAGE_SIZE * MAX_MESSAGES_IN_PACK;
    public static final int MAX_MSG_VERSION = 2;
}
