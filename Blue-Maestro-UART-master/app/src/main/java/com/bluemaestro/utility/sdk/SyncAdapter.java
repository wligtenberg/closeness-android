package com.bluemaestro.utility.sdk;

import android.accounts.Account;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SyncResult;
import android.database.Cursor;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

import com.android.volley.AuthFailureError;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.RetryPolicy;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by willem on 13-3-17.
 */

public class SyncAdapter extends AbstractThreadedSyncAdapter {
    public static final String TAG = "BlueMaestro";
    ContentResolver mContentResolver;
    Context mContext;

    public SyncAdapter(Context context, boolean autoInitialize) {
        super(context, autoInitialize);
        mContentResolver = context.getContentResolver();
        mContext = context;
    }

    public SyncAdapter(
            Context context,
            boolean autoInitialize,
            boolean allowParallelSyncs) {
        super(context, autoInitialize, allowParallelSyncs);
        mContentResolver = context.getContentResolver();
        mContext = context;
    }

    @Override
    public void onPerformSync(Account account, Bundle bundle, String s, ContentProviderClient contentProviderClient, SyncResult syncResult) {
//        Read from ContentProvider
        Log.d(TAG, "syncing!");
        String[] projection = {TemperatureTable.COLUMN_ID, TemperatureTable.COLUMN_TIMESTAMP, TemperatureTable.COLUMN_TEMP};
        String selection = TemperatureTable.COLUMN_ID + ">?";
        String[] selectionArgs = {"0"};
        String sortOrder = "";
        Cursor mCursor = mContentResolver.query(ClosenessProvider.CONTENT_URI, projection, selection, selectionArgs, sortOrder);
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(mContext);
        String client_hash = sharedPref.getString(mContext.getString(R.string.private_hash), "");
        String studyNumber = sharedPref.getString("study_number", "0");
        String participantNumber = sharedPref.getString("participant_number", "0");
        String dropIdClause = "_ID IN (";
        JSONArray jsonArray = new JSONArray();
        try {
            while (mCursor.moveToNext()) {
                dropIdClause = dropIdClause + String.valueOf(
                        mCursor.getInt(mCursor.getColumnIndexOrThrow(TemperatureTable.COLUMN_ID))) + ", ";
                JSONObject item = new JSONObject();
                item.put("client_hash", client_hash);
                item.put("participant_number", Integer.valueOf(participantNumber));
                item.put("study_number", Integer.valueOf(studyNumber));
                String timestamp = mCursor.getString(mCursor.getColumnIndexOrThrow(TemperatureTable.COLUMN_TIMESTAMP));
                item.put("time_stamp_device", timestamp);
                float temp = mCursor.getFloat(mCursor.getColumnIndexOrThrow(TemperatureTable.COLUMN_TEMP));
                item.put("temperature", temp);
                jsonArray.put(item);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        } finally {
            mCursor.close();
        }

        dropIdClause = dropIdClause.replaceAll(", $", "");
        dropIdClause = dropIdClause + ")";
        final String dropIdClause2 = dropIdClause;
        final String jsonContent = jsonArray.toString();
        if (!jsonContent.equals("[]")) {

            RequestQueue queue = Volley.newRequestQueue(mContext);
            String url = sharedPref.getString("server_url", "");
            //FIXME API structure hard coded...
            url = url + "/temperature";

            // Request a string response from the provided URL.
            StringRequest stringRequest = new StringRequest(Request.Method.PUT, url,
                    new Response.Listener<String>() {
                        @Override
                        public void onResponse(String response) {
                            response = response.replaceAll("\\s$", "");
                            response = response.replaceAll("\"", "");
                            Log.d(TAG, response);
                            if (response.length() == 2){
                                Log.d(TAG, "All fine, proceed with deletion");
                                int rowsDeleted = mContentResolver.delete(ClosenessProvider.CONTENT_URI, dropIdClause2, null);
                                Log.d(TAG, String.valueOf(rowsDeleted));
                            } else{
                                Log.d(TAG, "Something wrong");
                            }
                        }
                    }, new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    Log.d(TAG, error.toString());
                    Log.d(TAG, "That didn't work!");
                }
            }) {
                @Override
                public String getBodyContentType() {
                    return "application/x-www-form-urlencoded; charset=UTF-8";
                }

                @Override
                protected Map<String, String> getParams() throws AuthFailureError {
                    Map<String, String> params = new HashMap<String, String>();
                    params.put("data", jsonContent);
                    return params;
                }
            };
            // Add the request to the RequestQueue.
            stringRequest.setRetryPolicy(new RetryPolicy() {
                @Override
                public int getCurrentTimeout() {
                    // Here goes the new timeout
                    return 30000;
                }
                @Override
                public int getCurrentRetryCount() {
                    // The max number of attempts
                    return 1;
                }
                @Override
                public void retry(VolleyError error) throws VolleyError {
                    // Here you could check if the retry count has gotten
                    // To the max number, and if so, send a VolleyError msg
                    // or something
                }
            });
            queue.add(stringRequest);
        }
    }
}
