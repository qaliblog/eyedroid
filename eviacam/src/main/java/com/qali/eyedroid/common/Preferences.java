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

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.preference.PreferenceManager;
import android.util.Log;

import com.qali.eyedroid.R;

import org.acra.ACRA;

@SuppressWarnings("WeakerAccess")
public class Preferences {
    /**
     * Preference file names
     */
    public static final String FILE_SLAVE_MODE= 
            Preferences.class.getPackage().getName() + ".slave_mode";

    /**
     * Preference keys
     */
    public static final String KEY_HORIZONTAL_SPEED = "horizontal_speed";
    public static final String KEY_VERTICAL_SPEED = "vertical_speed";
    public static final String KEY_ACCELERATION= "acceleration";
    public static final String KEY_MOTION_SMOOTHING= "motion_smoothing";
    public static final String KEY_MOTION_THRESHOLD= "motion_threshold";
    public static final String KEY_HEAD_MOTION_STRENGTH= "head_motion_strength";
    public static final String KEY_PUPIL_HORIZONTAL_GAIN= "pupil_horizontal_gain";
    public static final String KEY_PUPIL_VERTICAL_GAIN= "pupil_vertical_gain";
    public static final String KEY_PUPIL_EFFECT_GAIN= "pupil_effect_gain";
    public static final String KEY_BLINK_CLICK= "blink_click";
    public static final String KEY_BLINK_EYE= "blink_eye";
    public static final String KEY_BLINK_THRESHOLD= "blink_threshold";
    public static final String KEY_BLINK_HOLD_TIME= "blink_hold_time";
    public static final String KEY_DWELL_TIME= "dwell_time";
    public static final String KEY_DWELL_AREA= "dwell_area";
    public static final String KEY_SOUND_ON_CLICK= "sound_on_click";
    public static final String KEY_CONSECUTIVE_CLICKS = "consecutive_clicks";
    public static final String KEY_DOCKING_PANEL_EDGE= "docking_panel_edge";
    public static final String KEY_UI_ELEMENTS_SIZE= "ui_elements_size";
    public static final String KEY_TIME_WITHOUT_DETECTION= "time_without_detection";
    public static final String KEY_GAMEPAD_LOCATION= "gamepad_location";
    public static final String KEY_GAMEPAD_TRANSPARENCY= "gamepad_transparency";
    public static final String KEY_GAMEPAD_ABS_SPEED= "gamepad_abs_speed";
    public static final String KEY_GAMEPAD_REL_SENSITIVITY= "gamepad_rel_sensitivity";
    private static final String KEY_RUN_TUTORIAL= "run_tutorial";
    private static final String KEY_SHOW_LAUNCHER_HELP= "show_launcher_help";
    public static final String KEY_USE_CAMERA2_API= "use_camera2_api";
    private static final String KEY_ENGINE_WAS_RUNNING= "engine_was_running";
    private static final String KEY_SHOW_CONTEXT_MENU_HELP = "display_context_menu_help";

    /**
     * Wink eye values
     */
    public static final int BLINK_EYE_RIGHT= 0;
    public static final int BLINK_EYE_LEFT= 1;

    /**
     * Gamepad locations
     */
    public static final int LOCATION_GAMEPAD_TOP_LEFT= 0;
    public static final int LOCATION_GAMEPAD_BOTTOM_LEFT= 1;
    public static final int LOCATION_GAMEPAD_TOP_CENTER= 2;
    public static final int LOCATION_GAMEPAD_BOTTOM_CENTER= 3;
    public static final int LOCATION_GAMEPAD_TOP_RIGHT= 4;
    @SuppressWarnings("unused")
    public static final int LOCATION_GAMEPAD_BOTTOM_RIGHT= 5;

