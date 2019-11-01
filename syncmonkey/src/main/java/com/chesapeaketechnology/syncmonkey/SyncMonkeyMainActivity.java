package com.chesapeaketechnology.syncmonkey;

import android.Manifest;
import android.accounts.Account;
import android.accounts.AccountManager;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.RestrictionsManager;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.ActivityCompat;
import androidx.preference.PreferenceManager;

import com.chesapeaketechnology.syncmonkey.fileupload.FileUploadSyncAdapter;
import com.chesapeaketechnology.syncmonkey.settings.SettingsActivity;

import net.grandcentrix.tray.AppPreferences;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;

public class SyncMonkeyMainActivity extends AppCompatActivity
{
    private static final String LOG_TAG = SyncMonkeyMainActivity.class.getSimpleName();

    private static final int ACCESS_PERMISSION_REQUEST_ID = 1;

    private static Account dummyAccount;

    private AppPreferences appPreferences;
    private BroadcastReceiver managedConfigurationListener;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        Log.i(LOG_TAG, "Starting the SyncMonkey App");

        appPreferences = new AppPreferences(this);

        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);

        setContentView(R.layout.activity_main);
        findViewById(R.id.button).setOnClickListener(listener -> runSyncAdapter());

        // Create the dummy account
        dummyAccount = getSyncAccount(this);

        // Install the defaults specified in the XML preferences file, this is only done the first time the app is opened
        androidx.preference.PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
        copyDefaultSharedPreferencesToTrayPreferences();

        ActivityCompat.requestPermissions(this, new String[]{
                        Manifest.permission.READ_PHONE_STATE,
                        Manifest.permission.READ_EXTERNAL_STORAGE},
                ACCESS_PERMISSION_REQUEST_ID);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults)
    {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == ACCESS_PERMISSION_REQUEST_ID)
        {
            for (int index = 0; index < permissions.length; index++)
            {
                if (Manifest.permission.READ_PHONE_STATE.equals(permissions[index]))
                {
                    initializeDeviceId();

                    if (grantResults[index] == PackageManager.PERMISSION_DENIED) Log.w(LOG_TAG, "The READ_PHONE_STATE Permission was denied.");
                } else if (Manifest.permission.READ_EXTERNAL_STORAGE.equals(permissions[index]))
                {
                    if (grantResults[index] == PackageManager.PERMISSION_GRANTED)
                    {
                        initializeSyncAdapterIfNecessary();
                    } else
                    {
                        Log.w(LOG_TAG, "The READ_EXTERNAL_STORAGE Permission was denied.");
                    }
                }
            }
        }
    }

    @Override
    protected void onResume()
    {
        super.onResume();

        // Really just a sanity check since it should have been initialized in the onCreate method
        if (appPreferences == null)
        {
            Log.wtf(LOG_TAG, "Somehow the Tray App Preferences were null in the onResume call");
            appPreferences = new AppPreferences(this);
        }

        // Per the Android developer tutorials it is recommended to read the managed configuration in the onResume method
        readSyncMonkeyProperties(this, appPreferences); // The properties and managed config need to be read before installing the rclone config file
        readSyncMonkeyManagedConfiguration(this, appPreferences);

        installRcloneConfigFile(this, appPreferences);

        managedConfigurationListener = registerManagedConfigurationListener(this, appPreferences);

        initializeSyncAdapterIfNecessary();
    }

    @Override
    protected void onPause()
    {
        super.onPause();

        if (managedConfigurationListener != null)
        {
            try
            {
                getApplicationContext().unregisterReceiver(managedConfigurationListener);
            } catch (Exception e)
            {
                Log.e(LOG_TAG, "Unable to unregister the Managed Configuration Listener when pausing the app");
            }
            managedConfigurationListener = null;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_sync_monkey, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        int id = item.getItemId();

        if (id == R.id.action_settings)
        {
            startActivity(new Intent(SyncMonkeyMainActivity.this, SettingsActivity.class));
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * Get the device ID, and place it in the shared preferences.  This is needed to represent the device specific directory on the remote upload server.
     */
    @SuppressLint("ApplySharedPref")
    private void initializeDeviceId()
    {
        if (appPreferences.contains(SyncMonkeyConstants.PROPERTY_DEVICE_ID_KEY))
        {
            Log.i(LOG_TAG, "The Device ID is already present in the Shared Preferences, skipping setting it to the App's default ID.");
            return;
        }

        appPreferences.put(SyncMonkeyConstants.PROPERTY_DEVICE_ID_KEY, getDeviceId());
    }

    /**
     * Reads all the preferences from the Default Shared Preferences and copies them over to the Tray Preferences IF they have not been copied before.  In
     * other words, thye are only copied on on first run of the app.  The Tray Preferences are used instead of the Default Shared Preferences because the Tray
     * Preferences allow for the sync adapter thread to get access to the latest user settings.  Each thread has their own copy of the Default Shared
     * Preferences so if the user changes the settings via the UI, the sync adapter won't know about those changes
     * until the app is stopped and started again.
     *
     * @since 0.0.8
     */
    private synchronized void copyDefaultSharedPreferencesToTrayPreferences()
    {
        // Only apply the defaults on the first run of the app.
        if (!appPreferences.getBoolean(PreferenceManager.KEY_HAS_SET_DEFAULT_VALUES, false))
        {
            PreferenceManager.getDefaultSharedPreferences(this).getAll().forEach((key, value) -> {

                if (value instanceof Boolean)
                {
                    appPreferences.put(key, (Boolean) value);
                } else if (value instanceof String)
                {
                    appPreferences.put(key, (String) value);
                } else
                {
                    Log.wtf(LOG_TAG, "There was not a mapping for a user preference to set it in the Tray Preferences");
                }
            });

            appPreferences.put(PreferenceManager.KEY_HAS_SET_DEFAULT_VALUES, true);
        }
    }

    /**
     * Initializes the sync adapter to run at a periodic interval if the Auto Start preferences is set to true AND the periodic sync adapter is not already added.
     */
    private synchronized void initializeSyncAdapterIfNecessary()
    {
        Log.i(LOG_TAG, "Initializing the Sync Monkey Sync Adapter");

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
        {
            Log.e(LOG_TAG, "Can't initialize the sync adapter schedule because we don't have access to read external storage");
            return;
        }

        final boolean autoStartOnBootPreference = appPreferences.getBoolean(SyncMonkeyConstants.PROPERTY_AUTO_START_ON_BOOT_KEY, true);

        final Account syncAccount = getSyncAccount(this);
        if (autoStartOnBootPreference)
        {
            // Per the ContentResolver#addPeriodicSync javadoc, if the is already another periodic sync scheduled with the account, authority, and extras, then
            // a new periodic sync won't be added.  While the requestSync javadoc does not also say that, my guess is it is true as well.  Probably worth
            // verifying at some point.
            Log.i(LOG_TAG, "Adding the periodic sync adapter for Sync Monkey");
            ContentResolver.requestSync(FileUploadSyncAdapter.generatePeriodicSyncRequest(this));
        } else
        {
            Log.i(LOG_TAG, "Removing the periodic sync adapter for Sync Monkey");
            ContentResolver.removePeriodicSync(syncAccount, SyncMonkeyConstants.AUTHORITY, new Bundle());
        }
    }

    /**
     * Respond to a button click by calling requestSync(). This is an asynchronous operation.
     * <p>
     * This method is attached to the refresh button in the layout XML file.
     */
    private void runSyncAdapter()
    {
        // Pass the settings flags by inserting them in a bundle
        Bundle settingsBundle = new Bundle();
        settingsBundle.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);
        settingsBundle.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true);
        /*
         * Request the sync for the default account, authority, and
         * manual sync settings
         */
        ContentResolver.requestSync(dummyAccount, SyncMonkeyConstants.AUTHORITY, settingsBundle);
    }

    /**
     * Create a new dummy account for the sync adapter.
     *
     * @param context The application context
     */
    public static Account getSyncAccount(Context context)
    {
        if (dummyAccount == null)
        {
            Log.i(LOG_TAG, "Creating a new Sync Account");
            dummyAccount = new Account(SyncMonkeyConstants.ACCOUNT, SyncMonkeyConstants.ACCOUNT_TYPE);
            final AccountManager accountManager = (AccountManager) context.getSystemService(ACCOUNT_SERVICE);

            /*
             * Add the account and account type, no password or user data
             * If successful, return the Account object, otherwise report an error.
             */
            if (accountManager != null && accountManager.addAccountExplicitly(dummyAccount, null, null))
            {
                /*
                 * If you don't set android:syncable="true" in
                 * in your <provider> element in the manifest,
                 * then call context.setIsSyncable(account, AUTHORITY, 1)
                 * here.
                 */
                return dummyAccount;
            } else
            {
                /*
                 * The account exists or some other error occurred. Log this, report it,
                 * or handle it internally.
                 */
                Log.v(LOG_TAG, "The account already exists, or the account manager could not be found");
            }
        }

        return dummyAccount;
    }

    /**
     * Reads the {@link SyncMonkeyConstants#SYNC_MONKEY_PROPERTIES_FILE} and loads the values into the App's Shared Preferences.
     */
    @SuppressLint("ApplySharedPref")
    public static void readSyncMonkeyProperties(Context context, AppPreferences appPreferences)
    {
        try (final InputStream propertiesInputStream = context.getAssets().open(SyncMonkeyConstants.SYNC_MONKEY_PROPERTIES_FILE))
        {
            // First read in the values from the properties file
            Log.i(LOG_TAG, "Reading in the Sync Monkey properties file");
            final Properties properties = new Properties();

            properties.load(propertiesInputStream);
            properties.entrySet().forEach(preferenceEntry -> {
                // Custom handling for the boolean preferences
                final String key = (String) preferenceEntry.getKey();
                switch (key)
                {
                    case SyncMonkeyConstants.PROPERTY_AUTO_START_ON_BOOT_KEY:
                    case SyncMonkeyConstants.PROPERTY_VPN_ONLY_KEY:
                    case SyncMonkeyConstants.PROPERTY_WIFI_ONLY_KEY:
                        appPreferences.put(key, Boolean.parseBoolean((String) preferenceEntry.getValue()));
                        break;

                    default:
                        appPreferences.put(key, (String) preferenceEntry.getValue());
                }
            });

            /*if (Log.isLoggable(LOG_TAG, Log.INFO))
            {
                Log.i(LOG_TAG, "The Properties after reading in the properties file: " + preferences.getAll().toString());
            }*/
        } catch (Exception e)
        {
            Log.e(LOG_TAG, "Can't open the Sync Monkey properties file or write a preference to the shared preferences", e);
        }
    }

    /**
     * Reads the Sync Monkey Managed Configuration and loads the values into the App's Shared Preferences.
     */
    @SuppressLint("ApplySharedPref")
    public static void readSyncMonkeyManagedConfiguration(Context context, AppPreferences appPreferences)
    {
        try
        {
            // Next, read any MDM set values.  Doing this last so that we can overwrite the values from the properties file
            Log.i(LOG_TAG, "Reading in any MDM configured properties");
            final RestrictionsManager restrictionsManager = (RestrictionsManager) context.getSystemService(Context.RESTRICTIONS_SERVICE);
            if (restrictionsManager != null)
            {
                final Bundle mdmProperties = restrictionsManager.getApplicationRestrictions();

                mdmProperties.keySet().forEach(key -> {
                    final Object property = mdmProperties.get(key);
                    if (property instanceof String)
                    {
                        appPreferences.put(key, (String) property);
                    } else if (property instanceof Boolean)
                    {
                        appPreferences.put(key, (Boolean) property);
                    }
                });
            }

            /*if (Log.isLoggable(LOG_TAG, Log.INFO))
            {
                Log.i(LOG_TAG, "The Properties after reading in the managed config: " + preferences.getAll().toString());
            }*/
        } catch (Exception e)
        {
            Log.e(LOG_TAG, "Can't read the Sync Monkey managed configuration", e);
        }
    }

    /**
     * Register a listener so that if the Managed Config changes we will be notified of the new config.
     */
    public static BroadcastReceiver registerManagedConfigurationListener(Context context, AppPreferences appPreferences)
    {
        final IntentFilter restrictionsFilter = new IntentFilter(Intent.ACTION_APPLICATION_RESTRICTIONS_CHANGED);

        final BroadcastReceiver restrictionsReceiver = new BroadcastReceiver()
        {
            @Override
            public void onReceive(Context context, Intent intent)
            {
                readSyncMonkeyManagedConfiguration(context, appPreferences);
                installRcloneConfigFile(context, appPreferences);
            }
        };

        context.registerReceiver(restrictionsReceiver, restrictionsFilter);

        return restrictionsReceiver;
    }

    /**
     * Checks to see if the SAS key is in the app properties.  If it is, then create the config file using the properties
     */
    public static void installRcloneConfigFile(Context context, AppPreferences appPreferences)
    {
        if (appPreferences.contains(SyncMonkeyConstants.PROPERTY_AZURE_SAS_URL_KEY))
        {
            Log.i(LOG_TAG, "Found the Azure SAS URL Property, creating a new rclone.conf file");
            createNewRcloneConfigFile(context, appPreferences);
        } else
        {
            Log.i(LOG_TAG, "Did not find the Azure SAS URL Property, copying the rclone.conf file that was packaged with the APK");
            copyRcloneConfigFile(context);
        }
    }

    /**
     * Creates a new {@link SyncMonkeyConstants#RCLONE_CONFIG_FILE} in the app's private storage area using the values from the shared preferences.
     */
    private static synchronized void createNewRcloneConfigFile(Context context, AppPreferences appPreferences)
    {
        final File rcloneConfigFile = new File(context.getFilesDir(), SyncMonkeyConstants.RCLONE_CONFIG_FILE);

        // The rclone.conf file does not exist, so copy it out of assets.
        try (final OutputStream privateAppRcloneConfigFileOutputStream = new FileOutputStream(rcloneConfigFile))
        {
            final String sasUrl = appPreferences.getString(SyncMonkeyConstants.PROPERTY_AZURE_SAS_URL_KEY, null);

            if (sasUrl == null)
            {
                Log.e(LOG_TAG, "One of the values were null when trying to create a new rclone config file");
                return;
            }

            final String configNameWithBracketsEntry = "[" + SyncMonkeyConstants.AZURE_CONFIG_NAME + "]" + System.lineSeparator();
            final String typeEntry = "type = " + SyncMonkeyConstants.AZURE_REMOTE_TYPE + System.lineSeparator();
            final String sasUrlEntry = "sas_url = " + sasUrl + System.lineSeparator();

            if (Log.isLoggable(LOG_TAG, Log.INFO))
            {
                Log.i(LOG_TAG, "configNameWithBracketsEntry=" + configNameWithBracketsEntry);
                Log.i(LOG_TAG, "typeEntry=" + typeEntry);
            }

            privateAppRcloneConfigFileOutputStream.write(configNameWithBracketsEntry.getBytes());
            privateAppRcloneConfigFileOutputStream.write(typeEntry.getBytes());
            privateAppRcloneConfigFileOutputStream.write(sasUrlEntry.getBytes());

            privateAppRcloneConfigFileOutputStream.flush();
        } catch (FileNotFoundException e)
        {
            final String message = "The " + SyncMonkeyConstants.RCLONE_CONFIG_FILE + " file was not found in the app's assets directory";
            Log.e(LOG_TAG, message, e);
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
        } catch (IOException e)
        {
            final String message = "Could not create the " + SyncMonkeyConstants.RCLONE_CONFIG_FILE + " file";
            Log.e(LOG_TAG, message, e);
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Copies the {@link SyncMonkeyConstants#RCLONE_CONFIG_FILE} from the assets directory to the app's private storage area.
     */
    private static synchronized void copyRcloneConfigFile(Context context)
    {
        final File rcloneConfigFile = new File(context.getFilesDir(), SyncMonkeyConstants.RCLONE_CONFIG_FILE);

        // The rclone.conf file does not exist, so copy it out of assets.
        try (final InputStream assetRcloneConfigFileInputStream = context.getAssets().open(SyncMonkeyConstants.RCLONE_CONFIG_FILE);
             final OutputStream privateAppRcloneConfigFileOutputStream = new FileOutputStream(rcloneConfigFile))
        {
            copyInputStreamToOutputStream(assetRcloneConfigFileInputStream, privateAppRcloneConfigFileOutputStream);
        } catch (FileNotFoundException e)
        {
            final String message = "The " + SyncMonkeyConstants.RCLONE_CONFIG_FILE + " file was not found in the app's assets directory";
            Log.e(LOG_TAG, message, e);
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
        } catch (IOException e)
        {
            final String message = "Could not create the " + SyncMonkeyConstants.RCLONE_CONFIG_FILE + " file";
            Log.e(LOG_TAG, message, e);
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Copies the provide input stream to the provided output stream.
     *
     * @throws IOException If the first byte cannot be read for any reason other than the end of the file, if the input stream has been closed, or if some
     *                     other I/O error occurs.
     */
    private static void copyInputStreamToOutputStream(InputStream in, OutputStream out) throws IOException
    {
        final byte[] buffer = new byte[1024];
        int read;
        while ((read = in.read(buffer)) != -1)
        {
            out.write(buffer, 0, read);
        }
    }

    /**
     * Attempts to get the device's IMEI if the user has granted the permission.  If not, then a default ID it used.
     *
     * @return The IMEI if it can be found, otherwise the Android ID.
     */
    @SuppressWarnings("ConstantConditions")
    @SuppressLint({"HardwareIds", "MissingPermission"})
    private String getDeviceId()
    {
        String deviceId = null;
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
                && getSystemService(Context.TELEPHONY_SERVICE) != null
                && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) // As of Android API level 29 the IMEI permission is restricted to system apps only.
        {
            TelephonyManager telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            {
                deviceId = telephonyManager.getImei();
            } else
            {
                deviceId = telephonyManager.getDeviceId();
            }
        }

        // Fall back on the ANDROID_ID
        if (deviceId == null)
        {
            Log.w(LOG_TAG, "Could not get the device IMEI");
            //Toast.makeText(getApplicationContext(), "Could not get the device IMEI", Toast.LENGTH_SHORT).show();
            deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        }

        return deviceId;
    }
}
