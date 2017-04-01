package com.bluemaestro.utility.sdk;

import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

/**
 * Created by willem on 12-3-17.
 */

public class TemperatureTable {
    // Database table
    public static final String TABLE_TEMPERATURE = "temperature";
    public static final String COLUMN_ID = "_id";
    public static final String COLUMN_TIMESTAMP = "time_stamp_device";
    public static final String COLUMN_TEMP= "temperature";

    // Database creation SQL statement
    private static final String DATABASE_CREATE = "create table "
            + TABLE_TEMPERATURE
            + "("
            + COLUMN_ID + " integer primary key autoincrement, "
            + COLUMN_TIMESTAMP + " text not null, "
            + COLUMN_TEMP + " real not null"
            + ");";

    public static void onCreate(SQLiteDatabase database) {
        database.execSQL(DATABASE_CREATE);
    }

    public static void onUpgrade(SQLiteDatabase database, int oldVersion,
                                 int newVersion) {
        Log.w(TemperatureTable.class.getName(), "Upgrading database from version "
                + oldVersion + " to " + newVersion
                + ", which will destroy all old data");
        database.execSQL("DROP TABLE IF EXISTS " + TABLE_TEMPERATURE);
        onCreate(database);
    }
}