    /**
        Run-time constants
     */
    private final int AXIS_SPEED_DEFAULT;
    private final int AXIS_SPEED_MIN;
    private final int AXIS_SPEED_MAX;
    private final int ACCELERATION_DEFAULT;
    private final int ACCELERATION_MIN;
    private final int ACCELERATION_MAX;
    private final int MOTION_SMOOTHING_DEFAULT;
    private final int MOTION_SMOOTHING_MIN;
    private final int MOTION_SMOOTHING_MAX;
    private final int MOTION_THRESHOLD_DEFAULT;
    private final int MOTION_THRESHOLD_MIN;
    private final int MOTION_THRESHOLD_MAX;
    private final int HEAD_MOTION_STRENGTH_DEFAULT;
    private final int HEAD_MOTION_STRENGTH_MIN;
    private final int HEAD_MOTION_STRENGTH_MAX;
    private final int PUPIL_GAIN_DEFAULT;
    private final int PUPIL_GAIN_MIN;
    private final int PUPIL_GAIN_MAX;
    private final boolean SOUND_ON_CLICK_DEFAULT;
    private final boolean BLINK_CLICK_DEFAULT;
    private final int BLINK_THRESHOLD_DEFAULT;
    private final int BLINK_THRESHOLD_MIN;
    private final int BLINK_THRESHOLD_MAX;
    private final int BLINK_HOLD_TIME_DEFAULT;
    private final int BLINK_HOLD_TIME_MIN;
    private final int BLINK_HOLD_TIME_MAX;
    private final String BLINK_EYE_DEFAULT;
    private String[] TIME_WITHOUT_DETECTION_ENTRIES;
    private String[] TIME_WITHOUT_DETECTION_VALUES;

    // singleton instance
    private static Preferences sInstance= null;

    private final SharedPreferences mSharedPreferences;

    // initialization counter, allow for nested initialization/cleanup
    private int mInitCount= 0;

    // init mode
    private static final int INIT_NONE= 0;
    private static final int INIT_A11Y= 1;
    private static final int INIT_SLAVE_MODE= 2;
    private int mInitMode= INIT_NONE;

    // constructor
    private Preferences(Context c, SharedPreferences sp) {
        mSharedPreferences= sp;

        /*
         * Read run-time constants
         */
        final Resources r= c.getResources();

        AXIS_SPEED_DEFAULT= r.getInteger(R.integer.axis_speed_default);
        AXIS_SPEED_MIN= r.getInteger(R.integer.axis_speed_min);
        AXIS_SPEED_MAX= r.getInteger(R.integer.axis_speed_max);
        ACCELERATION_DEFAULT= r.getInteger(R.integer.acceleration_default);
        ACCELERATION_MIN= r.getInteger(R.integer.acceleration_min);
        ACCELERATION_MAX= r.getInteger(R.integer.acceleration_max);
        MOTION_SMOOTHING_DEFAULT= r.getInteger(R.integer.motion_smoothing_default);
        MOTION_SMOOTHING_MIN= r.getInteger(R.integer.motion_smoothing_min);
        MOTION_SMOOTHING_MAX= r.getInteger(R.integer.motion_smoothing_max);
        MOTION_THRESHOLD_DEFAULT= r.getInteger(R.integer.motion_threshold_default);
        MOTION_THRESHOLD_MIN= r.getInteger(R.integer.motion_threshold_min);
        MOTION_THRESHOLD_MAX= r.getInteger(R.integer.motion_threshold_max);
        HEAD_MOTION_STRENGTH_DEFAULT= r.getInteger(R.integer.head_motion_strength_default);
        HEAD_MOTION_STRENGTH_MIN= r.getInteger(R.integer.head_motion_strength_min);
        HEAD_MOTION_STRENGTH_MAX= r.getInteger(R.integer.head_motion_strength_max);
        PUPIL_GAIN_DEFAULT= r.getInteger(R.integer.pupil_gain_default);
        PUPIL_GAIN_MIN= r.getInteger(R.integer.pupil_gain_min);
        PUPIL_GAIN_MAX= r.getInteger(R.integer.pupil_gain_max);
        SOUND_ON_CLICK_DEFAULT= r.getBoolean(R.bool.sound_on_click_default);
        BLINK_CLICK_DEFAULT= r.getBoolean(R.bool.blink_click_default);
        BLINK_THRESHOLD_DEFAULT= r.getInteger(R.integer.blink_threshold_default);
        BLINK_THRESHOLD_MIN= r.getInteger(R.integer.blink_threshold_min);
        BLINK_THRESHOLD_MAX= r.getInteger(R.integer.blink_threshold_max);
        BLINK_HOLD_TIME_DEFAULT= r.getInteger(R.integer.blink_hold_time_default);
        BLINK_HOLD_TIME_MIN= r.getInteger(R.integer.blink_hold_time_min);
        BLINK_HOLD_TIME_MAX= r.getInteger(R.integer.blink_hold_time_max);
        BLINK_EYE_DEFAULT= r.getString(R.string.blink_eye_default);

        TIME_WITHOUT_DETECTION_VALUES= r.getStringArray(R.array.time_without_detection_values);
        TIME_WITHOUT_DETECTION_ENTRIES= r.getStringArray(R.array.time_without_detection_entries);

        /* Make sure both arrays have the same size */
        if (TIME_WITHOUT_DETECTION_VALUES.length != TIME_WITHOUT_DETECTION_ENTRIES.length)
            throw new ExceptionInInitializerError();
    }

