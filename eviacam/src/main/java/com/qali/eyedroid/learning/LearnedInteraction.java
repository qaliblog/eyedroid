/*
 * EyeDroid
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.qali.eyedroid.learning;

import android.graphics.Rect;

/**
 * One interaction performed by the user, together with the screen it led to.
 */
public class LearnedInteraction {

    public static final String TYPE_CLICK = "click";
    public static final String TYPE_LONG_CLICK = "long_click";
    public static final String TYPE_SCROLL = "scroll";

    public long id;

    public long applicationId;
    public String packageName;

    /* 0 when the screen was not known yet */
    public long fromScreenId;
    public long toScreenId;

    public String elementKey;
    public String interactionType;

    public String className;
    public String viewId;
    public String text;
    public String contentDescription;

    public Rect bounds = new Rect();

    public long timestamp;
    public int successCount = 1;

    /**
     * Build a click-like interaction for an element.
     *
     * @param element element which was activated, may be null when it could not be read
     * @param packageName package of the application
     * @param applicationId database id of the application
     * @param fromScreenId screen the interaction started from
     * @param toScreenId screen observed after the interaction
     * @param type one of the TYPE_* constants
     * @param timestamp when the interaction happened
     */
    public static LearnedInteraction forElement(LearnedElement element, String packageName,
                                                long applicationId, long fromScreenId,
                                                long toScreenId, String type, long timestamp) {
        LearnedInteraction interaction = new LearnedInteraction();

        interaction.applicationId = applicationId;
        interaction.packageName = packageName;
        interaction.fromScreenId = fromScreenId;
        interaction.toScreenId = toScreenId;
        interaction.interactionType = type;
        interaction.timestamp = timestamp;

        if (element != null) {
            interaction.elementKey = element.stableKey();
            interaction.className = element.className;
            interaction.viewId = element.viewId;
            interaction.text = element.text;
            interaction.contentDescription = element.contentDescription;
            interaction.bounds.set(element.left, element.top, element.right, element.bottom);
        }
        else {
            interaction.elementKey = "viewport";
        }

        return interaction;
    }
}
