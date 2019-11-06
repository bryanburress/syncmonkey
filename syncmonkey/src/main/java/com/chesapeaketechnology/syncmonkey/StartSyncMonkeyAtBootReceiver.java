package com.chesapeaketechnology.syncmonkey;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.chesapeaketechnology.syncmonkey.fileupload.FileUploadSyncAdapter;

import net.grandcentrix.tray.AppPreferences;

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

        final AppPreferences appPreferences = new AppPreferences(context);
        final boolean autoSyncPreference = appPreferences.getBoolean(SyncMonkeyConstants.PROPERTY_AUTO_SYNC_KEY, true);

        if (Log.isLoggable(LOG_TAG, Log.INFO)) Log.i(LOG_TAG, "Auto Sync Preference: " + autoSyncPreference);

        if (autoSyncPreference)
        {
            Log.i(LOG_TAG, "Auto starting the Sync Monkey Sync Adapter");
            final Context applicationContext = context.getApplicationContext();

            SyncMonkeyMainActivity.readSyncMonkeyProperties(applicationContext, appPreferences); // The properties need to be read before installing the rclone config file
            SyncMonkeyMainActivity.readSyncMonkeyManagedConfiguration(applicationContext, appPreferences);
            SyncMonkeyMainActivity.installRcloneConfigFile(applicationContext, appPreferences);

            FileUploadSyncAdapter.addPeriodicSync(applicationContext);

            // Register a listener for Managed Configuration changes.
            SyncMonkeyMainActivity.registerManagedConfigurationListener(applicationContext, appPreferences);
        }
    }
}
