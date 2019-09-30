package com.chesapeaketechnology.syncmonkey;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ContentResolver;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity
{
    private static final String LOG_TAG = MainActivity.class.getSimpleName();

    private Account dummyAccount;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Create the dummy account
        dummyAccount = CreateSyncAccount(this);
    }

    /**
     * Respond to a button click by calling requestSync(). This is an
     * asynchronous operation.
     * <p>
     * This method is attached to the refresh button in the layout
     * XML file
     *
     * @param v The View associated with the method call,
     *          in this case a Button
     */
    public void runSyncAdapter(View v)
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
        if (accountManager.addAccountExplicitly(newAccount, null, null))
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
            Log.e(LOG_TAG, "Something went wrong when creating the Sync Adapter Account");
        }

        return null;
    }
}
