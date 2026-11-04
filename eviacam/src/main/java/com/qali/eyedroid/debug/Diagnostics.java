/*
 * EyeDroid
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.qali.eyedroid.debug;

import android.content.Context;
import android.os.Build;

import com.qali.eyedroid.BuildConfig;
import com.qali.eyedroid.common.Engine;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Runtime status of the whole pipeline plus the last failures seen.
 *
 * Every subsystem reports here what it is doing, so the debug screen can show what is
 * really happening instead of guessing from log lines, and the cursor self-diagnostics
 * can name the actual broken link of the chain.
 */
public final class Diagnostics {

    private static final String CRASH_FILE = "eyedroid_last_uncaught_exception.txt";

    private static final Diagnostics sInstance = new Diagnostics();

    public static Diagnostics get() {
        return sInstance;
    }

    /* Service */
    private boolean mServiceConnected;

    /* Engine */
    private boolean mEngineInitialized;
    private int mEngineState = Engine.STATE_DISABLED;

    /* Camera */
    private boolean mCameraInitialized;
    private boolean mCameraRunning;
    private long mCameraFrameCount;
    private long mLastCameraFrame;

    /* MediaPipe */
    private boolean mMediaPipeReady;
    private long mLastMediaPipeDetection;

    /* Overlay and pointer */
    private boolean mOverlayAttached;
    private boolean mOverlayVisible;
    private String mOverlayAttachError;
    private int mOverlayWindowType;
    private boolean mPointerLayerAttached;
    private boolean mPointerLayerVisible;
    private long mPointerUpdateCount;
    private long mLastPointerUpdate;

    /* Face detection */
    private long mLastFaceDetection;

    /* Learning */
    private boolean mLearningEnabled;
    private boolean mLearningDatabaseReady;

    /* Last failures */
    private String mLastInitFailure;
    private String mLastCameraFailure;
    private String mLastOverlayFailure;
    private String mLastMediaPipeFailure;

    /* Engine retries, used to avoid restart loops */
    private int mEngineRetryCount;
    private long mLastEngineRetry;

    private Diagnostics() {}

    public synchronized void setServiceConnected(boolean connected) {
        mServiceConnected = connected;
        if (!connected) resetRuntimeStatus();
    }

    public synchronized boolean isServiceConnected() {
        return mServiceConnected;
    }

    public synchronized void setEngineInitialized(boolean initialized) {
        mEngineInitialized = initialized;
    }

    public synchronized boolean isEngineInitialized() {
        return mEngineInitialized;
    }

    public synchronized void setEngineState(int state) {
        mEngineState = state;
    }

    public synchronized int getEngineState() {
        return mEngineState;
    }

    public synchronized void setCameraInitialized(boolean initialized) {
        mCameraInitialized = initialized;
    }

    public synchronized boolean isCameraInitialized() {
        return mCameraInitialized;
    }

    public synchronized void setCameraRunning(boolean running) {
        mCameraRunning = running;
        if (running) mCameraFrameCount = 0;
    }

    public synchronized boolean isCameraRunning() {
        return mCameraRunning;
    }

    public synchronized void noteCameraFrame() {
        mCameraFrameCount++;
        mLastCameraFrame = System.currentTimeMillis();
    }

    public synchronized long getCameraFrameCount() {
        return mCameraFrameCount;
    }

    public synchronized long getLastCameraFrame() {
        return mLastCameraFrame;
    }

    /* whether the face landmarker is running on the GPU */
    private boolean mMediaPipeGpu;

    public synchronized void setMediaPipeReady(boolean ready) {
        mMediaPipeReady = ready;
    }

    public synchronized boolean isMediaPipeReady() {
        return mMediaPipeReady;
    }

    public synchronized void setMediaPipeGpu(boolean gpu) {
        mMediaPipeGpu = gpu;
    }

    public synchronized boolean isMediaPipeGpu() {
        return mMediaPipeGpu;
    }

    public synchronized void noteMediaPipeDetection() {
        mLastMediaPipeDetection = System.currentTimeMillis();
    }

    public synchronized long getLastMediaPipeDetection() {
        return mLastMediaPipeDetection;
    }

    public synchronized void setOverlayAttached(boolean attached, int windowType) {
        mOverlayAttached = attached;
        mOverlayWindowType = windowType;
        if (!attached) mOverlayVisible = false;
    }

    public synchronized boolean isOverlayAttached() {
        return mOverlayAttached;
    }

    public synchronized int getOverlayWindowType() {
        return mOverlayWindowType;
    }

    public synchronized String getOverlayAttachError() {
        return mOverlayAttachError;
    }

