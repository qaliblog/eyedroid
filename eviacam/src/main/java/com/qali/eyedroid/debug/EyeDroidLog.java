/*
 * EyeDroid
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.qali.eyedroid.debug;

import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Central application log.
 *
 * Every subsystem logs through this class using one of the EyeDroid-* tags. Besides
 * writing to logcat the entries are kept in a small in-memory ring buffer which feeds
 * the live viewer of the debug screen. There is no dependency on the facial tracking
 * engine, so it can be used from services, activities and background threads alike.
 *
 * Never log passwords, screen text or personal information through this class.
 */
public final class EyeDroidLog {

    /* Structured tags, one per subsystem */
    public static final String TAG_SERVICE = "EyeDroid-Service";
    public static final String TAG_ENGINE = "EyeDroid-Engine";
    public static final String TAG_CAMERA = "EyeDroid-Camera";
    public static final String TAG_MEDIAPIPE = "EyeDroid-MediaPipe";
    public static final String TAG_OVERLAY = "EyeDroid-Overlay";
    public static final String TAG_CURSOR = "EyeDroid-Cursor";
    public static final String TAG_LEARNING = "EyeDroid-Learning";
    public static final String TAG_DEBUG = "EyeDroid-Debug";

    /* Levels, they match the priorities used by android.util.Log */
    public static final int LEVEL_VERBOSE = Log.VERBOSE;
    public static final int LEVEL_DEBUG = Log.DEBUG;
    public static final int LEVEL_INFO = Log.INFO;
    public static final int LEVEL_WARN = Log.WARN;
    public static final int LEVEL_ERROR = Log.ERROR;

    /* Maximum number of entries kept in memory */
    private static final int MAX_ENTRIES = 1500;

    /* Listener to be notified when a new entry is added (called from any thread) */
    public interface Listener {
        void onLogEntry(Entry entry);
    }

    /**
     * One log line
     */
    public static final class Entry {
        public final long timestamp;
        public final int level;
        public final String tag;
        public final String message;

        Entry(long timestamp, int level, String tag, String message) {
            this.timestamp = timestamp;
            this.level = level;
            this.tag = tag;
            this.message = message;
        }

        public String levelName() {
            switch (level) {
                case LEVEL_VERBOSE: return "VERBOSE";
                case LEVEL_DEBUG: return "DEBUG";
                case LEVEL_INFO: return "INFO";
                case LEVEL_WARN: return "WARN";
                case LEVEL_ERROR: return "ERROR";
                default: return "?";
            }
        }

        public String time() {
            synchronized (TIME_FORMAT) {
                return TIME_FORMAT.format(new Date(timestamp));
            }
        }

        public String toLine() {
            return time() + " " + levelName() + " " + tag + ": " + message;
        }
    }

    private static final SimpleDateFormat TIME_FORMAT =
            new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);

    private static final ArrayDeque<Entry> sEntries = new ArrayDeque<>();
    private static final CopyOnWriteArrayList<Listener> sListeners =
            new CopyOnWriteArrayList<>();

    private EyeDroidLog() {}

    public static void v(String tag, String message) {
        log(LEVEL_VERBOSE, tag, message, null);
    }

    public static void d(String tag, String message) {
        log(LEVEL_DEBUG, tag, message, null);
    }

    public static void i(String tag, String message) {
        log(LEVEL_INFO, tag, message, null);
    }

    public static void w(String tag, String message) {
        log(LEVEL_WARN, tag, message, null);
    }

    public static void w(String tag, String message, Throwable error) {
        log(LEVEL_WARN, tag, message + (error != null ? ": " + error : ""), error);
    }

    public static void e(String tag, String message) {
        log(LEVEL_ERROR, tag, message, null);
    }

    public static void e(String tag, String message, Throwable error) {
        log(LEVEL_ERROR, tag, message + (error != null ? ": " + error : ""), error);
    }

    private static void log(int level, String tag, String message, Throwable error) {
        final String safeTag = tag != null ? tag : TAG_DEBUG;
        final String safeMessage = message != null ? message : "";

        try {
            switch (level) {
                case LEVEL_VERBOSE: Log.v(safeTag, safeMessage, error); break;
                case LEVEL_DEBUG: Log.d(safeTag, safeMessage, error); break;
                case LEVEL_INFO: Log.i(safeTag, safeMessage, error); break;
                case LEVEL_WARN: Log.w(safeTag, safeMessage, error); break;
                default: Log.e(safeTag, safeMessage, error); break;
            }
        }
        catch (Throwable ignored) {
            /* logging must never break the caller */
        }

        final Entry entry = new Entry(System.currentTimeMillis(), level, safeTag, safeMessage);

        synchronized (sEntries) {
            sEntries.addLast(entry);
            while (sEntries.size() > MAX_ENTRIES) sEntries.removeFirst();
        }

        for (Listener listener : sListeners) {
            try {
                listener.onLogEntry(entry);
            }
            catch (Throwable ignored) {
                /* a broken listener must not break logging */
            }
        }
    }

    /**
     * @return a copy of the current entries, oldest first
     */
    public static List<Entry> snapshot() {
        synchronized (sEntries) {
            return new ArrayList<>(sEntries);
        }
    }

    /**
     * Remove every entry kept in memory. Entries previously written to logcat remain there.
     */
    public static void clear() {
        synchronized (sEntries) {
            sEntries.clear();
        }
    }

    public static void addListener(Listener listener) {
        if (listener != null) sListeners.addIfAbsent(listener);
    }

    public static void removeListener(Listener listener) {
        if (listener != null) sListeners.remove(listener);
    }
}
