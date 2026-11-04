/*
 * EyeDroid
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.qali.eyedroid.learning;

import java.security.MessageDigest;
import java.util.List;
import java.util.TreeSet;

/**
 * Fingerprint of a screen.
 *
 * The signature is built from the package, the activity and the set of stable element
 * keys, so the same screen is recognised again even when its elements have moved or
 * when it is scrolled back into a known state. Coordinates are never part of it.
 */
public class ScreenSignature {

    private final String mPackageName;
    private final String mActivityName;
    private final String mHash;
    private final int mElementCount;

    private ScreenSignature(String packageName, String activityName, String hash,
                            int elementCount) {
        mPackageName = packageName != null ? packageName : "";
        mActivityName = activityName;
        mHash = hash;
        mElementCount = elementCount;
    }

    public static ScreenSignature compute(String packageName, String activityName,
                                          List<LearnedElement> elements) {
        TreeSet<String> keys = new TreeSet<>();
        if (elements != null) {
            for (LearnedElement element : elements) {
                if (element != null) keys.add(element.stableKey());
            }
        }

        StringBuilder sb = new StringBuilder();
        for (String key : keys) sb.append(key).append('\n');

        return new ScreenSignature(packageName, activityName, hash(sb.toString()), keys.size());
    }

    private static String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] bytes = digest.digest(value.getBytes("UTF-8"));

            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                String hex = Integer.toHexString(bytes[i] & 0xff);
                if (hex.length() == 1) sb.append('0');
                sb.append(hex);
            }
            return sb.toString();
        }
        catch (Throwable ignored) {
            return Integer.toHexString(value.hashCode());
        }
    }

    public String getPackageName() {
        return mPackageName;
    }

    public String getActivityName() {
        return mActivityName;
    }

    public String getHash() {
        return mHash;
    }

    public int getElementCount() {
        return mElementCount;
    }

    public boolean isSameScreen(ScreenSignature other) {
        return other != null && mHash.equals(other.mHash) &&
                mPackageName.equals(other.mPackageName);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ScreenSignature)) return false;
        return isSameScreen((ScreenSignature) o);
    }

    @Override
    public int hashCode() {
        return mHash.hashCode() * 31 + mPackageName.hashCode();
    }

    @Override
    public String toString() {
        return mPackageName + "/" + mHash + " (" + mElementCount + " elements)";
    }
}
