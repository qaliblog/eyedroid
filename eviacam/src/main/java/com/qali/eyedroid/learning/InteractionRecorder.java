/*
 * EyeDroid
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.qali.eyedroid.learning;

/**
 * Holds the interaction which is waiting for the screen it produced.
 *
 * A click is not considered a successful navigation just because the accessibility
 * event arrived: the recorder keeps it pending until the resulting screen has been
 * observed, and only then the interaction (and, when the screen actually changed, the
 * transition) is written to the database.
 */
public class InteractionRecorder {

    /**
     * Interaction waiting for its resulting screen
     */
    public static class Pending {
        public final String packageName;
        public final long fromScreenId;
        public final LearnedElement element;
        public final String interactionType;
        public final long timestamp;

        Pending(String packageName, long fromScreenId, LearnedElement element,
                String interactionType, long timestamp) {
            this.packageName = packageName;
            this.fromScreenId = fromScreenId;
            this.element = element;
            this.interactionType = interactionType;
            this.timestamp = timestamp;
        }
    }

    private Pending mPending;

    public synchronized void begin(String packageName, long fromScreenId,
                                   LearnedElement element, String interactionType,
                                   long timestamp) {
        mPending = new Pending(packageName, fromScreenId, element, interactionType, timestamp);
    }

    public synchronized boolean hasPending() {
        return mPending != null;
    }

    public synchronized Pending peek() {
        return mPending;
    }

    public synchronized Pending take() {
        Pending pending = mPending;
        mPending = null;
        return pending;
    }

    public synchronized void clear() {
        mPending = null;
    }
}
