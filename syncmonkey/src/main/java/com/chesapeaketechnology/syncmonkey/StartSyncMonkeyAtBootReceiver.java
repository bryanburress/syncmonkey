package com.chesapeaketechnology.syncmonkey;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import com.chesapeaketechnology.syncmonkey.fileupload.FileUploadSyncAdapter;

/**
 * Starts the Sync Monkey Sync Adapter when Android is booted.
 */
public class StartSyncMonkeyAtBootReceiver extends BroadcastReceiver
{
    private static final String LOG_TAG = StartSyncMonkeyAtBootReceiver.class.getSimpleName();

    @Override
    public void onReceive(Context context, Intent intent)
    {
        if (null == intent) return;

        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;

        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        final boolean autoStartOnBootPreference = preferences.getBoolean(SyncMonkeyConstants.PROPERTY_AUTO_START_ON_BOOT_KEY, true);

        if (Log.isLoggable(LOG_TAG, Log.INFO)) Log.i(LOG_TAG, "Auto Start at Boot Preference: " + autoStartOnBootPreference);

        if (autoStartOnBootPreference)
        {
            Log.i(LOG_TAG, "Auto starting the Sync Monkey Sync Adapter");

            SyncMonkeyMainActivity.readSyncMonkeyProperties(context); // The properties need to be read before installing the rclone config file
            SyncMonkeyMainActivity.readSyncMonkeyManagedConfiguration(context);
            SyncMonkeyMainActivity.installRcloneConfigFile(context);

            ContentResolver.requestSync(FileUploadSyncAdapter.generatePeriodicSyncRequest(context));

            // Register a listener for Managed Configuration changes.
            SyncMonkeyMainActivity.registerManagedConfigurationListener(context);
        }
    }
}
