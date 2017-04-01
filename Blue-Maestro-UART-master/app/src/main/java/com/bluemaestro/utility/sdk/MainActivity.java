
/*
 * Copyright (c) 2016, Blue Maestro
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to endorse or promote products derived from this
 * software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
 * USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.bluemaestro.utility.sdk;




import java.io.UnsupportedEncodingException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.regex.Pattern;


import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;
import android.preference.PreferenceManager;
import android.content.SharedPreferences;
import android.view.Menu;
import android.view.MenuInflater;

import java.util.UUID;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import android.os.Handler;

import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.bluemaestro.utility.sdk.devices.BMDevice;
import com.bluemaestro.utility.sdk.devices.BMDeviceMap;
import com.bluemaestro.utility.sdk.views.dialogs.BMAlertDialog;
import com.bluemaestro.utility.sdk.views.graphs.BMLineChart;

public class MainActivity extends Activity implements RadioGroup.OnCheckedChangeListener {

    public static final String TAG = "BlueMaestro";

    private static final int REQUEST_SELECT_DEVICE = 1;
    private static final int REQUEST_ENABLE_BT = 2;
    private static final int REQUEST_HISTORY_DEVICE = 3;
    private static final int UART_PROFILE_READY = 10;

    private static final int UART_PROFILE_CONNECTED = 20;
    private static final int UART_PROFILE_DISCONNECTED = 21;
    private static final int STATE_OFF = 10;

    private int mState = UART_PROFILE_DISCONNECTED;

    private UartService mService = null;

    private BluetoothDevice mDevice = null;
    private BMDevice mBMDevice = null;

    private String mPrivateHash = "";

    private BluetoothAdapter mBtAdapter = null;
    private ListView messageListView;
    private ArrayAdapter<String> listAdapter;
    private Button
            btnConnectDisconnect,
            btnSend, btnGraph;
    private EditText edtMessage;
    private BMLineChart lineChart;

    // The authority for the sync adapter's content provider
    public static final String AUTHORITY = "com.bluemaestro.utility.sdk.contentprovider";
    // An account type, in the form of a domain name
    public static final String ACCOUNT_TYPE = "nl.closeness";
    // The account name
    public static final String ACCOUNT = "dummyaccount";
    public static final long SECONDS_PER_MINUTE = 60L;
    public static final long SYNC_INTERVAL_IN_MINUTES = 1L;
    public static final long SYNC_INTERVAL =
            SYNC_INTERVAL_IN_MINUTES *
                    SECONDS_PER_MINUTE;
    // Instance fields
    Account mAccount;
    ContentResolver mResolver;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mResolver = getContentResolver();
        mAccount = CreateSyncAccount(this);
//        ContentResolver.addPeriodicSync(
//                mAccount,
//                AUTHORITY,
//                Bundle.EMPTY,
//                SYNC_INTERVAL);
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);

        // Delete all databases; testing only
        String[] addresses = getApplicationContext().databaseList();
        for(String address : addresses) {
            getApplicationContext().deleteDatabase(address);
        }

        View rootView = findViewById(android.R.id.content).getRootView();
        StyleOverride.setDefaultTextColor(rootView, Color.BLACK);
        StyleOverride.setDefaultFont(rootView, this, "Montserrat-Regular.ttf");

        setContentView(R.layout.main);
        mBtAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBtAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        messageListView = (ListView) findViewById(R.id.listMessage);
        listAdapter = new CustomArrayAdapter<String>(this, R.layout.message_detail);

        messageListView.setAdapter(listAdapter);
        messageListView.setDivider(null);

        btnConnectDisconnect = (Button) findViewById(R.id.btn_select);

        // Initialise UART service
        service_init();
       
        // Handle Disconnect & Connect button
        btnConnectDisconnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {onClickConnectDisconnect();
            }
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy()");

        try {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(UARTStatusChangeReceiver);
        } catch (Exception ignore) {
            Log.e(TAG, ignore.toString());
        }
        unbindService(mServiceConnection);
        if(mService != null){
            mService.stopSelf();
            mService = null;
        }
        BMDeviceMap.INSTANCE.clear();
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
        if (!mBtAdapter.isEnabled()) {
            Log.i(TAG, "onResume - BT not enabled yet");
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.options_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.settings:
                Intent intent = new Intent(this, SettingsActivity.class);
                startActivity(intent);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_SELECT_DEVICE:
                // When the DeviceListActivity return, with the selected device address
                if (resultCode == Activity.RESULT_OK && data != null) {
//                    String deviceAddress = data.getStringExtra(BluetoothDevice.EXTRA_DEVICE);
                    SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
                    String deviceAddress = sharedPref.getString("sensor_name", "");
                    mDevice = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(deviceAddress);
                    mBMDevice = BMDeviceMap.INSTANCE.getBMDevice(mDevice.getAddress());
                    Log.d(TAG, "... onActivityResultdevice.address==" + mDevice + "mserviceValue" + mService);
                    ((TextView) findViewById(R.id.deviceName)).setText(mDevice.getName() + " - connecting");
                    mService.connect(deviceAddress);
                }
                break;
            case REQUEST_ENABLE_BT:
                // When the request to enable Bluetooth returns
                if (resultCode == Activity.RESULT_OK) {
                    Toast.makeText(this, "Bluetooth has turned on ", Toast.LENGTH_SHORT).show();
                } else {
                    // User did not enable Bluetooth or an error occurred
                    Log.d(TAG, "BT not enabled");
                    Toast.makeText(this, "Problem in BT Turning ON ", Toast.LENGTH_SHORT).show();
                    finish();
                }
                break;
            case REQUEST_HISTORY_DEVICE:
                // When the HistoryListActivity return, with the selected device address
                if (resultCode == Activity.RESULT_OK && data != null) {
                    String deviceAddress = data.getStringExtra(BluetoothDevice.EXTRA_DEVICE);
                    btnGraph.setEnabled(true);
                }
                break;
            default:
                Log.e(TAG, "wrong request code");
                break;
        }
    }

    /************************** INITIALISE **************************/

    private void service_init() {
        Intent bindIntent = new Intent(this, UartService.class);
        bindService(bindIntent, mServiceConnection, Context.BIND_AUTO_CREATE);

        LocalBroadcastManager.getInstance(this).registerReceiver(UARTStatusChangeReceiver, makeGattUpdateIntentFilter());
    }

    /************************** UART STATUS CHANGE **************************/

    // UART service connected/disconnected
    private ServiceConnection mServiceConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder rawBinder) {
            mService = ((UartService.LocalBinder) rawBinder).getService();
            Log.d(TAG, "onServiceConnected mService= " + mService);
            if (!mService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
        }

        public void onServiceDisconnected(ComponentName classname) {
            mService = null;
        }
    };

    // Main UART broadcast receiver
    private final BroadcastReceiver UARTStatusChangeReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(UartService.ACTION_GATT_CONNECTED)) {
                onGattConnected();
            } else if (action.equals(UartService.ACTION_GATT_DISCONNECTED)) {
                onGattDisconnected();
            } else if (action.equals(UartService.ACTION_GATT_SERVICES_DISCOVERED)) {
                onGattServicesDiscovered();
            } else if (action.equals(UartService.ACTION_DATA_AVAILABLE)) {
                onDataAvailable(intent.getByteArrayExtra(UartService.EXTRA_DATA));
            } else if (action.equals(UartService.DEVICE_DOES_NOT_SUPPORT_UART)){
                onDeviceDoesNotSupportUART();
            } else{

            }
        }
    };

    private void onGattConnected() {
        runOnUiThread(new Runnable() {
            public void run() {
                String currentDateTimeString = DateFormat.getTimeInstance().format(new Date());
                Log.d(TAG, "UART_CONNECT_MSG");
                btnConnectDisconnect.setText("Disconnect");
                ((TextView) findViewById(R.id.deviceName)).setText(mDevice.getName() + " - ready");
                listAdapter.add("Connected to: " + mDevice.getName());
                listAdapter.notifyDataSetChanged();
                messageListView.smoothScrollToPosition(listAdapter.getCount() - 1);
                mState = UART_PROFILE_CONNECTED;
            }
        });
    }

    private void onGattDisconnected() {
        runOnUiThread(new Runnable() {
            public void run() {
                String currentDateTimeString = DateFormat.getTimeInstance().format(new Date());
                Log.d(TAG, "UART_DISCONNECT_MSG");
                btnConnectDisconnect.setText("Connect");
                ((TextView) findViewById(R.id.deviceName)).setText("Not Connected");
                listAdapter.add("Disconnected from: " + mDevice.getName());
                listAdapter.notifyDataSetChanged();
                mState = UART_PROFILE_DISCONNECTED;
                messageListView.setSelection(listAdapter.getCount() - 1);
                mService.close();
            }
        });
    }

    private void onGattServicesDiscovered(){
        mService.enableTXNotification();
    }

    private void onDataAvailable(final byte[] txValue) {
        runOnUiThread(new Runnable() {
            public void run() {
                try {
                    String text = new String(txValue, "UTF-8").trim();
                    if (!text.equals("Ok")){
                        SimpleDateFormat outputFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");
                        String currentDateTimeString = outputFormat.format(new Date());
                        ContentValues values = new ContentValues();
                        values.put(TemperatureTable.COLUMN_TEMP, Double.valueOf(text.split("H")[0].replace("T", "")));
                        values.put(TemperatureTable.COLUMN_TIMESTAMP, currentDateTimeString);
                        Uri uri = getContentResolver().insert(ClosenessProvider.CONTENT_URI, values);
                    }
                    listAdapter.add(text);
                    listAdapter.notifyDataSetChanged();
                    if (messageListView.getVisibility() == View.VISIBLE) {
                        messageListView.smoothScrollToPosition(listAdapter.getCount() - 1);
                    } else {
                        messageListView.setSelection(listAdapter.getCount() - 1);
                    }

                    if (mBMDevice == null) return;
                    mBMDevice.updateChart(lineChart, text);
                    //mBMDatabase.addData(BMDatabase.TIMESTAMP_NOW(), text);

                } catch (Exception e) {
                    Log.e(TAG, e.toString());
                }
            }
        });
    }

    private void onDeviceDoesNotSupportUART() {
        showMessage("Device doesn't support UART. Disconnecting");
        mService.disconnect();
    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(UartService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(UartService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(UartService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(UartService.ACTION_DATA_AVAILABLE);
        intentFilter.addAction(UartService.DEVICE_DOES_NOT_SUPPORT_UART);
        return intentFilter;
    }

    /************************** BUTTON CLICK HANDLERS **************************/

    private void onClickConnectDisconnect(){
        if (!mBtAdapter.isEnabled()) {
            Log.i(TAG, "onClick - BT not enabled yet");
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        } else {
            if (btnConnectDisconnect.getText().equals("Connect")) {
                // Connect button pressed, open DeviceListActivity class, with popup windows that scan for devices
//                Intent newIntent = new Intent(MainActivity.this, DeviceListActivity.class);
//                startActivityForResult(newIntent, REQUEST_SELECT_DEVICE);
//                edtMessage.setText("");
//                edtMessage.setVisibility(View.VISIBLE);
//                messageListView.smoothScrollToPosition(listAdapter.getCount() - 1);
                SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
                String deviceAddress = sharedPref.getString("sensor_name", "");
                mPrivateHash = sharedPref.getString(getString(R.string.private_hash), "");
                if (mPrivateHash.equals("")){
                    registerDevice();
                } else{
                    Log.d(TAG, "using private hash");
//                    Log.d(TAG, mPrivateHash);
                }
                mDevice = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(deviceAddress);
                mBMDevice = BMDeviceMap.INSTANCE.getBMDevice(mDevice.getAddress());
                Log.d(TAG, "... onActivityResultdevice.address==" + mDevice + "mserviceValue" + mService);
                mService.connect(deviceAddress);
                final Handler handler = new Handler();
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        startLogging();
                    }
                }, 1000);
            } else {
                // Disconnect button pressed
                if (mDevice != null) mService.disconnect();
                mBMDevice = null;
                messageListView.setVisibility(View.VISIBLE);
                messageListView.setSelection(listAdapter.getCount() - 1);
            }
        }
    }

    public static Account CreateSyncAccount(Context context) {
        // Create the account type and default account
        Account newAccount = new Account(
                ACCOUNT, ACCOUNT_TYPE);
        // Get an instance of the Android account manager
        AccountManager accountManager =
                (AccountManager) context.getSystemService(
                        ACCOUNT_SERVICE);
        /*
         * Add the account and account type, no password or user data
         * If successful, return the Account object, otherwise report an error.
         */
        if (!accountManager.addAccountExplicitly(newAccount, null, null)) {
            Log.d(TAG, "Account issue");
        }
        return(newAccount);
    }

    private void registerDevice(){
        final Context context = getApplicationContext();
        final int duration = Toast.LENGTH_SHORT;
        String uniqueID = UUID.randomUUID().toString();
        // Instantiate the RequestQueue.
        RequestQueue queue = Volley.newRequestQueue(this);
        final SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        String url = sharedPref.getString("server_url", "");
        //FIXME API structure hard coded...
        url = url + "/register/" + uniqueID;

        // Request a string response from the provided URL.
        StringRequest stringRequest = new StringRequest(Request.Method.PUT, url,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        Log.d(TAG, response);
                        response = response.replaceAll("\\s$", "");
                        response = response.replaceAll("\"", "");
                        Log.d(TAG, response);
                        Log.d(TAG, Integer.toString(response.length()));
                        if (response.length() == 28){
                            Toast toast = Toast.makeText(context, R.string.registration_impossible, duration);
                            toast.show();
                            Log.d(TAG, "Registration is not possible");
                        } else{
                            if (response.length() == 64){
                                mPrivateHash = response;
                                SharedPreferences.Editor editor = sharedPref.edit();
                                editor.putString(getString(R.string.private_hash), response);
                                editor.commit();

                                Log.d(TAG, response);
                            } else{
                                Toast toast = Toast.makeText(context, R.string.server_error_msg, duration);
                                toast.show();
                                Log.d(TAG, "Server error occured");
                            }
                        }
                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Log.d(TAG, "That didn't work!");
            }
        });
        // Add the request to the RequestQueue.
        queue.add(stringRequest);
    }


    private void startLogging(){
        Log.d(TAG, "test");
        String message = "*bur";
        byte[] value;
        try {
            // Send data to service
            value = message.getBytes("UTF-8");
            if (mState == UART_PROFILE_CONNECTED){
                mService.writeRXCharacteristic(value);
            }
        } catch (UnsupportedEncodingException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    private void showMessage(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onBackPressed() {
        if (mState == UART_PROFILE_CONNECTED) {
            Intent startMain = new Intent(Intent.ACTION_MAIN);
            startMain.addCategory(Intent.CATEGORY_HOME);
            startMain.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(startMain);
            showMessage("Blue Maestro Utility App is running in background. Disconnect to exit");
        }
        else {
            BMAlertDialog dialog = new BMAlertDialog(this,
                    "",
                    "Do you want to quit this Application?");
            dialog.setPositiveButton("YES", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    finish();
                }
            });
            dialog.setNegativeButton("NO", null);

            dialog.show();
            dialog.applyFont(this, "Montserrat-Regular.ttf");
        }
    }

    @Override
    public void onCheckedChanged(RadioGroup group, int checkedId) {

    }

    private class CustomArrayAdapter<T> extends ArrayAdapter<T>{
        private final Pattern pattern = Pattern.compile("\\*.*");

        public CustomArrayAdapter(Context context, int resource) {
            super(context, resource);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            TextView textView = (TextView) super.getView(position, convertView, parent);

            CharSequence charSequence = textView.getText();
            if(charSequence == null) return textView;
            int color = getResources().getColor(
                    pattern.matcher(charSequence).matches()
                            ? R.color.bm_command_color
                            : R.color.bm_black);
            textView.setTextColor(color);
            return textView;
        }
    }
}
