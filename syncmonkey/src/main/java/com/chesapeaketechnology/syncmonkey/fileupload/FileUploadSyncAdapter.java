package com.chesapeaketechnology.syncmonkey.fileupload;

import android.accounts.Account;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SyncRequest;
import android.content.SyncResult;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.util.Log;

import com.chesapeaketechnology.syncmonkey.SyncMonkeyConstants;
import com.chesapeaketechnology.syncmonkey.SyncMonkeyMainActivity;
import com.chesapeaketechnology.syncmonkey.fileupload.Items.RemoteItem;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * Handle the transfer of data between a server and an
 * app, using the Android sync adapter framework.
 */
public class FileUploadSyncAdapter extends AbstractThreadedSyncAdapter
{
    private static final String LOG_TAG = FileUploadSyncAdapter.class.getSimpleName();

    private final Rclone rclone;
    private final String dataDirectoryPath;

    /**
     * Set up the sync adapter
     */
    FileUploadSyncAdapter(Context context, boolean autoInitialize)
    {
        this(context, autoInitialize, false);
    }

    /**
     * Set up the sync adapter. This form of the
     * constructor maintains compatibility with Android 3.0
     * and later platform versions
     */
    private FileUploadSyncAdapter(Context context, boolean autoInitialize, boolean allowParallelSyncs)
    {
        super(context, autoInitialize, allowParallelSyncs);

        rclone = new Rclone(context);
        dataDirectoryPath = Environment.getExternalStorageDirectory().getPath() + "/";
    }

