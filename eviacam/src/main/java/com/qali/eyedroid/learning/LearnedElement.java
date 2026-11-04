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
import android.view.accessibility.AccessibilityNodeInfo;

/**
 * A UI element which has been observed on a screen.
 *
 * Only safe properties are kept: class, resource id, a short text/description and the
 * bounds. Password fields are never recorded, and coordinates are deliberately not part
 * of {@link #stableKey()}, so an element can be found again after it moves.
 */
public class LearnedElement {

    /* Longest text kept for an element */
    private static final int MAX_TEXT_LENGTH = 64;

    public long id;
    public long screenId;

    public String packageName;
    public String className;
    public String viewId;
    public String text;
    public String contentDescription;

    public int left;
    public int top;
    public int right;
    public int bottom;

    public boolean clickable;
    public boolean scrollable;

    /* Position in the traversal order, only used as a last resort key */
    public int order;

    /**
     * Property based identifier of the element, stable when the element moves.
     */
    public String stableKey() {
        String base;

        if (isMeaningful(viewId)) {
            base = "id:" + viewId;
        }
        else if (isMeaningful(contentDescription)) {
            base = "cd:" + contentDescription;
        }
        else if (isMeaningful(text)) {
            base = "t:" + text;
        }
        else {
            base = "cls:" + (className != null ? className : "?") + "#" + order;
        }

        return base + (clickable ? "|c" : "") + (scrollable ? "|s" : "");
    }

    public Rect bounds() {
        return new Rect(left, top, right, bottom);
    }

    /**
     * Read a node into an element.
     *
     * The node is only read, never recycled here: the caller owns it.
     *
     * @param node the node to read
     * @param packageName package of the screen the node belongs to
     * @return the element or null when the node cannot be read
     */
    public static LearnedElement fromNode(AccessibilityNodeInfo node, String packageName) {
        if (node == null) return null;

        try {
            LearnedElement element = new LearnedElement();
            element.packageName = packageName;

            CharSequence cls = node.getClassName();
            element.className = cls != null ? cls.toString() : null;

            element.viewId = node.getViewIdResourceName();

            /* never keep the contents of password fields */
            boolean password = false;
            try {
                password = node.isPassword();
            }
            catch (Throwable ignored) { /* not supported by this node */ }

            element.text = password ? null : shorten(node.getText());
            element.contentDescription = shorten(node.getContentDescription());

            Rect bounds = new Rect();
            node.getBoundsInScreen(bounds);
            element.left = bounds.left;
            element.top = bounds.top;
            element.right = bounds.right;
            element.bottom = bounds.bottom;

            element.clickable = node.isClickable();
            element.scrollable = node.isScrollable();

            return element;
        }
        catch (Throwable ignored) {
            return null;
        }
    }

    private static String shorten(CharSequence value) {
        if (value == null) return null;

        String text = value.toString().trim();
        if (text.length() == 0) return null;
        if (text.length() > MAX_TEXT_LENGTH) text = text.substring(0, MAX_TEXT_LENGTH);

        return text;
    }

    private static boolean isMeaningful(String value) {
        return value != null && value.length() > 0;
    }

    @Override
    public String toString() {
        return stableKey();
    }
}
