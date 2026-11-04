/*
 * EyeDroid
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.qali.eyedroid.learning;

import android.view.accessibility.AccessibilityNodeInfo;

import com.qali.eyedroid.debug.EyeDroidLog;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Walks the accessibility hierarchy of the active window and produces the elements and
 * the signature of the screen.
 *
 * The traversal is bounded (node count and depth) so that a huge web page cannot block
 * the learning thread. Every node obtained through {@link AccessibilityNodeInfo#getChild}
 * is recycled; the root node belongs to the caller and is left untouched.
 */
public class ScreenAnalyzer {

    private static final int MAX_NODES = 250;
    private static final int MAX_DEPTH = 25;

    /**
     * Result of analysing one screen
     */
    public static class Result {
        public final ScreenSignature signature;
        public final List<LearnedElement> elements;
        public final int visitedNodes;

        Result(ScreenSignature signature, List<LearnedElement> elements, int visitedNodes) {
            this.signature = signature;
            this.elements = elements;
            this.visitedNodes = visitedNodes;
        }
    }

    private static class NodeRef {
        final AccessibilityNodeInfo node;
        final int depth;
        final boolean owned;

        NodeRef(AccessibilityNodeInfo node, int depth, boolean owned) {
            this.node = node;
            this.depth = depth;
            this.owned = owned;
        }
    }

    /**
     * @param root active window root, not recycled by this method
     * @param packageName package of the screen
     * @return the elements and signature of the screen
     */
    public Result analyze(AccessibilityNodeInfo root, String packageName) {
        final List<LearnedElement> elements = new ArrayList<>();
        final ArrayDeque<NodeRef> queue = new ArrayDeque<>();

        String activityName = null;
        int visited = 0;

        queue.addLast(new NodeRef(root, 0, false));

        while (!queue.isEmpty() && visited < MAX_NODES) {
            NodeRef ref = queue.removeFirst();
            AccessibilityNodeInfo node = ref.node;

            try {
                if (activityName == null) {
                    CharSequence cls = node.getClassName();
                    if (cls != null && cls.toString().indexOf('.') > 0) {
                        activityName = cls.toString();
                    }
                }

                LearnedElement element = LearnedElement.fromNode(node, packageName);
                if (element != null && isInteresting(element)) {
                    element.order = visited;
                    elements.add(element);
                }

                visited++;

                if (ref.depth < MAX_DEPTH) {
                    int children = node.getChildCount();
                    for (int i = 0; i < children && visited + queue.size() < MAX_NODES; i++) {
                        AccessibilityNodeInfo child = node.getChild(i);
                        if (child != null) queue.addLast(new NodeRef(child, ref.depth + 1, true));
                    }
                }
            }
            catch (Throwable error) {
                EyeDroidLog.w(EyeDroidLog.TAG_LEARNING, "Cannot read accessibility node", error);
            }
            finally {
                if (ref.owned) recycle(node);
            }
        }

        /* release the nodes which were not processed because of the limits */
        while (!queue.isEmpty()) {
            NodeRef ref = queue.removeFirst();
            if (ref.owned) recycle(ref.node);
        }

        ScreenSignature signature = ScreenSignature.compute(packageName, activityName, elements);
        return new Result(signature, elements, visited);
    }

    private static boolean isInteresting(LearnedElement element) {
        if (element.clickable || element.scrollable) return true;
        if (element.text != null) return true;
        if (element.contentDescription != null) return true;
        if (element.viewId != null && element.viewId.length() > 0) return true;
        return false;
    }

    private static void recycle(AccessibilityNodeInfo node) {
        try {
            node.recycle();
        }
        catch (Throwable ignored) {
            /* already recycled or detached */
        }
    }
}