    public synchronized void setOverlayAttachError(String error) {
        mOverlayAttachError = error;
        if (error != null) setLastOverlayFailure(error);
    }

    public synchronized void setOverlayVisible(boolean visible) {
        mOverlayVisible = visible;
    }

    public synchronized boolean isOverlayVisible() {
        return mOverlayVisible;
    }

    public synchronized void setPointerLayerAttached(boolean attached) {
        mPointerLayerAttached = attached;
        if (attached) {
            mPointerLayerVisible = false;
            mPointerUpdateCount = 0;
            mLastPointerUpdate = 0;
        }
    }

    public synchronized boolean isPointerLayerAttached() {
        return mPointerLayerAttached;
    }

    public synchronized void setPointerLayerVisible(boolean visible) {
        mPointerLayerVisible = visible;
    }

    public synchronized boolean isPointerLayerVisible() {
        return mPointerLayerVisible;
    }

    public synchronized void notePointerUpdate() {
        mPointerUpdateCount++;
        mLastPointerUpdate = System.currentTimeMillis();
    }

    public synchronized long getPointerUpdateCount() {
        return mPointerUpdateCount;
    }

    public synchronized long getLastPointerUpdate() {
        return mLastPointerUpdate;
    }

    public synchronized void noteFaceDetected() {
        mLastFaceDetection = System.currentTimeMillis();
    }

    public synchronized long getLastFaceDetection() {
        return mLastFaceDetection;
    }

    public synchronized void setLearningEnabled(boolean enabled) {
        mLearningEnabled = enabled;
    }

    public synchronized boolean isLearningEnabled() {
        return mLearningEnabled;
    }

    public synchronized void setLearningDatabaseReady(boolean ready) {
        mLearningDatabaseReady = ready;
    }

    public synchronized boolean isLearningDatabaseReady() {
        return mLearningDatabaseReady;
    }

    public synchronized void setLastInitFailure(String failure) {
        mLastInitFailure = failure;
    }

    public synchronized String getLastInitFailure() {
        return mLastInitFailure;
    }

    public synchronized void setLastCameraFailure(String failure) {
        mLastCameraFailure = failure;
    }

    public synchronized String getLastCameraFailure() {
        return mLastCameraFailure;
    }

    public synchronized void setLastOverlayFailure(String failure) {
        mLastOverlayFailure = failure;
    }

    public synchronized String getLastOverlayFailure() {
        return mLastOverlayFailure;
    }

    public synchronized void setLastMediaPipeFailure(String failure) {
        mLastMediaPipeFailure = failure;
    }

    public synchronized String getLastMediaPipeFailure() {
        return mLastMediaPipeFailure;
    }

    public synchronized void noteEngineRetry() {
        mEngineRetryCount++;
        mLastEngineRetry = System.currentTimeMillis();
    }

    public synchronized int getEngineRetryCount() {
        return mEngineRetryCount;
    }

    public synchronized long getLastEngineRetry() {
        return mLastEngineRetry;
    }

    /**
     * Clear everything that describes the current run, keeping the recorded failures
     * so that they remain visible after the service is stopped.
     */
    public synchronized void resetRuntimeStatus() {
        mEngineInitialized = false;
        mEngineState = Engine.STATE_DISABLED;
        mCameraInitialized = false;
        mCameraRunning = false;
        mCameraFrameCount = 0;
        mLastCameraFrame = 0;
        mMediaPipeReady = false;
        mLastMediaPipeDetection = 0;
        mOverlayAttached = false;
        mOverlayVisible = false;
        mPointerLayerAttached = false;
        mPointerLayerVisible = false;
        mPointerUpdateCount = 0;
        mLastPointerUpdate = 0;
        mLastFaceDetection = 0;
    }

    public static String engineStateName(int state) {
        switch (state) {
            case Engine.STATE_DISABLED: return "disabled";
            case Engine.STATE_STOPPED: return "stopped";
            case Engine.STATE_RUNNING: return "running";
            case Engine.STATE_PAUSED: return "paused";
            case Engine.STATE_STANDBY: return "standby";
            default: return "unknown (" + state + ")";
        }
    }

    /**
     * Cursor self-diagnostics: name the link of the chain which is broken right now.
     *
     * @return a human readable description of the detected problem or
     *         {@link #NO_PROBLEM} when everything needed for the cursor is healthy
     */
    public static final String NO_PROBLEM = "No problem detected";

