package com.chesapeaketechnology.syncmonkey;

/**
 * Some constants used in the App.
 *
 * @since 0.0.1
 */
@SuppressWarnings("WeakerAccess")
public class SyncMonkeyConstants
{
    private SyncMonkeyConstants()
    {
    }

    public static final String RCLONE_CONFIG_FILE = "rclone.conf";
    public static final String SYNC_MONKEY_PROPERTIES_FILE = "syncmonkey.properties";

    // The authority for the sync adapter's content provider
    public static final String AUTHORITY = "com.chesapeaketechnology.sycnmonkey.provider";
    // An account type, in the form of a domain name
    public static final String ACCOUNT_TYPE = "rfmonkey.com";
    // The account name
    public static final String ACCOUNT = "dummyaccount";

    public static final int SECONDS_IN_HOUR = 3600;

    // Properties
    public static final String PROPERTY_REMOTE_NAME = "remoteName";
    public static final String PROPERTY_REMOTE_TYPE = "remoteType";
    public static final String PROPERTY_LOCAL_SYNC_DIRECTORIES = "localSyncDirectories";
    public static final String PROPERTY_DEVICE_ID = "deviceId";
    public static final String PROPERTY_AUTO_START_ON_BOOT = "autoStartOnBoot";

    public static final String DEFAULT_DEVICE_ID = "UnknownDeviceId";
}
