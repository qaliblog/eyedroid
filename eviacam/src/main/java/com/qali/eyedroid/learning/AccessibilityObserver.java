/*
 * EyeDroid
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.qali.eyedroid.learning;

import android.view.accessibility.AccessibilityEvent;

/**
 * Decides which accessibility events are worth learning from.
 *
 * Only the events already subscribed by the service are considered, the service's own
 * UI is ignored, and events without a package are dropped.
 */
public class AccessibilityObserver {

    private long mEventCount;
    private long mLastEventTime;
    private String mLastPackage;

    /**
     * @param event the received event
     * @param ownPackage package of EyeDroid itself, ignored to avoid learning our UI
     * @return true when the event should be processed
     */
    public boolean shouldObserve(AccessibilityEvent event, String ownPackage) {
        if (event == null) return false;

        final int type = event.getEventType();
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
                type != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
                type != AccessibilityEvent.TYPE_VIEW_CLICKED &&
                type != AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            return false;
        }

        CharSequence packageName = event.getPackageName();
        if (packageName == null || packageName.length() == 0) return false;
        if (ownPackage != null && ownPackage.contentEquals(packageName)) return false;

        mEventCount++;
        mLastEventTime = System.currentTimeMillis();
        mLastPackage = packageName.toString();

        return true;
    }

    public long getEventCount() {
        return mEventCount;
    }

    public long getLastEventTime() {
        return mLastEventTime;
    }

    public String getLastPackage() {
        return mLastPackage;
    }
}