    public synchronized String describeProblem() {
        final long now = System.currentTimeMillis();

        if (!mServiceConnected) {
            return "Accessibility service is not connected";
        }

        if (!mEngineInitialized) {
            StringBuilder sb = new StringBuilder(
                    "Accessibility service connected but the engine is not running");
            if (mLastInitFailure != null) sb.append(" (last failure: ").append(mLastInitFailure).append(')');
            return sb.toString();
        }

        if (mEngineState == Engine.STATE_STOPPED) {
            return "Engine initialized but stopped, so the cursor is not shown";
        }

        if (mEngineState == Engine.STATE_STANDBY) {
            return "Engine in standby because no face was detected";
        }

        if (mEngineState != Engine.STATE_RUNNING) {
            return "Engine not running (state: " + engineStateName(mEngineState) + ")";
        }

        if (!mCameraInitialized) {
            StringBuilder sb = new StringBuilder("Engine running but the camera is unavailable");
            if (mLastCameraFailure != null) sb.append(" (").append(mLastCameraFailure).append(')');
            return sb.toString();
        }

        if (!mCameraRunning) {
            return "Engine running but the camera is not delivering frames";
        }

        if (mLastCameraFrame > 0 && now - mLastCameraFrame > 5000) {
            return "Camera stopped delivering frames "
                    + seconds(now - mLastCameraFrame) + "s ago";
        }

        if (mLastFaceDetection > 0 && now - mLastFaceDetection > 10000) {
            return "Camera running but no face detected for "
                    + seconds(now - mLastFaceDetection) + "s";
        }

        if (!mOverlayAttached) {
            StringBuilder sb = new StringBuilder("Overlay window not attached");
            if (mOverlayAttachError != null) sb.append(": ").append(mOverlayAttachError);
            return sb.toString();
        }

        if (!mOverlayVisible) {
            return "Overlay attached but invisible";
        }

        if (!mPointerLayerAttached) {
            return "Pointer layer not attached to the overlay";
        }

        if (!mPointerLayerVisible) {
            return "Pointer layer disabled";
        }

        if (mLastPointerUpdate <= 0) {
            return "Pointer layer visible but not receiving movement updates yet";
        }

        if (now - mLastPointerUpdate > 5000) {
            return "Pointer layer visible but not receiving movement updates for "
                    + seconds(now - mLastPointerUpdate) + "s";
        }

        return NO_PROBLEM;
    }

    public synchronized boolean hasProblem() {
        return !NO_PROBLEM.equals(describeProblem());
    }

