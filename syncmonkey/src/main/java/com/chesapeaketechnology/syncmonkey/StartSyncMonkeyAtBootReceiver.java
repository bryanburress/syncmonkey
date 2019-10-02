package com.chesapeaketechnology.syncmonkey;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

/**
 * Starts the Sync Monkey app when Android is booted.
 */
public class StartSyncMonkeyAtBootReceiver extends BroadcastReceiver
{
    private static final String LOG_TAG = StartSyncMonkeyAtBootReceiver.class.getSimpleName();

    @Override
    public void onReceive(Context context, Intent intent)
    {
        if (null == intent) return;

        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        final boolean autoStartOnBootPreference = preferences.getBoolean(SyncMonkeyConstants.PROPERTY_AUTO_START_ON_BOOT, true);

        if (autoStartOnBootPreference && Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()))
        {
            Log.i(LOG_TAG, "Auto starting the Sync Monkey App");
            final Intent serviceIntent = new Intent(context, SyncMonkeyMainActivity.class);
            serviceIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            serviceIntent.putExtra(SyncMonkeyConstants.START_HEADLESS_FLAG, true);
            context.startActivity(serviceIntent);
        }
    }
}