    public void cleanup() {
        if (mInitCount> 0) {
            if (--mInitCount == 0) {
                Log.i(EVIACAM.TAG, "Preferences: cleanup");
                sInstance= null;
            }
        }
    }

    /**
     * Get current singleton instance
     *
     * @return a Preferences instance or null is not initialised
     */
    public static Preferences get() { return sInstance; }

    /**
     * Init preferences for A11Y service
     *
     * @param c instance to the accessibility service
     * @return reference to the Preferences instances or null if cannot be initialised
     */
    public static Preferences initForA11yService (Context c) {
        if (sInstance!= null) {
            // already initialised
            if (sInstance.mInitMode != INIT_A11Y) return null;
        }
        else {
            Log.i(EVIACAM.TAG, "Preferences: initForA11yService");

            // As accessibility service use the default preferences
            PreferenceManager.setDefaultValues(c, R.xml.preference_fragment, true);
            sInstance = new Preferences(c, PreferenceManager.getDefaultSharedPreferences(c));
            sInstance.mInitMode= INIT_A11Y;
        }

        ++sInstance.mInitCount;

        return sInstance;
    }

    /**
     * Init preferences for slave mode service
     *
     * @param c instance of the service
     * @return reference to the Preferences instances or null if cannot be initialised
     */
    public static Preferences initForSlaveService (Context c) {
        if (sInstance!= null) {
            // already initialised
            if (sInstance.mInitMode != INIT_SLAVE_MODE) return null;
        }
        else {
            Log.i(EVIACAM.TAG, "Preferences: initForSlaveService");

            // Slave mode preferences. We first load default default
            // preferences and then update with slave mode ones
            PreferenceManager.setDefaultValues(c, Preferences.FILE_SLAVE_MODE,
                    Context.MODE_PRIVATE,
                    R.xml.preference_fragment, true);
            PreferenceManager.setDefaultValues(c, Preferences.FILE_SLAVE_MODE,
                    Context.MODE_PRIVATE,
                    R.xml.gamepad_preference_fragment, true);

            // Set the slave mode shared preferences
            sInstance = new Preferences(c,
                    c.getSharedPreferences(Preferences.FILE_SLAVE_MODE, Context.MODE_PRIVATE));
            sInstance.mInitMode= INIT_SLAVE_MODE;
        }

        ++sInstance.mInitCount;

        return sInstance;
    }

    public SharedPreferences getSharedPreferences() {
        return mSharedPreferences;
    }

    /**
     * Constraint a value within a range
     *
     * @param v the value
     * @param min range min value
     * @param max range max value
     * @return constrained value
     */
    private static int constraint (int v, int min, int max) {
        if (v< min) return min;
        if (v> max) return max;
        return v;
    }

    /**
     * Get horizontal speed value
     */
    public int getHorizontalSpeed() {
        int v= mSharedPreferences.getInt(Preferences.KEY_HORIZONTAL_SPEED, AXIS_SPEED_DEFAULT);
        return constraint (v, AXIS_SPEED_MIN, AXIS_SPEED_MAX);
    }

    /**
     * Save horizontal speed value
     * @param v value
     * @return sanitized horizontal speed value
     */
    public int setHorizontalSpeed(int v) {
        v= constraint (v, AXIS_SPEED_MIN, AXIS_SPEED_MAX);
        SharedPreferences.Editor spe= mSharedPreferences.edit();
        spe.putInt(Preferences.KEY_HORIZONTAL_SPEED, v);
        spe.apply();
        return v;
    }

    /**
     * Get vertical speed value
     */
    public int getVerticalSpeed() {
        int v= mSharedPreferences.getInt(Preferences.KEY_VERTICAL_SPEED, AXIS_SPEED_DEFAULT);
        return constraint(v, AXIS_SPEED_MIN, AXIS_SPEED_MAX);
    }

