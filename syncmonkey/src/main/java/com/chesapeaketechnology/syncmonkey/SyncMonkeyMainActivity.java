package com.chesapeaketechnology.syncmonkey;

import android.Manifest;
import android.accounts.Account;
import android.accounts.AccountManager;
import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.ActivityCompat;

import com.chesapeaketechnology.syncmonkey.fileupload.FileUploadSyncAdapter;

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

    private Account dummyAccount;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        Log.i(LOG_TAG, "Starting the SyncMonkey App");

        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);

        setContentView(R.layout.activity_main);
        findViewById(R.id.button).setOnClickListener(listener -> runSyncAdapter());

        copyRcloneConfigFileIfNecessary();
        readSyncMonkeyPropertiesFromFile();

        // Create the dummy account
        dummyAccount = CreateSyncAccount(this);

        ActivityCompat.requestPermissions(this, new String[]{
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.READ_PHONE_STATE},
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
                        initializeSyncAdapter();
                    } else
                    {
                        Log.w(LOG_TAG, "The READ_EXTERNAL_STORAGE Permission was denied.");
                    }
                }
            }
        }
    }

    /**
     * Get the device ID, and place it in the shared preferences.  This is needed to represent the device specific directory on the remote upload server.
     */
    private void initializeDeviceId()
    {
        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        final SharedPreferences.Editor edit = preferences.edit();

        edit.putString(SyncMonkeyConstants.PROPERTY_DEVICE_ID, getDeviceId());
        edit.apply();
    }

    /**
     * Initializes the sync adapter to run at a periodic interval.
     */
    private void initializeSyncAdapter()
    {
        Log.i(LOG_TAG, "Initializing the Sync Monkey Sync Adapter");

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
        {
            Log.e(LOG_TAG, "Can't initialize the sync adapter schedule because we don't have access to read external storage");
            return;
        }

        ContentResolver.requestSync(FileUploadSyncAdapter.generatePeriodicSyncRequest(this));
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
    public static Account CreateSyncAccount(Context context)
    {
        // Create the account type and default account
        Account newAccount = new Account(SyncMonkeyConstants.ACCOUNT, SyncMonkeyConstants.ACCOUNT_TYPE);
        // Get an instance of the Android account manager
        AccountManager accountManager = (AccountManager) context.getSystemService(ACCOUNT_SERVICE);

        /*
         * Add the account and account type, no password or user data
         * If successful, return the Account object, otherwise report an error.
         */
        if (accountManager != null && accountManager.addAccountExplicitly(newAccount, null, null))
        {
            /*
             * If you don't set android:syncable="true" in
             * in your <provider> element in the manifest,
             * then call context.setIsSyncable(account, AUTHORITY, 1)
             * here.
             */
            return newAccount;
        } else
        {
            /*
             * The account exists or some other error occurred. Log this, report it,
             * or handle it internally.
             */
            Log.v(LOG_TAG, "The account already exists, or the account manager could not be found");
        }

        return newAccount;
    }

    /**
     * First, checks to see if the {@link SyncMonkeyConstants#RCLONE_CONFIG_FILE} exists in the app's private storage area.  If it does not, then it is copied
     * from the assets directory.
     */
    private void copyRcloneConfigFileIfNecessary()
    {
        final File rcloneConfigFile = new File(getFilesDir(), SyncMonkeyConstants.RCLONE_CONFIG_FILE);
        if (rcloneConfigFile.exists()) return;

        // The rclone.conf file does not exist, so copy it out of assets.
        try (final InputStream assetRcloneConfigFileInputStream = getAssets().open(SyncMonkeyConstants.RCLONE_CONFIG_FILE);
             final OutputStream privateAppRcloneConfigFileOutputStream = new FileOutputStream(rcloneConfigFile))
        {
            copyInputStreamToOutputStream(assetRcloneConfigFileInputStream, privateAppRcloneConfigFileOutputStream);
        } catch (FileNotFoundException e)
        {
            final String message = "The " + SyncMonkeyConstants.RCLONE_CONFIG_FILE + " file was not found in the app's assets directory";
            Log.e(LOG_TAG, message, e);
            Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
        } catch (IOException e)
        {
            final String message = "Could not create the " + SyncMonkeyConstants.RCLONE_CONFIG_FILE + " file";
            Log.e(LOG_TAG, message, e);
            Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Copies the provide input stream to the provided output stream.
     *
     * @throws IOException If the first byte cannot be read for any reason other than the end of the file, if the input stream has been closed, or if some
     *                     other I/O error occurs.
     */
    private void copyInputStreamToOutputStream(InputStream in, OutputStream out) throws IOException
    {
        final byte[] buffer = new byte[1024];
        int read;
        while ((read = in.read(buffer)) != -1)
        {
            out.write(buffer, 0, read);
        }
    }

    /**
     * Reads the {@link SyncMonkeyConstants#SYNC_MONKEY_PROPERTIES_FILE} and loads the values into the App's Shared Preferences.
     */
    private void readSyncMonkeyPropertiesFromFile()
    {
        final Properties properties = new Properties();
        try (final InputStream propertiesInputStream = getAssets().open(SyncMonkeyConstants.SYNC_MONKEY_PROPERTIES_FILE))
        {
            final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
            final SharedPreferences.Editor edit = preferences.edit();

            properties.load(propertiesInputStream);
            properties.entrySet().forEach(preferenceEntry -> edit.putString((String) preferenceEntry.getKey(), (String) preferenceEntry.getValue()));
            edit.apply();
        } catch (Exception e)
        {
            Log.e(LOG_TAG, "Can't open the Sync Monkey properties file or write a preference to the shared preferences", e);
        }
    }

    /**
     * Attempts to get the device's IMEI if the user has granted the permission.  If not, then a default ID it used.
     *
     * @return The IMEI if it can be found, otherwise the Android ID.
     */
    @SuppressWarnings("ConstantConditions")
    @SuppressLint("HardwareIds")
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
