/*
 * Enable Viacam for Android, a camera based mouse emulator
 *
 * Copyright (C) 2015-17 Cesar Mauri Loba (CREA Software Systems)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.qali.eyedroid.common;

import android.content.SharedPreferences;
import android.graphics.PointF;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.qali.eyedroid.vision.PupilDetection;

/**
 * Combines the head motion provided by the vision pipeline with the motion of the
 * pupils provided by the pupil detector.
 *
 * The head motion is scaled down by the "head movement strength" setting. The pupil
 * motion is computed as the frame to frame displacement of the iris centroid, scaled
 * by the pupil settings.
 */
class PupilMotionControl implements SharedPreferences.OnSharedPreferenceChangeListener {

    /* reference capture size, used to convert normalized displacements into pixels */
    private static final float REFERENCE_WIDTH= 352f;
    private static final float REFERENCE_HEIGHT= 288f;

    /* base scaling of the pupil displacement, tuned so that it is comparable to
       the motion provided by the vision pipeline for the same amount of effort */
    private static final float PUPIL_SCALE= 0.25f;

    /* displacements above this value are considered tracking errors and ignored */
    private static final float MAX_PUPIL_STEP= 0.08f;

    /* pupil motion is not applied while the eyes are closed */
    private static final float BLINK_THRESHOLD= 0.4f;

    // head motion gain (from the "head movement strength" setting)
    private float mHeadGain= 0.1f;

    // pupil gains (from the pupil settings)
    private float mPupilGainX= 1.0f;
    private float mPupilGainY= 1.0f;
    private float mPupilEffectGain= 1.0f;

    // whether the pupil detector is available
    private boolean mPupilAvailable= false;

    // previous pupil position (needed to compute the displacement)
    private final PointF mPrevPupil= new PointF();
    private boolean mHasPrevPupil= false;

    PupilMotionControl() {
        Preferences.get().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
        updateSettings();
    }

    void cleanup() {
        Preferences.get().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals(Preferences.KEY_HEAD_MOTION_STRENGTH) ||
                key.equals(Preferences.KEY_PUPIL_HORIZONTAL_GAIN) ||
                key.equals(Preferences.KEY_PUPIL_VERTICAL_GAIN) ||
                key.equals(Preferences.KEY_PUPIL_EFFECT_GAIN)) {
            updateSettings();
        }
    }

    private void updateSettings() {
        final Preferences p= Preferences.get();
        mHeadGain= p.getHeadMotionStrength() / 100.0f;
        mPupilGainX= p.getPupilHorizontalGain() / 100.0f;
        mPupilGainY= p.getPupilVerticalGain() / 100.0f;
        mPupilEffectGain= p.getPupilEffectGain() / 100.0f;
    }

    /**
     * Notify whether the pupil detector is available. When it is not, head motion
     * is applied with its full effect so that the pointer remains usable.
     */
    void setPupilAvailable(boolean available) {
        mPupilAvailable= available;
        if (!available) mHasPrevPupil= false;
    }

    /**
     * Forget the previous pupil position, the next detection starts a new reference
     */
    void reset() {
        mHasPrevPupil= false;
    }

    /**
     * Compute the combined motion
     *
     * @param headMotion motion provided by the vision pipeline (may be null)
     * @param detection last pupil detection, null if there is no new detection
     * @param out output motion vector
     */
    void combine(@Nullable PointF headMotion, @Nullable PupilDetection detection,
                 @NonNull PointF out) {
        final float headGain= mPupilAvailable ? mHeadGain : 1.0f;

        out.x= (headMotion != null) ? headMotion.x * headGain : 0.0f;
        out.y= (headMotion != null) ? headMotion.y * headGain : 0.0f;

        if (!mPupilAvailable || detection == null) return;

        if (detection.blinkLeft >= BLINK_THRESHOLD || detection.blinkRight >= BLINK_THRESHOLD) {
            // eyes closed, do not move the pointer
            return;
        }

        if (!mHasPrevPupil) {
            mPrevPupil.set(detection.x, detection.y);
            mHasPrevPupil= true;
            return;
        }

        float dx= detection.x - mPrevPupil.x;
        float dy= detection.y - mPrevPupil.y;
        mPrevPupil.set(detection.x, detection.y);

        // discard jumps (e.g. the face was lost and found again elsewhere)
        if (Math.abs(dx) > MAX_PUPIL_STEP || Math.abs(dy) > MAX_PUPIL_STEP) return;

        // the camera image is not mirrored, therefore the horizontal displacement
        // needs to be inverted to compensate the mirror effect, as done for the face
        out.x += -dx * REFERENCE_WIDTH * PUPIL_SCALE * mPupilEffectGain * mPupilGainX;
        out.y += dy * REFERENCE_HEIGHT * PUPIL_SCALE * mPupilEffectGain * mPupilGainY;
    }
}
