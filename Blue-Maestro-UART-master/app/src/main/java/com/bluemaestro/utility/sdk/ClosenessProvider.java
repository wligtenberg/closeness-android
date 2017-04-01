package com.bluemaestro.utility.sdk;

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import java.util.Arrays;
import java.util.HashSet;

/**
 * Created by willem on 12-3-17.
 */

public class ClosenessProvider extends ContentProvider {
    public static final String TAG = "BlueMaestro";

    private TemperatureDatabaseHelper mOpenHelper;

    // used for the UriMacher
    private static final int TEMPS = 10;
    private static final int TEMP_ID = 20;

    private static final String AUTHORITY = "com.bluemaestro.utility.sdk.contentprovider";

    private static final String BASE_PATH = "temperature";
    public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY
            + "/" + BASE_PATH);

    public static final String CONTENT_TYPE = ContentResolver.CURSOR_DIR_BASE_TYPE
            + "/temperatures";
    public static final String CONTENT_ITEM_TYPE = ContentResolver.CURSOR_ITEM_BASE_TYPE
            + "/temperature";

    private static final UriMatcher sURIMatcher = new UriMatcher(UriMatcher.NO_MATCH);
    static {
        sURIMatcher.addURI(AUTHORITY, BASE_PATH, TEMPS);
        sURIMatcher.addURI(AUTHORITY, BASE_PATH + "/#", TEMP_ID);
    }

    @Override
    public boolean onCreate() {
        /*
         * Creates a new helper object. This method always returns quickly.
         * Notice that the database itself isn't created or opened
         * until SQLiteOpenHelper.getWritableDatabase is called
         */
        mOpenHelper = new TemperatureDatabaseHelper(getContext());
        return false;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        // Uisng SQLiteQueryBuilder instead of query() method
        SQLiteQueryBuilder queryBuilder = new SQLiteQueryBuilder();

        // check if the caller has requested a column which does not exists
        checkColumns(projection);

        // Set the table
        queryBuilder.setTables(TemperatureTable.TABLE_TEMPERATURE);

        int uriType = sURIMatcher.match(uri);
        switch (uriType) {
            case TEMPS:
                break;
            case TEMP_ID:
                // adding the ID to the original query
                queryBuilder.appendWhere(TemperatureTable.COLUMN_ID + "="
                        + uri.getLastPathSegment());
                break;
            default:
                throw new IllegalArgumentException("Unknown URI: " + uri);
        }

        SQLiteDatabase db = mOpenHelper.getReadableDatabase();
        Cursor cursor = queryBuilder.query(db, projection, selection,
                selectionArgs, null, null, sortOrder);
        // make sure that potential listeners are getting notified
        cursor.setNotificationUri(getContext().getContentResolver(), uri);

        return cursor;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues contentValues) {
        int uriType = sURIMatcher.match(uri);
        SQLiteDatabase sqlDB = mOpenHelper.getWritableDatabase();
        long id = 0;
        switch (uriType) {
            case TEMPS:
                id = sqlDB.insert(TemperatureTable.TABLE_TEMPERATURE, null, contentValues);
                break;
            default:
                throw new IllegalArgumentException("Unknown URI: " + uri);
        }
        getContext().getContentResolver().notifyChange(uri, null);
        return Uri.parse(BASE_PATH + "/" + id);
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        int uriType = sURIMatcher.match(uri);
        SQLiteDatabase sqlDB = mOpenHelper.getWritableDatabase();
        int rowsDeleted = 0;
        switch (uriType) {
            case TEMPS:
                rowsDeleted = sqlDB.delete(TemperatureTable.TABLE_TEMPERATURE, selection,
                        selectionArgs);
                break;
            case TEMP_ID:
                String id = uri.getLastPathSegment();
                if (TextUtils.isEmpty(selection)) {
                    rowsDeleted = sqlDB.delete(
                            TemperatureTable.TABLE_TEMPERATURE,
                            TemperatureTable.COLUMN_ID + "=" + id,
                            null);
                } else {
                    rowsDeleted = sqlDB.delete(
                            TemperatureTable.TABLE_TEMPERATURE,
                            TemperatureTable.COLUMN_ID + "=" + id
                                    + " and " + selection,
                            selectionArgs);
                }
                break;
            default:
                throw new IllegalArgumentException("Unknown URI: " + uri);
        }
        getContext().getContentResolver().notifyChange(uri, null);
        return rowsDeleted;
    }

    @Override
    public int update(Uri uri, ContentValues contentValues, String s, String[] strings) {
        return 0;
    }
    private void checkColumns(String[] projection) {
        String[] available = { TemperatureTable.COLUMN_TIMESTAMP,
                TemperatureTable.COLUMN_TEMP, TemperatureTable.COLUMN_ID };
        if (projection != null) {
            HashSet<String> requestedColumns = new HashSet<String>(
                    Arrays.asList(projection));
            HashSet<String> availableColumns = new HashSet<String>(
                    Arrays.asList(available));
            // check if all columns which are requested are available
            if (!availableColumns.containsAll(requestedColumns)) {
                throw new IllegalArgumentException(
                        "Unknown columns in projection");
            }
        }
    }
}