    /**
     * Save vertical speed value
     * @param v value
     * @return sanitized vertical speed value
     */
    public int setVerticalSpeed(int v) {
        v= constraint (v, AXIS_SPEED_MIN, AXIS_SPEED_MAX);
        SharedPreferences.Editor spe= mSharedPreferences.edit();
        spe.putInt(Preferences.KEY_VERTICAL_SPEED, v);
        spe.apply();
        return v;
    }

    public int getAcceleration() {
        int v= mSharedPreferences.getInt(Preferences.KEY_ACCELERATION, ACCELERATION_DEFAULT);
        return constraint (v, ACCELERATION_MIN, ACCELERATION_MAX);
    }

    public int getMotionSmoothing() {
        int v= mSharedPreferences.getInt(
                Preferences.KEY_MOTION_SMOOTHING, MOTION_SMOOTHING_DEFAULT);
        return constraint (v, MOTION_SMOOTHING_MIN, MOTION_SMOOTHING_MAX);
    }

    public int getMotionThreshold() {
        int v= mSharedPreferences.getInt(
                Preferences.KEY_MOTION_THRESHOLD, MOTION_THRESHOLD_DEFAULT);
        return constraint (v, MOTION_THRESHOLD_MIN, MOTION_THRESHOLD_MAX);
    }

    public boolean getSoundOnClick() {
        return mSharedPreferences.getBoolean(
                Preferences.KEY_SOUND_ON_CLICK, SOUND_ON_CLICK_DEFAULT);
    }

    /**
     * Get the strength (percent) of the head movement over the pointer
     */
    public int getHeadMotionStrength() {
        int v= mSharedPreferences.getInt(
                Preferences.KEY_HEAD_MOTION_STRENGTH, HEAD_MOTION_STRENGTH_DEFAULT);
        return constraint(v, HEAD_MOTION_STRENGTH_MIN, HEAD_MOTION_STRENGTH_MAX);
    }

    /**
     * Get the horizontal gain (percent) of the pupil movement
     */
    public int getPupilHorizontalGain() {
        int v= mSharedPreferences.getInt(
                Preferences.KEY_PUPIL_HORIZONTAL_GAIN, PUPIL_GAIN_DEFAULT);
        return constraint(v, PUPIL_GAIN_MIN, PUPIL_GAIN_MAX);
    }

    /**
     * Get the vertical gain (percent) of the pupil movement
     */
    public int getPupilVerticalGain() {
        int v= mSharedPreferences.getInt(
                Preferences.KEY_PUPIL_VERTICAL_GAIN, PUPIL_GAIN_DEFAULT);
        return constraint(v, PUPIL_GAIN_MIN, PUPIL_GAIN_MAX);
    }

    /**
     * Get the overall gain (percent) of the pupil movement
     */
    public int getPupilEffectGain() {
        int v= mSharedPreferences.getInt(
                Preferences.KEY_PUPIL_EFFECT_GAIN, PUPIL_GAIN_DEFAULT);
        return constraint(v, PUPIL_GAIN_MIN, PUPIL_GAIN_MAX);
    }

    /**
     * Get whether clicks are generated through a half blink of the right eye
     */
    public boolean getBlinkClick() {
        return mSharedPreferences.getBoolean(
                Preferences.KEY_BLINK_CLICK, BLINK_CLICK_DEFAULT);
    }

    /**
     * Get which eye generates the half blink
     *
     * @return {@link #BLINK_EYE_RIGHT} or {@link #BLINK_EYE_LEFT}
     */
    public int getBlinkEye() {
        String v= mSharedPreferences.getString(
                Preferences.KEY_BLINK_EYE, BLINK_EYE_DEFAULT);
        return "left".equals(v) ? BLINK_EYE_LEFT : BLINK_EYE_RIGHT;
    }

    /**
     * Get the eyelid closure (percent) needed to detect the half blink
     */
    public int getBlinkThreshold() {
        int v= mSharedPreferences.getInt(
                Preferences.KEY_BLINK_THRESHOLD, BLINK_THRESHOLD_DEFAULT);
        return constraint(v, BLINK_THRESHOLD_MIN, BLINK_THRESHOLD_MAX);
    }

