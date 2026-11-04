/*
 * EyeDroid
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.qali.eyedroid.learning;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.qali.eyedroid.debug.EyeDroidLog;

import java.util.List;

/**
 * Local SQLite storage of the learning subsystem.
 *
 * Everything is written in transactions, duplicates are collapsed through unique
 * constraints (observing the same interaction again only increments its success count)
 * and every query relies on an index. The database only contains learned navigation
 * data: it never holds credentials or screen contents beyond short element labels.
 */
public class LearningDatabase extends SQLiteOpenHelper {

    private static final String TAG = EyeDroidLog.TAG_LEARNING;

    public static final String DB_NAME = "eyedroid_learning.db";
    public static final int DB_VERSION = 1;

    private static final String TABLE_APPLICATIONS = "applications";
    private static final String TABLE_SCREENS = "screens";
    private static final String TABLE_ELEMENTS = "screen_elements";
    private static final String TABLE_INTERACTIONS = "interactions";
    private static final String TABLE_NAVIGATION = "navigation_history";

    public LearningDatabase(Context context) {
        super(context.getApplicationContext(), DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        try {
            db.setForeignKeyConstraintsEnabled(true);
        }
        catch (Throwable ignored) {
            /* not supported by very old SQLite builds */
        }
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        EyeDroidLog.i(TAG, "Creating learning database v" + DB_VERSION);

        db.execSQL("CREATE TABLE " + TABLE_APPLICATIONS + " ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "package_name TEXT NOT NULL UNIQUE,"
                + "first_seen INTEGER NOT NULL,"
                + "last_seen INTEGER NOT NULL)");

        db.execSQL("CREATE TABLE " + TABLE_SCREENS + " ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "application_id INTEGER NOT NULL,"
                + "signature TEXT NOT NULL,"
                + "activity_name TEXT,"
                + "first_seen INTEGER NOT NULL,"
                + "last_seen INTEGER NOT NULL,"
                + "visit_count INTEGER NOT NULL DEFAULT 1,"
                + "UNIQUE(application_id, signature),"
                + "FOREIGN KEY(application_id) REFERENCES " + TABLE_APPLICATIONS
                + "(id) ON DELETE CASCADE)");

        db.execSQL("CREATE TABLE " + TABLE_ELEMENTS + " ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "screen_id INTEGER NOT NULL,"
                + "stable_key TEXT NOT NULL,"
                + "class_name TEXT,"
                + "view_id TEXT,"
                + "text TEXT,"
                + "content_description TEXT,"
                + "left INTEGER NOT NULL,"
                + "top INTEGER NOT NULL,"
                + "right INTEGER NOT NULL,"
                + "bottom INTEGER NOT NULL,"
                + "clickable INTEGER NOT NULL,"
                + "scrollable INTEGER NOT NULL,"
                + "UNIQUE(screen_id, stable_key),"
                + "FOREIGN KEY(screen_id) REFERENCES " + TABLE_SCREENS
                + "(id) ON DELETE CASCADE)");

        db.execSQL("CREATE TABLE " + TABLE_INTERACTIONS + " ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "application_id INTEGER NOT NULL,"
                + "from_screen_id INTEGER NOT NULL,"
                + "to_screen_id INTEGER NOT NULL,"
                + "element_key TEXT NOT NULL,"
                + "interaction_type TEXT NOT NULL,"
                + "class_name TEXT,"
                + "view_id TEXT,"
                + "text TEXT,"
                + "content_description TEXT,"
                + "left INTEGER NOT NULL,"
                + "top INTEGER NOT NULL,"
                + "right INTEGER NOT NULL,"
                + "bottom INTEGER NOT NULL,"
                + "timestamp INTEGER NOT NULL,"
                + "success_count INTEGER NOT NULL DEFAULT 1,"
                + "UNIQUE(application_id, from_screen_id, to_screen_id, element_key, "
                + "interaction_type),"
                + "FOREIGN KEY(application_id) REFERENCES " + TABLE_APPLICATIONS
                + "(id) ON DELETE CASCADE)");

        db.execSQL("CREATE TABLE " + TABLE_NAVIGATION + " ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "application_id INTEGER NOT NULL,"
                + "from_screen_id INTEGER NOT NULL,"
                + "to_screen_id INTEGER NOT NULL,"
                + "interaction_id INTEGER,"
                + "timestamp INTEGER NOT NULL,"
                + "FOREIGN KEY(application_id) REFERENCES " + TABLE_APPLICATIONS
                + "(id) ON DELETE CASCADE)");

        db.execSQL("CREATE INDEX idx_screens_signature ON " + TABLE_SCREENS + "(signature)");
        db.execSQL("CREATE INDEX idx_elements_screen ON " + TABLE_ELEMENTS + "(screen_id)");
        db.execSQL("CREATE INDEX idx_interactions_application ON " + TABLE_INTERACTIONS
                + "(application_id, timestamp)");
        db.execSQL("CREATE INDEX idx_interactions_from ON " + TABLE_INTERACTIONS
                + "(from_screen_id)");
        db.execSQL("CREATE INDEX idx_navigation_application ON " + TABLE_NAVIGATION
                + "(application_id, timestamp)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        EyeDroidLog.i(TAG, "Upgrading learning database from v" + oldVersion + " to v" + newVersion);

        /* Each future version adds its own migration step; existing data is preserved */
        for (int version = oldVersion + 1; version <= newVersion; version++) {
            switch (version) {
                default:
                    /* nothing to do yet */
                    break;
            }
        }
    }

    @Override
    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        /*
         * Learned data is fully rebuildable, so a downgrade simply starts from a clean
         * schema instead of leaving an incompatible database behind.
         */
        EyeDroidLog.w(TAG, "Downgrading learning database, learned data will be rebuilt");
        dropAll(db);
        onCreate(db);
    }

    private void dropAll(SQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAVIGATION);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_INTERACTIONS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_ELEMENTS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_SCREENS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_APPLICATIONS);
    }

    public long getOrCreateApplication(String packageName) {
        final SQLiteDatabase db = getWritableDatabase();
        final long now = System.currentTimeMillis();

        db.beginTransaction();
        try {
            long id = DatabaseUtils.longForQuery(db,
                    "SELECT id FROM " + TABLE_APPLICATIONS + " WHERE package_name = ?",
                    new String[]{packageName});

            if (id <= 0) {
                ContentValues values = new ContentValues();
                values.put("package_name", packageName);
                values.put("first_seen", now);
                values.put("last_seen", now);
                id = db.insertOrThrow(TABLE_APPLICATIONS, null, values);
            }
            else {
                ContentValues values = new ContentValues();
                values.put("last_seen", now);
                db.update(TABLE_APPLICATIONS, values, "id = ?", new String[]{String.valueOf(id)});
            }

            db.setTransactionSuccessful();
            return id;
        }
        finally {
            db.endTransaction();
        }
    }

    public long getOrCreateScreen(long applicationId, String signature, String activityName) {
        final SQLiteDatabase db = getWritableDatabase();
        final long now = System.currentTimeMillis();

        db.beginTransaction();
        try {
            long id = DatabaseUtils.longForQuery(db,
                    "SELECT id FROM " + TABLE_SCREENS
                            + " WHERE application_id = ? AND signature = ?",
                    new String[]{String.valueOf(applicationId), signature});

            if (id <= 0) {
                ContentValues values = new ContentValues();
                values.put("application_id", applicationId);
                values.put("signature", signature);
                values.put("activity_name", activityName);
                values.put("first_seen", now);
                values.put("last_seen", now);
                values.put("visit_count", 1);
                id = db.insertOrThrow(TABLE_SCREENS, null, values);
            }
            else {
                db.execSQL("UPDATE " + TABLE_SCREENS
                                + " SET last_seen = ?, visit_count = visit_count + 1 WHERE id = ?",
                        new Object[]{now, id});
            }

            db.setTransactionSuccessful();
            return id;
        }
        finally {
            db.endTransaction();
        }
    }

    public void upsertElements(long screenId, List<LearnedElement> elements) {
        if (elements == null || elements.isEmpty()) return;

        final SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            for (LearnedElement element : elements) {
                if (element == null) continue;

                ContentValues values = new ContentValues();
                values.put("screen_id", screenId);
                values.put("stable_key", element.stableKey());
                values.put("class_name", element.className);
                values.put("view_id", element.viewId);
                values.put("text", element.text);
                values.put("content_description", element.contentDescription);
                values.put("left", element.left);
                values.put("top", element.top);
                values.put("right", element.right);
                values.put("bottom", element.bottom);
                values.put("clickable", element.clickable ? 1 : 0);
                values.put("scrollable", element.scrollable ? 1 : 0);

                db.insertWithOnConflict(TABLE_ELEMENTS, null, values,
                        SQLiteDatabase.CONFLICT_REPLACE);
            }

            db.setTransactionSuccessful();
        }
        finally {
            db.endTransaction();
        }
    }

    /**
     * Store an interaction. Repeating the same interaction only increments its success
     * count, so the table describes what has been observed to work.
     *
     * @return id of the stored row
     */
    public long recordInteraction(LearnedInteraction interaction) {
        if (interaction == null) return 0;

        final SQLiteDatabase db = getWritableDatabase();
        final String[] whereArgs = new String[]{
                String.valueOf(interaction.applicationId),
                String.valueOf(interaction.fromScreenId),
                String.valueOf(interaction.toScreenId),
                interaction.elementKey,
                interaction.interactionType
        };

        db.beginTransaction();
        try {
            ContentValues update = new ContentValues();
            update.put("timestamp", interaction.timestamp);
            update.put("class_name", interaction.className);
            update.put("view_id", interaction.viewId);
            update.put("text", interaction.text);
            update.put("content_description", interaction.contentDescription);
            update.put("left", interaction.bounds.left);
            update.put("top", interaction.bounds.top);
            update.put("right", interaction.bounds.right);
            update.put("bottom", interaction.bounds.bottom);

            int updated = db.update(TABLE_INTERACTIONS, update,
                    "application_id = ? AND from_screen_id = ? AND to_screen_id = ?"
                            + " AND element_key = ? AND interaction_type = ?",
                    whereArgs);

            long id;
            if (updated == 0) {
                ContentValues values = new ContentValues(update);
                values.put("application_id", interaction.applicationId);
                values.put("from_screen_id", interaction.fromScreenId);
                values.put("to_screen_id", interaction.toScreenId);
                values.put("element_key", interaction.elementKey);
                values.put("interaction_type", interaction.interactionType);
                values.put("timestamp", interaction.timestamp);
                values.put("success_count", interaction.successCount);
                id = db.insertOrThrow(TABLE_INTERACTIONS, null, values);
            }
            else {
                db.execSQL("UPDATE " + TABLE_INTERACTIONS
                                + " SET success_count = success_count + 1"
                                + " WHERE application_id = ? AND from_screen_id = ?"
                                + " AND to_screen_id = ? AND element_key = ?"
                                + " AND interaction_type = ?",
                        new Object[]{interaction.applicationId, interaction.fromScreenId,
                                interaction.toScreenId, interaction.elementKey,
                                interaction.interactionType});

                id = DatabaseUtils.longForQuery(db,
                        "SELECT id FROM " + TABLE_INTERACTIONS
                                + " WHERE application_id = ? AND from_screen_id = ?"
                                + " AND to_screen_id = ? AND element_key = ?"
                                + " AND interaction_type = ?",
                        whereArgs);
            }

            db.setTransactionSuccessful();
            interaction.id = id;
            return id;
        }
        finally {
            db.endTransaction();
        }
    }

    public void recordNavigation(long applicationId, long fromScreenId, long toScreenId,
                                 long interactionId, long timestamp) {
        final SQLiteDatabase db = getWritableDatabase();

        db.beginTransaction();
        try {
            ContentValues values = new ContentValues();
            values.put("application_id", applicationId);
            values.put("from_screen_id", fromScreenId);
            values.put("to_screen_id", toScreenId);
            values.put("interaction_id", interactionId);
            values.put("timestamp", timestamp);
            db.insertOrThrow(TABLE_NAVIGATION, null, values);
            db.setTransactionSuccessful();
        }
        finally {
            db.endTransaction();
        }
    }

    public int getApplicationCount() {
        return count(TABLE_APPLICATIONS, null, null);
    }

    public int getScreenCount() {
        return count(TABLE_SCREENS, null, null);
    }

    public int getInteractionCount() {
        return count(TABLE_INTERACTIONS, null, null);
    }

    public int getNavigationCount() {
        return count(TABLE_NAVIGATION, null, null);
    }

    private int count(String table, String where, String[] args) {
        Cursor cursor = null;
        try {
            cursor = getReadableDatabase().query(table, new String[]{"COUNT(*)"},
                    where, args, null, null, null);
            if (cursor != null && cursor.moveToFirst()) return cursor.getInt(0);
            return 0;
        }
        catch (Throwable error) {
            EyeDroidLog.e(TAG, "Cannot count rows of " + table, error);
            return 0;
        }
        finally {
            if (cursor != null) {
                try {
                    cursor.close();
                }
                catch (Throwable ignored) { /* nothing to do */ }
            }
        }
    }

    /**
     * Delete every learned record but keep the database itself, the learning setting and
     * all normal EyeDroid preferences untouched.
     *
     * @return true when the reset completed
     */
    public boolean reset() {
        final SQLiteDatabase db = getWritableDatabase();

        db.beginTransaction();
        try {
            db.delete(TABLE_NAVIGATION, null, null);
            db.delete(TABLE_INTERACTIONS, null, null);
            db.delete(TABLE_ELEMENTS, null, null);
            db.delete(TABLE_SCREENS, null, null);
            db.delete(TABLE_APPLICATIONS, null, null);

            try {
                db.delete("sqlite_sequence", "name IN (?, ?, ?, ?, ?)", new String[]{
                        TABLE_NAVIGATION, TABLE_INTERACTIONS, TABLE_ELEMENTS,
                        TABLE_SCREENS, TABLE_APPLICATIONS});
            }
            catch (Throwable ignored) {
                /* sqlite_sequence only exists once an autoincrement row was written */
            }

            db.setTransactionSuccessful();
            EyeDroidLog.i(TAG, "Learned data deleted");
            return true;
        }
        catch (Throwable error) {
            EyeDroidLog.e(TAG, "Cannot delete learned data", error);
            return false;
        }
        finally {
            db.endTransaction();
        }
    }
}
