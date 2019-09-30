package com.chesapeaketechnology.syncmonkey.fileupload;

import android.accounts.Account;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SyncResult;
import android.os.Bundle;

/**
 * Handle the transfer of data between a server and an
 * app, using the Android sync adapter framework.
 */
public class FileUploadSyncAdapter extends AbstractThreadedSyncAdapter
{
    // Global variables
    // Define a variable to contain a content resolver instance
    ContentResolver contentResolver;

    /**
     * Set up the sync adapter
     */
    public FileUploadSyncAdapter(Context context, boolean autoInitialize)
    {
        super(context, autoInitialize);
        /*
         * If your app uses a content resolver, get an instance of it
         * from the incoming Context
         */
        contentResolver = context.getContentResolver();
    }

    /**
     * Set up the sync adapter. This form of the
     * constructor maintains compatibility with Android 3.0
     * and later platform versions
     */
    public FileUploadSyncAdapter(Context context, boolean autoInitialize, boolean allowParallelSyncs)
    {
        super(context, autoInitialize, allowParallelSyncs);
        /*
         * If your app uses a content resolver, get an instance of it
         * from the incoming Context
         */
        contentResolver = context.getContentResolver();

        // TODO Finish Me
    }

    /*
     * Specify the code you want to run in the sync adapter. The entire
     * sync adapter runs in a background thread, so you don't have to set
     * up your own background processing.
     */
    @Override
    public void onPerformSync(
            Account account,
            Bundle extras,
            String authority,
            ContentProviderClient provider,
            SyncResult syncResult)
    {
        /*
         * Put the data transfer code here.
         */
    }
}