    /**
     * Specify the code you want to run in the sync adapter. The entire
     * sync adapter runs in a background thread, so you don't have to set
     * up your own background processing.
     */
    @Override
    public void onPerformSync(Account account, Bundle extras, String authority, ContentProviderClient provider, SyncResult syncResult)
    {
        try
        {
            Log.i(LOG_TAG, "Running the SyncMonkey Sync Adapter");

            final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getContext());
            final boolean transmitOnlyOnVPN = preferences.getBoolean(SyncMonkeyConstants.PROPERTY_VPN_ONLY_KEY, true);

            if (transmitOnlyOnVPN)
            {
                if (isVpnEnabled()) uploadFile();
            } else
            {
                uploadFile();
            }
        } catch (Exception e)
        {
            Log.e(LOG_TAG, "Caught an exception when trying to perform a sync", e);
        }
    }

    /**
     * Check if the Android device is currently attached to a VPN.
     *
     * @return If the device is connected to a VPN return true.
     */
    private boolean isVpnEnabled()
    {
        final ConnectivityManager connectivityManager = (ConnectivityManager) getContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager == null) return false;

        final Network[] networks = connectivityManager.getAllNetworks();

        boolean vpnEnabled = false;

        Log.i(LOG_TAG, "Network count: " + networks.length);
        for (int i = 0; i < networks.length; i++)
        {
            final NetworkCapabilities caps = connectivityManager.getNetworkCapabilities(networks[i]);
            if (caps == null) continue;

            if (Log.isLoggable(LOG_TAG, Log.INFO)) Log.i(LOG_TAG, "Network " + i + ": " + networks[i].toString());
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) && !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN))
            {
                vpnEnabled = true;
            }

            if (Log.isLoggable(LOG_TAG, Log.INFO)) Log.i(LOG_TAG, "VPN transport?: " + vpnEnabled);
        }

        return vpnEnabled;
    }

    /**
     * Generates a {@link SyncRequest} that can be used to schedule sync updates.
     *
     * @param context The context to use when creating the Sync {@link Account}.
     * @return A {@link SyncRequest} that can be submitted to schedule a periodic sync.
     */
    public static SyncRequest generatePeriodicSyncRequest(Context context)
    {
        final Account dummyAccount = SyncMonkeyMainActivity.createSyncAccount(context);

        return new SyncRequest.Builder()
                .setSyncAdapter(dummyAccount, SyncMonkeyConstants.AUTHORITY)
                .syncPeriodic(2 * SyncMonkeyConstants.SECONDS_IN_HOUR, 2 * SyncMonkeyConstants.SECONDS_IN_HOUR)
                .setExtras(new Bundle()) // I think there is a bug in Android that makes setting this empty Bundle a requirement
                .build();
    }

    /**
     * Pull the upload preferences from the PreferenceManager, and then upload any files that are not already present on the remote server.
     */
    private void uploadFile()
    {
        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getContext());

        final String configName = preferences.getString(SyncMonkeyConstants.PROPERTY_CONFIG_NAME_KEY, null);
        final String containerName = preferences.getString(SyncMonkeyConstants.PROPERTY_CONTAINER_NAME_KEY, null);
        final String remoteType = preferences.getString(SyncMonkeyConstants.PROPERTY_REMOTE_TYPE_KEY, null);
        final String localSyncDirectories = preferences.getString(SyncMonkeyConstants.PROPERTY_LOCAL_SYNC_DIRECTORIES_KEY, null);
        final String deviceId = preferences.getString(SyncMonkeyConstants.PROPERTY_DEVICE_ID_KEY, SyncMonkeyConstants.DEFAULT_DEVICE_ID);

        if (configName == null || containerName == null || remoteType == null || localSyncDirectories == null)
        {
            Log.e(LOG_TAG, "Could not upload any files because the remoteName, remoteType, or localSyncDirectories was null");
            return;
        }

        final RemoteItem remote = new RemoteItem(configName + SyncMonkeyConstants.COLON_SEPARATOR + containerName, remoteType);

        for (String relativeSyncDirectory : localSyncDirectories.split(SyncMonkeyConstants.COLON_SEPARATOR))
        {
            final String localSyncDirectory = dataDirectoryPath + relativeSyncDirectory;

            if (Log.isLoggable(LOG_TAG, Log.INFO)) Log.i(LOG_TAG, "Syncing the directory: " + localSyncDirectory);

            Process currentProcess = rclone.uploadFile(remote, "/" + deviceId, localSyncDirectory);

            if (currentProcess != null)
            {
                try
                {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(currentProcess.getErrorStream()));
                    String line;
                    //String notificationContent = "";
                    //String[] notificationBigText = new String[5];
                    while ((line = reader.readLine()) != null)
                    {
                        Log.d(LOG_TAG, line);

                        // This code might be useful to show toasts with specific transfer information
                        /*if (line.startsWith("Transferred:") && !line.matches("Transferred:\\s+\\d+\\s+/\\s+\\d+,\\s+\\d+%$"))
                        {
                            String s = line.substring(12).trim();
                            notificationBigText[0] = s;
                            notificationContent = s;
                        } else if (line.startsWith(" *"))
                        {
                            String s = line.substring(2).trim();
                            notificationBigText[1] = s;
                        } else if (line.startsWith("Errors:"))
                        {
                            notificationBigText[2] = line;
                        } else if (line.startsWith("Checks:"))
                        {
                            notificationBigText[3] = line;
                        } else if (line.matches("Transferred:\\s+\\d+\\s+/\\s+\\d+,\\s+\\d+%$"))
                        {
                            notificationBigText[4] = line;
                        } else if (isLoggingEnable && line.startsWith("ERROR :"))
                        {
                            log2File.log(line);
                        }*/
                    }
                } catch (IOException e)
                {
                    Log.e(LOG_TAG, "Caught an exception when trying to read an error from the upload process", e);
                }

                try
                {
                    currentProcess.waitFor();
                } catch (InterruptedException e)
                {
                    Log.e(LOG_TAG, "Caught an exception when waiting for the rclone upload process to finish", e);
                }
            }

            boolean result = currentProcess != null && currentProcess.exitValue() == 0;
            Log.i(LOG_TAG, "rclone upload result=" + result);
        }
    }
}