    /**
     * Text report shown by the debug screen
     */
    public synchronized String snapshot(Context c) {
        final long now = System.currentTimeMillis();
        final StringBuilder sb = new StringBuilder();

        sb.append("EyeDroid ").append(BuildConfig.VERSION_NAME)
                .append(" (").append(BuildConfig.BUILD_TYPE).append(")\n");
        sb.append("Device: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
                .append(", Android ").append(Build.VERSION.RELEASE)
                .append(" (API ").append(Build.VERSION.SDK_INT).append(")\n\n");

        sb.append("ACCESSIBILITY SERVICE\n");
        sb.append("  Connected: ").append(mServiceConnected ? "yes" : "no").append('\n');

        sb.append("\nENGINE\n");
        sb.append("  Initialized: ").append(mEngineInitialized ? "yes" : "no").append('\n');
        sb.append("  State: ").append(engineStateName(mEngineState)).append('\n');
        sb.append("  Retries requested: ").append(mEngineRetryCount);
        if (mLastEngineRetry > 0) sb.append(" (last ").append(ago(now, mLastEngineRetry)).append(')');
        sb.append('\n');

        sb.append("\nCAMERA\n");
        sb.append("  Initialized: ").append(mCameraInitialized ? "yes" : "no").append('\n');
        sb.append("  Running: ").append(mCameraRunning ? "yes" : "no").append('\n');
        sb.append("  Frames received: ").append(mCameraFrameCount);
        if (mLastCameraFrame > 0) sb.append(" (last ").append(ago(now, mLastCameraFrame)).append(')');
        sb.append('\n');

        sb.append("\nMEDIAPIPE\n");
        sb.append("  Ready: ").append(mMediaPipeReady ? "yes" : "no").append('\n');
        sb.append("  Delegate: ").append(mMediaPipeGpu ? "GPU" : "CPU").append('\n');
        if (mLastMediaPipeDetection > 0) {
            sb.append("  Last detection: ").append(ago(now, mLastMediaPipeDetection)).append('\n');
        }

        sb.append("\nOVERLAY\n");
        sb.append("  Attached: ").append(mOverlayAttached ? "yes" : "no").append('\n');
        sb.append("  Visible: ").append(mOverlayVisible ? "yes" : "no").append('\n');
        sb.append("  Window type: ").append(windowTypeName(mOverlayWindowType)).append('\n');
        if (mOverlayAttachError != null) {
            sb.append("  Attach error: ").append(mOverlayAttachError).append('\n');
        }

        sb.append("\nPOINTER\n");
        sb.append("  Layer attached: ").append(mPointerLayerAttached ? "yes" : "no").append('\n');
        sb.append("  Layer visible: ").append(mPointerLayerVisible ? "yes" : "no").append('\n');
        sb.append("  Movement updates: ").append(mPointerUpdateCount);
        if (mLastPointerUpdate > 0) sb.append(" (last ").append(ago(now, mLastPointerUpdate)).append(')');
        sb.append('\n');

        sb.append("\nFACE\n");
        if (mLastFaceDetection > 0) {
            sb.append("  Last detected: ").append(ago(now, mLastFaceDetection)).append('\n');
        }
        else {
            sb.append("  Last detected: never\n");
        }

        sb.append("\nLEARNING\n");
        sb.append("  Enabled: ").append(mLearningEnabled ? "yes" : "no").append('\n');
        sb.append("  Database ready: ").append(mLearningDatabaseReady ? "yes" : "no").append('\n');

        sb.append("\nDETECTED PROBLEM\n  ").append(describeProblem()).append('\n');

        sb.append("\nLAST FAILURES\n");
        sb.append("  Initialization: ").append(valueOrNone(mLastInitFailure)).append('\n');
        sb.append("  Camera: ").append(valueOrNone(mLastCameraFailure)).append('\n');
        sb.append("  Overlay: ").append(valueOrNone(mLastOverlayFailure)).append('\n');
        sb.append("  MediaPipe: ").append(valueOrNone(mLastMediaPipeFailure)).append('\n');
        sb.append("  Uncaught exception: ").append(valueOrNone(readLastUncaughtException(c)))
                .append('\n');

        return sb.toString();
    }

    private static String valueOrNone(String value) {
        return value != null && value.length() > 0 ? value : "none";
    }

    private static String seconds(long millis) {
        return String.valueOf(millis / 1000);
    }

    private static String ago(long now, long timestamp) {
        return seconds(Math.max(0, now - timestamp)) + "s ago";
    }

    private static String windowTypeName(int type) {
        if (type == android.view.WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY) {
            return "TYPE_ACCESSIBILITY_OVERLAY";
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                type == android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY) {
            return "TYPE_APPLICATION_OVERLAY";
        }
        if (type == android.view.WindowManager.LayoutParams.TYPE_PHONE) return "TYPE_PHONE";
        if (type == android.view.WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY) {
            return "TYPE_SYSTEM_OVERLAY";
        }
        return String.valueOf(type);
    }

    /**
     * Persist the last uncaught exception so that the debug screen can show it after
     * the process has been restarted.
     */
    public static synchronized void recordUncaughtException(Context c, Throwable error) {
        if (c == null || error == null) return;

        final StringBuilder sb = new StringBuilder();
        synchronized (TIME_FORMAT_LOCK) {
            sb.append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                    .format(new Date())).append('\n');
        }
        sb.append(error.getClass().getName());
        if (error.getMessage() != null) sb.append(": ").append(error.getMessage());
        sb.append('\n');
        for (StackTraceElement element : error.getStackTrace()) {
            sb.append("    at ").append(element.toString()).append('\n');
        }

        OutputStreamWriter writer = null;
        try {
            File file = new File(c.getFilesDir(), CRASH_FILE);
            FileOutputStream stream = new FileOutputStream(file, false);
            writer = new OutputStreamWriter(stream, "UTF-8");
            writer.write(sb.toString());
            writer.flush();
        }
        catch (Throwable ignored) {
            /* never fail while handling a crash */
        }
        finally {
            if (writer != null) {
                try {
                    writer.close();
                }
                catch (Throwable ignored) { /* nothing to do */ }
            }
        }
    }

    /**
     * @return the last uncaught exception recorded by a previous run or null
     */
    public static synchronized String readLastUncaughtException(Context c) {
        if (c == null) return null;

        File file = new File(c.getFilesDir(), CRASH_FILE);
        if (!file.exists()) return null;

        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
            String first = reader.readLine();
            String second = reader.readLine();
            if (first == null) return null;
            StringBuilder sb = new StringBuilder(first);
            if (second != null) sb.append(" - ").append(second);
            return sb.toString();
        }
        catch (Throwable ignored) {
            return null;
        }
        finally {
            if (reader != null) {
                try {
                    reader.close();
                }
                catch (Throwable ignored) { /* nothing to do */ }
            }
        }
    }

    public static synchronized void clearLastUncaughtException(Context c) {
        if (c == null) return;
        //noinspection ResultOfMethodCallIgnored
        new File(c.getFilesDir(), CRASH_FILE).delete();
    }

    private static final Object TIME_FORMAT_LOCK = new Object();
}
