/*
 * EyeDroid
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.qali.eyedroid.learning;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.preference.PreferenceManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import com.qali.eyedroid.common.Preferences;
import com.qali.eyedroid.debug.Diagnostics;
import com.qali.eyedroid.debug.EyeDroidLog;

/**
 * Entry point of the on-device learning subsystem.
 *
 * It is completely independent of the facial tracking engine: it only listens to the
 * accessibility events already delivered to the service, describes the screens the user
 * visits and remembers which interaction led to which screen. Everything is local, no
 * language model, no cloud service and no network access are involved.
 *
 * All accessibility work happens on a dedicated background thread, so the pointer is
 * never slowed down by the screen analysis.
 */
public class LearningManager implements SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String TAG = EyeDroidLog.TAG_LEARNING;

    /* Minimum time between two screen analyses, to absorb event storms */
    private static final long MIN_ANALYSIS_INTERVAL_MS = 700;

    /* Content changes are debounced so that a list refreshing does not flood us */
    private static final long SCREEN_DEBOUNCE_MS = 450;

    /* Time given to the application to show the screen produced by a click */
    private static final long INTERACTION_OBSERVE_DELAY_MS = 750;

    /* Do not repeat the "no active window" warning more often than this */
    private static final long NO_WINDOW_LOG_INTERVAL_MS = 10000;

    private static final LearningManager sInstance = new LearningManager();

    public static LearningManager get() {
        return sInstance;
    }

    private final Object mLock = new Object();

    private final ScreenAnalyzer mAnalyzer = new ScreenAnalyzer();
    private final NavigationMemory mNavigationMemory = new NavigationMemory();
    private final InteractionRecorder mRecorder = new InteractionRecorder();
    private final AccessibilityObserver mObserver = new AccessibilityObserver();

    private Context mContext;
    private AccessibilityService mService;
    private HandlerThread mThread;
    private Handler mHandler;
    private LearningDatabase mDatabase;
    private boolean mPreferenceListenerRegistered;

    private volatile boolean mEnabled;
    private long mLastAnalysis;
    private long mLastNoWindowLog;

    private final Runnable mAnalyzeRunnable = new Runnable() {
        @Override
        public void run() {
            analyzeActiveScreen();
        }
    };

    private final Runnable mOpenDatabaseRunnable = new Runnable() {
        @Override
        public void run() {
            openDatabase();
        }
    };

    private LearningManager() {}

    /**
     * Keep a context around so that the settings and debug screens can query the learned
     * data even when the accessibility service is not connected.
     */
    public void attach(Context context) {
        if (context == null) return;

        synchronized (mLock) {
            if (mContext == null) mContext = context.getApplicationContext();
        }
    }

    /**
     * Called when the accessibility service becomes available.
     */
    public void onServiceConnected(AccessibilityService service) {
        attach(service);

        synchronized (mLock) {
            mService = service;
        }

        mEnabled = readEnabledPreference();
        Diagnostics.get().setLearningEnabled(mEnabled);

        registerPreferenceListener();

        if (mEnabled) {
            EyeDroidLog.i(TAG, "Learning enabled, observing accessibility events");
            startCollecting();
        }
        else {
            EyeDroidLog.i(TAG, "Learning is disabled in settings, nothing will be recorded");
        }
    }

    /**
     * Called when the accessibility service goes away.
     */
    public void onServiceDisconnected() {
        synchronized (mLock) {
            mService = null;
            if (mHandler != null) {
                mHandler.removeCallbacks(mAnalyzeRunnable);
                mHandler.removeCallbacks(mOpenDatabaseRunnable);
            }
            stopWorkerLocked();
        }

        unregisterPreferenceListener();
        Diagnostics.get().setLearningDatabaseReady(false);
        EyeDroidLog.i(TAG, "Learning stopped with the accessibility service");
    }

    public boolean isEnabled() {
        return mEnabled;
    }

    /**
     * Called for every accessibility event delivered to the service.
     */
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (!mEnabled) return;

        final AccessibilityService service;
        final Handler handler;
        synchronized (mLock) {
            service = mService;
            handler = mHandler;
        }

        if (service == null || handler == null) return;

        if (!mObserver.shouldObserve(event, getOwnPackage())) return;

        final AccessibilityEvent copy = AccessibilityEvent.obtain(event);
        try {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        handleEvent(copy);
                    }
                    finally {
                        copy.recycle();
                    }
                }
            });
        }
        catch (Throwable error) {
            copy.recycle();
            EyeDroidLog.w(TAG, "Cannot schedule accessibility event", error);
        }
    }

    /**
     * Number of distinct screens learned so far.
     */
    public int getLearnedScreenCount() {
        LearningDatabase database = currentDatabase();
        if (database != null) return database.getScreenCount();

        Context context = getContext();
        if (context == null) return 0;

        LearningDatabase temporary = new LearningDatabase(context);
        try {
            return temporary.getScreenCount();
        }
        catch (Throwable error) {
            EyeDroidLog.e(TAG, "Cannot read the learned screen count", error);
            return 0;
        }
        finally {
            temporary.close();
        }
    }

    /**
     * Number of recorded interactions so far.
     */
    public int getRecordedInteractionCount() {
        LearningDatabase database = currentDatabase();
        if (database != null) return database.getInteractionCount();

        Context context = getContext();
        if (context == null) return 0;

        LearningDatabase temporary = new LearningDatabase(context);
        try {
            return temporary.getInteractionCount();
        }
        catch (Throwable error) {
            EyeDroidLog.e(TAG, "Cannot read the interaction count", error);
            return 0;
        }
        finally {
            temporary.close();
        }
    }

    /**
     * Delete every learned record. The learning preference is not modified.
     *
     * @return true when the data has been deleted
     */
    public boolean resetLearnedData() {
        boolean success = false;

        LearningDatabase database = currentDatabase();
        if (database != null) {
            success = database.reset();
        }
        else {
            Context context = getContext();
            if (context != null) {
                LearningDatabase temporary = new LearningDatabase(context);
                try {
                    success = temporary.reset();
                }
                catch (Throwable error) {
                    EyeDroidLog.e(TAG, "Cannot reset the learned data", error);
                }
                finally {
                    temporary.close();
                }
            }
        }

        mNavigationMemory.clear();
        mRecorder.clear();

        if (success) EyeDroidLog.i(TAG, "Learned data reset requested by the user");
        else EyeDroidLog.e(TAG, "Learned data could not be reset");

        return success;
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (!Preferences.KEY_LEARNING_ENABLED.equals(key)) return;

        final boolean enabled = sharedPreferences.getBoolean(Preferences.KEY_LEARNING_ENABLED, false);
        if (enabled == mEnabled) return;

        mEnabled = enabled;
        Diagnostics.get().setLearningEnabled(enabled);

        if (enabled) {
            EyeDroidLog.i(TAG, "Learning enabled by the user");
            startCollecting();
        }
        else {
            EyeDroidLog.i(TAG, "Learning disabled by the user, previously learned data is kept");
            Handler handler;
            synchronized (mLock) {
                handler = mHandler;
            }
            if (handler != null) handler.removeCallbacks(mAnalyzeRunnable);
            mRecorder.clear();
        }
    }

    /* ------------------------------------------------------------------ internals */

    private void handleEvent(AccessibilityEvent event) {
        switch (event.getEventType()) {
            case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED:
                scheduleAnalysis(0);
                break;

            case AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED:
                scheduleAnalysis(SCREEN_DEBOUNCE_MS);
                break;

            case AccessibilityEvent.TYPE_VIEW_CLICKED:
                onViewClicked(event);
                break;

            case AccessibilityEvent.TYPE_VIEW_SCROLLED:
                onViewScrolled(event);
                break;

            default:
                break;
        }
    }

    private void onViewClicked(AccessibilityEvent event) {
        String packageName = packageOf(event);
        if (packageName == null) return;

        LearnedElement element = elementOf(event, packageName);

        NavigationMemory.ScreenState state = mNavigationMemory.get(packageName);
        long fromScreenId = state != null ? state.screenId : 0;

        mRecorder.begin(packageName, fromScreenId, element,
                LearnedInteraction.TYPE_CLICK, System.currentTimeMillis());

        EyeDroidLog.d(TAG, "Click observed in " + packageName + ", waiting for the resulting screen");

        /* give the application the time to show the new screen before describing it */
        scheduleAnalysis(INTERACTION_OBSERVE_DELAY_MS);
    }

    private void onViewScrolled(AccessibilityEvent event) {
        String packageName = packageOf(event);
        if (packageName == null) return;

        LearningDatabase database = currentDatabase();
        if (database == null) return;

        LearnedElement element = elementOf(event, packageName);
        NavigationMemory.ScreenState state = mNavigationMemory.get(packageName);
        long fromScreenId = state != null ? state.screenId : 0;

        try {
            long applicationId = database.getOrCreateApplication(packageName);
            LearnedInteraction interaction = LearnedInteraction.forElement(
                    element, packageName, applicationId, fromScreenId, fromScreenId,
                    LearnedInteraction.TYPE_SCROLL, System.currentTimeMillis());

            database.recordInteraction(interaction);
            EyeDroidLog.d(TAG, "Scroll recorded in " + packageName);
        }
        catch (Throwable error) {
            EyeDroidLog.e(TAG, "Cannot record the scroll interaction", error);
        }
    }

    private void scheduleAnalysis(long delay) {
        final Handler handler;
        synchronized (mLock) {
            handler = mHandler;
        }
        if (handler == null) return;

        handler.removeCallbacks(mAnalyzeRunnable);
        handler.postDelayed(mAnalyzeRunnable, delay);
    }

    private void analyzeActiveScreen() {
        final AccessibilityService service;
        synchronized (mLock) {
            service = mService;
        }

        if (!mEnabled || service == null) return;

        final LearningDatabase database = currentDatabase();
        if (database == null) return;

        final long now = System.currentTimeMillis();
        if (!mRecorder.hasPending() && now - mLastAnalysis < MIN_ANALYSIS_INTERVAL_MS) return;

        AccessibilityNodeInfo root = null;
        try {
            root = service.getRootInActiveWindow();
            if (root == null) {
                if (now - mLastNoWindowLog > NO_WINDOW_LOG_INTERVAL_MS) {
                    mLastNoWindowLog = now;
                    EyeDroidLog.w(TAG, "No active window available to learn from");
                }
                return;
            }

            CharSequence packageName = root.getPackageName();
            if (packageName == null || packageName.length() == 0) return;

            String pkg = packageName.toString();
            if (pkg.equals(getOwnPackage())) return;

            ScreenAnalyzer.Result result = mAnalyzer.analyze(root, pkg);
            mLastAnalysis = now;

            long applicationId = database.getOrCreateApplication(pkg);
            long screenId = database.getOrCreateScreen(applicationId,
                    result.signature.getHash(), result.signature.getActivityName());
            database.upsertElements(screenId, result.elements);

            NavigationMemory.ScreenState previous = mNavigationMemory.get(pkg);
            mNavigationMemory.onScreenObserved(pkg, result.signature.getHash(), screenId, now);

            InteractionRecorder.Pending pending = mRecorder.take();
            if (pending != null && pending.packageName.equals(pkg)) {
                long fromScreenId = pending.fromScreenId > 0 ? pending.fromScreenId : screenId;

                LearnedInteraction interaction = LearnedInteraction.forElement(
                        pending.element, pending.packageName, applicationId,
                        fromScreenId, screenId, pending.interactionType, pending.timestamp);

                long interactionId = database.recordInteraction(interaction);

                /*
                 * The transition is only recorded when the resulting screen has really
                 * been observed to be a different one.
                 */
                boolean navigated = previous != null && previous.screenId != screenId;
                if (navigated) {
                    database.recordNavigation(applicationId, fromScreenId, screenId,
                            interactionId, now);
                }

                EyeDroidLog.i(TAG, "Learned " + pending.interactionType + " on " + pkg
                        + (navigated ? " leading to a new screen" : " staying on the same screen"));
            }

            EyeDroidLog.d(TAG, "Screen " + result.signature.getHash() + " of " + pkg
                    + " (" + result.elements.size() + " elements, "
                    + result.visitedNodes + " nodes)");
        }
        catch (Throwable error) {
            EyeDroidLog.e(TAG, "Screen analysis failed", error);
        }
        finally {
            if (root != null) {
                try {
                    root.recycle();
                }
                catch (Throwable ignored) { /* nothing to do */ }
            }
        }
    }

    private void startCollecting() {
        synchronized (mLock) {
            if (mThread != null) return;
            if (mContext == null) {
                EyeDroidLog.w(TAG, "Learning cannot start without a context");
                return;
            }

            HandlerThread thread = new HandlerThread("EyeDroid-Learning",
                    Process.THREAD_PRIORITY_BACKGROUND);
            thread.start();

            mThread = thread;
            mHandler = new Handler(thread.getLooper());
            mHandler.post(mOpenDatabaseRunnable);
        }
    }

    private void openDatabase() {
        final LearningDatabase database;
        synchronized (mLock) {
            if (mThread == null || mDatabase != null || mContext == null) return;
            database = new LearningDatabase(mContext);
            mDatabase = database;
        }

        try {
            database.getWritableDatabase();
            Diagnostics.get().setLearningDatabaseReady(true);
            EyeDroidLog.i(TAG, "Learning database ready");
        }
        catch (Throwable error) {
            synchronized (mLock) {
                if (mDatabase == database) mDatabase = null;
            }
            Diagnostics.get().setLearningDatabaseReady(false);
            EyeDroidLog.e(TAG, "Cannot open the learning database", error);
        }
    }

    private void stopWorkerLocked() {
        final HandlerThread thread = mThread;
        final LearningDatabase database = mDatabase;

        mThread = null;
        mHandler = null;
        mDatabase = null;

        if (thread != null) {
            try {
                thread.quitSafely();
            }
            catch (Throwable ignored) { /* nothing to do */ }
        }

        if (database != null) {
            try {
                database.close();
            }
            catch (Throwable ignored) { /* nothing to do */ }
        }

        mRecorder.clear();
        mNavigationMemory.clear();
    }

    private LearningDatabase currentDatabase() {
        synchronized (mLock) {
            return mDatabase;
        }
    }

    private Context getContext() {
        synchronized (mLock) {
            return mContext;
        }
    }

    private boolean readEnabledPreference() {
        Context context = getContext();
        if (context == null) return false;

        return PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(Preferences.KEY_LEARNING_ENABLED, false);
    }

    private void registerPreferenceListener() {
        Context context = getContext();
        if (context == null) return;

        synchronized (mLock) {
            if (mPreferenceListenerRegistered) return;
            PreferenceManager.getDefaultSharedPreferences(context)
                    .registerOnSharedPreferenceChangeListener(this);
            mPreferenceListenerRegistered = true;
        }
    }

    private void unregisterPreferenceListener() {
        Context context = getContext();
        if (context == null) return;

        synchronized (mLock) {
            if (!mPreferenceListenerRegistered) return;
            PreferenceManager.getDefaultSharedPreferences(context)
                    .unregisterOnSharedPreferenceChangeListener(this);
            mPreferenceListenerRegistered = false;
        }
    }

    private String getOwnPackage() {
        Context context = getContext();
        return context != null ? context.getPackageName() : "com.qali.eyedroid";
    }

    private static String packageOf(AccessibilityEvent event) {
        CharSequence packageName = event.getPackageName();
        if (packageName == null || packageName.length() == 0) return null;
        return packageName.toString();
    }

    private static LearnedElement elementOf(AccessibilityEvent event, String packageName) {
        AccessibilityNodeInfo source = null;
        try {
            source = event.getSource();
            if (source == null) return null;
            return LearnedElement.fromNode(source, packageName);
        }
        catch (Throwable error) {
            EyeDroidLog.w(TAG, "Cannot read the event source node", error);
            return null;
        }
        finally {
            if (source != null) {
                try {
                    source.recycle();
                }
                catch (Throwable ignored) { /* nothing to do */ }
            }
        }
    }
}
