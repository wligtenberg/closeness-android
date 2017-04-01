package com.bluemaestro.utility.sdk;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

/**
 * Created by willem on 12-3-17.
 */

public class ClosenessAuthenticatorService extends Service {
    // Instance field that stores the authenticator object
    private ClosenessAuthenticator mAuthenticator;
    @Override
    public void onCreate() {
        // Create a new authenticator object
        mAuthenticator = new ClosenessAuthenticator(this);
    }
    /*
     * When the system binds to this Service to make the RPC call
     * return the authenticator's IBinder.
     */
    @Override
    public IBinder onBind(Intent intent) {
        return mAuthenticator.getIBinder();
    }
}
