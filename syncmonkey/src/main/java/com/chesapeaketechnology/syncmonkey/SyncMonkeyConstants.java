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
    public static final String COLON_SEPARATOR = ":";

    // Properties
    public static final String PROPERTY_CONFIG_NAME_KEY = "configName";
    public static final String PROPERTY_CONTAINER_NAME_KEY = "containerName";
    public static final String PROPERTY_AZURE_SAS_URL_KEY = "sas_url";
    public static final String PROPERTY_REMOTE_TYPE_KEY = "remoteType";
    public static final String PROPERTY_LOCAL_SYNC_DIRECTORIES_KEY = "localSyncDirectories";
    public static final String PROPERTY_DEVICE_ID_KEY = "deviceId";
    public static final String PROPERTY_AUTO_START_ON_BOOT_KEY = "autoStartOnBoot";
    public static final String PROPERTY_VPN_ONLY_KEY = "vpnOnly";
    public static final String PROPERTY_WIFI_ONLY_KEY = "wifiOnly";

    public static final String DEFAULT_DEVICE_ID = "UnknownDeviceId";
}
