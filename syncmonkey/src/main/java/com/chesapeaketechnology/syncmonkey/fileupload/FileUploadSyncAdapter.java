package com.chesapeaketechnology.syncmonkey.fileupload;

import android.accounts.Account;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SyncResult;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.util.Log;

import com.chesapeaketechnology.syncmonkey.Log2File;
import com.chesapeaketechnology.syncmonkey.SyncMonkeyConstants;
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

    // Global variables
    // Define a variable to contain a content resolver instance
    private final ContentResolver contentResolver;
    private final Rclone rclone;
    private final Log2File log2File;
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
        /*
         * If your app uses a content resolver, get an instance of it
         * from the incoming Context
         */
        contentResolver = context.getContentResolver();

        rclone = new Rclone(context);
        log2File = new Log2File(context);
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
            uploadFile();
        } catch (Exception e)
        {
            Log.e(LOG_TAG, "Caught an exception when trying to perform a sync", e);
        }
    }

    private void uploadFile()
    {
        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getContext());

        final String remoteName = preferences.getString(SyncMonkeyConstants.PROPERTY_REMOTE_NAME, null);
        final String remoteType = preferences.getString(SyncMonkeyConstants.PROPERTY_REMOTE_TYPE, null);
        final String localSyncDirectories = preferences.getString(SyncMonkeyConstants.PROPERTY_LOCAL_SYNC_DIRECTORIES, null);
        final String deviceId = preferences.getString(SyncMonkeyConstants.PROPERTY_DEVICE_ID, SyncMonkeyConstants.DEFAULT_DEVICE_ID);

        if (remoteName == null || remoteType == null || localSyncDirectories == null)
        {
            Log.e(LOG_TAG, "Could not upload any files because the remoteName, remoteType, or localSyncDirectories was null");
            return;
        }

        final RemoteItem remote = new RemoteItem(remoteName, remoteType);

        for (String relativeSyncDirectory : localSyncDirectories.split(":"))
        {
            final String localSyncDirectory = dataDirectoryPath + relativeSyncDirectory;

            Log.i(LOG_TAG, "Syncing the directory: " + localSyncDirectory);

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

                        // TODO Do something with this code
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

                        // TODO updateNotification(uploadFileName, notificationContent, notificationBigText);
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
        // TODO onUploadFinished(remote.getName(), uploadPath, uploadFilePath, result);
    }
}