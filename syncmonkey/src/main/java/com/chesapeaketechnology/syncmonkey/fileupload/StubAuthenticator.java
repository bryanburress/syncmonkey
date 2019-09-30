package com.chesapeaketechnology.syncmonkey.fileupload;

import android.accounts.AbstractAccountAuthenticator;
import android.accounts.Account;
import android.accounts.AccountAuthenticatorResponse;
import android.accounts.NetworkErrorException;
import android.content.Context;
import android.os.Bundle;

/**
 * Handles the authentication with the NextCloud server for the Sync Adapter used to upload the survey files.
 *
 * @since 0.0.1
 */
public class StubAuthenticator extends AbstractAccountAuthenticator
{
    // Simple constructor
    public StubAuthenticator(Context context)
    {
        super(context);
    }

    // Editing properties is not supported
    @Override
    public Bundle editProperties(
            AccountAuthenticatorResponse r, String s)
    {
        throw new UnsupportedOperationException();
    }

    // Don't add additional accounts
    @Override
    public Bundle addAccount(AccountAuthenticatorResponse response, String accountType, String authenticationTokenType, String[] supportedFeatures,
                             Bundle authenticatorOptionsBundle) throws NetworkErrorException
    {
        return null;
    }

    // Ignore attempts to confirm credentials
    @Override
    public Bundle confirmCredentials(AccountAuthenticatorResponse response, Account account, Bundle authenticatorOptionsBundle) throws NetworkErrorException
    {
        return null;
    }

    // Getting an authentication token is not supported
    @Override
    public Bundle getAuthToken(AccountAuthenticatorResponse response, Account account, String authenticationTokenType, Bundle authenticatorOptionsBundle) throws NetworkErrorException
    {
        throw new UnsupportedOperationException();
    }

    // Getting a label for the auth token is not supported
    @Override
    public String getAuthTokenLabel(String authenticationTokenType)
    {
        throw new UnsupportedOperationException();
    }

    // Updating user credentials is not supported
    @Override
    public Bundle updateCredentials(AccountAuthenticatorResponse response, Account account, String authenticationTokenType, Bundle authenticatorOptionsBundle) throws NetworkErrorException
    {
        throw new UnsupportedOperationException();
    }

    // Checking features for the account is not supported
    @Override
    public Bundle hasFeatures(
            AccountAuthenticatorResponse response,
            Account account, String[] strings) throws NetworkErrorException
    {
        throw new UnsupportedOperationException();
    }
}
