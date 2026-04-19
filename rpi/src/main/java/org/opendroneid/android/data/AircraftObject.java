package org.opendroneid.android.data;

import org.opendroneid.android.Constants;
import org.opendroneid.rpi.ObservableValue;

public class AircraftObject {
    final public ObservableValue<Connection> connection = new ObservableValue<>();
    final public ObservableValue<Identification> identification1 = new ObservableValue<>();
    final public ObservableValue<Identification> identification2 = new ObservableValue<>();
    final public ObservableValue<LocationData> location = new ObservableValue<>();
    final public ObservableValue<AuthenticationData> authentication = new ObservableValue<>();
    final public ObservableValue<SelfIdData> selfid = new ObservableValue<>();
    final public ObservableValue<SystemData> system = new ObservableValue<>();
    final public ObservableValue<OperatorIdData> operatorid = new ObservableValue<>();

    private final long macAddress;

    public AircraftObject(long macAddress) {
        this.macAddress = macAddress;
    }
    public long getMacAddress() { return macAddress; }

    public Connection getConnection() { return connection.getValue(); }
    public Identification getIdentification1() { return identification1.getValue(); }
    public Identification getIdentification2() { return identification2.getValue(); }
    public LocationData getLocation() { return location.getValue(); }
    public AuthenticationData getAuthentication() { return authentication.getValue(); }
    public SelfIdData getSelfID() { return selfid.getValue(); }
    public SystemData getSystem() { return system.getValue(); }
    public OperatorIdData getOperatorID() { return operatorid.getValue(); }

    private int authLastPageIndexSave;
    private int authLengthSave;
    private long authTimestampSave;

    private final byte[] authDataCombined = new byte[Constants.MAX_AUTH_DATA];

    public AuthenticationData combineAuthentication(AuthenticationData newData) {
        AuthenticationData currData = authentication.getValue();
        if (currData == null) currData = new AuthenticationData();

        currData.setMsgCounter(newData.getMsgCounter());
        currData.setTimestamp(newData.getTimestamp());
        currData.setMsgVersion(newData.getMsgVersion());

        int offset = 0;
        int amount = Constants.MAX_AUTH_PAGE_ZERO_SIZE;
        if (newData.getAuthDataPage() == 0) {
            authLastPageIndexSave = newData.getAuthLastPageIndex();
            authLengthSave = newData.getAuthLength();
            authTimestampSave = newData.getAuthTimestamp();
        } else {
            offset = Constants.MAX_AUTH_PAGE_ZERO_SIZE + (newData.getAuthDataPage() - 1) * Constants.MAX_AUTH_PAGE_NON_ZERO_SIZE;
            amount = Constants.MAX_AUTH_PAGE_NON_ZERO_SIZE;
        }
        for (int i = offset; i < offset + amount; i++)
            authDataCombined[i] = newData.getAuthData()[i];

        currData.setAuthType(newData.getAuthType().id);
        currData.setAuthLastPageIndex(authLastPageIndexSave);
        currData.setAuthLength(authLengthSave);
        currData.setAuthTimestamp(authTimestampSave);
        currData.setAuthData(authDataCombined);
        return currData;
    }

    @Override
    public String toString() {
        return "AircraftObject{macAddress=" + macAddress
                + ", identification1=" + identification1.getValue()
                + ", identification2=" + identification2.getValue() + '}';
    }
}
