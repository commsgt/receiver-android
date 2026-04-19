package org.opendroneid.android.data;

import org.opendroneid.android.Constants;

import java.sql.Timestamp;
import java.util.Locale;

public class AuthenticationData extends MessageData {

    private AuthTypeEnum authType;
    private int authDataPage;
    private int authLastPageIndex;
    private int authLength;
    private long authTimestamp;
    private byte[] authData;

    public AuthenticationData() {
        super();
        authType = AuthTypeEnum.None;
        authDataPage = 0;
        authLastPageIndex = 0;
        authLength = 0;
        authTimestamp = 0;
        authData = new byte[0];
    }

    public enum AuthTypeEnum {
        None(0), UAS_ID_Signature(1), Operator_ID_Signature(2), Message_Set_Signature(3),
        Network_Remote_ID(4), Specific_Authentication(5),
        Private_Use_0xA(0xA), Private_Use_0xB(0xB), Private_Use_0xC(0xC),
        Private_Use_0xD(0xD), Private_Use_0xE(0xE), Private_Use_0xF(0xF);
        AuthTypeEnum(int id) { this.id = id; }
        public final int id;
    }

    public AuthTypeEnum getAuthType() { return authType; }
    void setAuthType(AuthTypeEnum authType) { this.authType = authType; }
    public void setAuthType(int authType) {
        switch (authType) {
            case 1: this.authType = AuthTypeEnum.UAS_ID_Signature; break;
            case 2: this.authType = AuthTypeEnum.Operator_ID_Signature; break;
            case 3: this.authType = AuthTypeEnum.Message_Set_Signature; break;
            case 4: this.authType = AuthTypeEnum.Network_Remote_ID; break;
            case 5: this.authType = AuthTypeEnum.Specific_Authentication; break;
            case 0xA: this.authType = AuthTypeEnum.Private_Use_0xA; break;
            case 0xB: this.authType = AuthTypeEnum.Private_Use_0xB; break;
            case 0xC: this.authType = AuthTypeEnum.Private_Use_0xC; break;
            case 0xD: this.authType = AuthTypeEnum.Private_Use_0xD; break;
            case 0xE: this.authType = AuthTypeEnum.Private_Use_0xE; break;
            case 0xF: this.authType = AuthTypeEnum.Private_Use_0xF; break;
            default: this.authType = AuthTypeEnum.None; break;
        }
    }

    public int getAuthDataPage() { return authDataPage; }
    public void setAuthDataPage(int authDataPage) { this.authDataPage = authDataPage; }

    public int getAuthLastPageIndex() { return authLastPageIndex; }
    public void setAuthLastPageIndex(int authLastPageIndex) { this.authLastPageIndex = authLastPageIndex; }

    public int getAuthLength() { return authLength; }
    public void setAuthLength(int authLength) { this.authLength = authLength; }

    public long getAuthTimestamp() { return authTimestamp; }
    public void setAuthTimestamp(long authTimestamp) { this.authTimestamp = authTimestamp; }
    public String getAuthTimestampAsString() {
        if (authTimestamp == 0) return "Unknown";
        long ms = authTimestamp * 1000L;
        long unixMs = 1546300800000L + ms; // Jan 1 2019 00:00:00 UTC
        return new Timestamp(unixMs).toString();
    }

    public byte[] getAuthData() { return authData; }
    public void setAuthData(byte[] authData) { this.authData = authData; }
    public String getAuthDataAsString() {
        if (authData == null) return "";
        StringBuilder sb = new StringBuilder();
        for (byte b : authData) sb.append(String.format(Locale.US, "%02X", b));
        return sb.toString();
    }
}
