/*
 * EyeDroid
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.qali.eyedroid.learning;

import java.util.HashMap;
import java.util.Map;

/**
 * Remembers the last known screen of each application so that interactions can be
 * associated with the screen they started from and transitions between screens can be
 * derived from what is actually observed on screen.
 */
public class NavigationMemory {

    /**
     * Last known state of the screen of an application
     */
    public static class ScreenState {
        public final String signature;
        public final long screenId;
        public final long timestamp;

        ScreenState(String signature, long screenId, long timestamp) {
            this.signature = signature;
            this.screenId = screenId;
            this.timestamp = timestamp;
        }
    }

    private final Map<String, ScreenState> mScreens = new HashMap<>();

    public synchronized ScreenState get(String packageName) {
        if (packageName == null) return null;
        return mScreens.get(packageName);
    }

    /**
     * Record that a screen has been observed.
     *
     * @return true when the observed screen differs from the previously known one,
     *         i.e. a transition really happened
     */
    public synchronized boolean onScreenObserved(String packageName, String signature,
                                                 long screenId, long timestamp) {
        if (packageName == null) return false;

        ScreenState previous = mScreens.get(packageName);
        mScreens.put(packageName, new ScreenState(signature, screenId, timestamp));

        return previous != null && !previous.signature.equals(signature);
    }

    public synchronized void clear() {
        mScreens.clear();
    }

    public synchronized int getObservedApplicationCount() {
        return mScreens.size();
    }
}