    /**
     * Get the time (milliseconds) after which a held half blink is a long click
     */
    public int getBlinkHoldTime() {
        int v= mSharedPreferences.getInt(
                Preferences.KEY_BLINK_HOLD_TIME, BLINK_HOLD_TIME_DEFAULT);
        return constraint(v, BLINK_HOLD_TIME_MIN, BLINK_HOLD_TIME_MAX);
    }

    public float getUIElementsSize() {
        return Float.parseFloat(mSharedPreferences.getString(KEY_UI_ELEMENTS_SIZE, null));
    }

    public int getTimeWithoutDetection() {
        return Integer.parseInt(mSharedPreferences.getString(KEY_TIME_WITHOUT_DETECTION, null));
    }

    public String getTimeWithoutDetectionEntryValue() {
        // current value
        int value= getTimeWithoutDetection();

        // search value in array entries
        int pos;
        for (pos= 0; pos< TIME_WITHOUT_DETECTION_VALUES.length; pos++) {
            if (TIME_WITHOUT_DETECTION_VALUES[pos].contentEquals(String.valueOf(value))) break;
        }

        // if found, pick the entry value
        if (pos< TIME_WITHOUT_DETECTION_VALUES.length) {
            return TIME_WITHOUT_DETECTION_ENTRIES[pos];
        }

        // should never happen
        throw new UnknownError();
    }

    public boolean getRunTutorial() {
        return mSharedPreferences.getBoolean(KEY_RUN_TUTORIAL, true);
    }

    public void setRunTutorial(boolean value) {
        SharedPreferences.Editor spe= mSharedPreferences.edit();
        spe.putBoolean(KEY_RUN_TUTORIAL, value);
        spe.apply();
    }

    public boolean getShowLauncherHelp () {
        return mSharedPreferences.getBoolean(KEY_SHOW_LAUNCHER_HELP, true);
    }

    public void setShowLauncherHelp (boolean value) {
        SharedPreferences.Editor spe= mSharedPreferences.edit();
        spe.putBoolean(KEY_SHOW_LAUNCHER_HELP, value);
        spe.apply();
    }

    public enum UseCamera2API { NO, YES, AUTO }
    public UseCamera2API getUseCamera2API() {
        String val= mSharedPreferences.getString(KEY_USE_CAMERA2_API, "auto");
        if (val.equals("no")) return UseCamera2API.NO;
        if (val.equals("yes")) return UseCamera2API.YES;
        return UseCamera2API.AUTO;
    }

    public int getGamepadLocation() {
        return Integer.parseInt(mSharedPreferences.getString(KEY_GAMEPAD_LOCATION, null));
    }

    public int getGamepadTransparency() {
        return Integer.parseInt(mSharedPreferences.getString(KEY_GAMEPAD_TRANSPARENCY, "100"));
    }

    public int getGamepadAbsSpeed() {
        return mSharedPreferences.getInt(KEY_GAMEPAD_ABS_SPEED, 0);
    }

    public int getGamepadRelSensitivity() {
        return mSharedPreferences.getInt(KEY_GAMEPAD_REL_SENSITIVITY, 0);
    }

    public void setACRAEnabled (boolean value) {
        SharedPreferences.Editor spe= mSharedPreferences.edit();
        spe.putBoolean(ACRA.PREF_ENABLE_ACRA, value);
        spe.apply();
    }

    /**
     * Get whether the engine was running (i.e. not stopped through the notification)
     */
    public boolean getEngineWasRunning() {
        return mSharedPreferences.getBoolean(Preferences.KEY_ENGINE_WAS_RUNNING, true);
    }

    /**
     * Save whether the engine was running
     * @param v value
     */
    public void setEngineWasRunning(boolean v) {
        SharedPreferences.Editor spe= mSharedPreferences.edit();
        spe.putBoolean(Preferences.KEY_ENGINE_WAS_RUNNING, v);
        spe.apply();
    }

    /**
     * Get whether need to show the context menu help dialog
     *
     * @return true when need to show it
     */
    public boolean getShowContextMenuHelp () {
        return mSharedPreferences.getBoolean(Preferences.KEY_SHOW_CONTEXT_MENU_HELP, true);
    }

    /**
     * Save whether need to show the context menu help dialog
     * @param v value to save
     */
    public void setShowContextMenuHelp (boolean v) {
        SharedPreferences.Editor spe= mSharedPreferences.edit();
        spe.putBoolean(Preferences.KEY_SHOW_CONTEXT_MENU_HELP, v);
        spe.apply();
    }
 }
 