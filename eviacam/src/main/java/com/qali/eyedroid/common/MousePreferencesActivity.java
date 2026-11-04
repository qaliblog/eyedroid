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

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceFragment;
import android.preference.PreferenceGroup;
import android.widget.Toast;

import com.qali.eyedroid.BuildConfig;
import com.qali.eyedroid.R;
import com.qali.eyedroid.debug.DebugActivity;
import com.qali.eyedroid.learning.LearningManager;
import com.qali.eyedroid.util.NumberPickerPreference;

/**
 * The main preferences activity. It is also used for the mouse specific
 * settings when working in slave mode.
 */
public class MousePreferencesActivity extends Activity {
    /*
     * Listener for list preferences
     */
    private static class ListPreferenceUpdate implements OnPreferenceChangeListener {

        private final ListPreference mListPreference;

        ListPreferenceUpdate(ListPreference lp) {
            mListPreference = lp;
        }

        @Override
        public boolean onPreferenceChange(Preference preference, Object newValue) {
            // Set the value as the new value
            mListPreference.setValue(newValue.toString());                    
            // Get the entry which corresponds to the current value and set as summary
            preference.setSummary(mListPreference.getEntry());
            return true;
        }
    }

    /*
     * The settings fragment
     */
    public static class SettingsFragment extends PreferenceFragment {
        // Factory method to pass initial arguments using the bundle
        public static SettingsFragment newInstance (boolean slaveMode) {
            SettingsFragment sf= new SettingsFragment();

            Bundle args= new Bundle();
            args.putBoolean("slaveMode", slaveMode);
            sf.setArguments(args);

            return sf;
        }

        // return whether the fragment has been started in slave mode
        private boolean getSlaveMode() {
            return getArguments().getBoolean("slaveMode");
        }

        /* entries of the intelligent learning category */
        private Preference mLearnedScreens;
        private Preference mLearnedInteractions;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            setRetainInstance(true);

            final boolean slaveMode= getSlaveMode();

            if (slaveMode) {
                // In slave mode use different preference file
                getPreferenceManager().setSharedPreferencesName(Preferences.FILE_SLAVE_MODE);
            }

            // Load the preferences from an XML resource
            addPreferencesFromResource(R.xml.preference_fragment);

            /*
             * Lock speed settings checkbox
             */

            /* Disable vertical speed if needed */
            final NumberPickerPreference vSpeedPreference = (NumberPickerPreference)
                    getPreferenceScreen().findPreference("vertical_speed");
            final NumberPickerPreference hSpeedPreference = (NumberPickerPreference)
                    getPreferenceScreen().findPreference("horizontal_speed");

            CheckBoxPreference lockSpeedPreferences =
                    (CheckBoxPreference) getPreferenceScreen().findPreference("lock_speeds");
            final boolean lockSpeeds = lockSpeedPreferences.isChecked();
            if (lockSpeeds) {
                vSpeedPreference.setValue(hSpeedPreference.getValue());
                vSpeedPreference.setEnabled(false);
            }

            lockSpeedPreferences.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    if ((Boolean) newValue) {
                        // Lock speed preferences
                        vSpeedPreference.setValue(hSpeedPreference.getValue());
                        vSpeedPreference.setEnabled(false);
                    } else {
                        vSpeedPreference.setEnabled(true);
                    }
                    return true;
                }
            });

            hSpeedPreference.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    if (lockSpeeds) {
                        vSpeedPreference.setValue((int) newValue);
                    }
                    return true;
                }
            });

            /*
             * Remove preferences not applicable in slave mode
            */
            if (slaveMode) {
                PreferenceGroup cat = (PreferenceGroup) getPreferenceScreen().
                        findPreference("interface_settings");
                Preference p = getPreferenceScreen().
                        findPreference(Preferences.KEY_DOCKING_PANEL_EDGE);
                cat.removePreference(p);
            }
            else {
                ListPreference lp = (ListPreference) findPreference(Preferences.KEY_DOCKING_PANEL_EDGE);
                lp.setOnPreferenceChangeListener(new ListPreferenceUpdate(lp));
            }

            /*
             * Version button
             */
            Preference p = getPreferenceScreen().findPreference("version");
            p.setSummary(getResources().getText(R.string.app_name) +
                    " " + BuildConfig.VERSION_NAME);
            p.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                int clickCount;

                @Override
                public boolean onPreferenceClick(Preference preference) {
                    clickCount++;
                    if (clickCount >= 4) {
                        clickCount = 0;
                        Intent i = new Intent(getActivity(), TechInfoActivity.class);
                        startActivity(i);
                    }
                    return true;
                }
            });

            /*
             * Debug screen, directly accessible (no hidden gesture required)
             */
            Preference debugPreference = getPreferenceScreen().findPreference("debug");
            if (debugPreference != null) {
                debugPreference.setOnPreferenceClickListener(
                        new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        startActivity(new Intent(getActivity(), DebugActivity.class));
                        return true;
                    }
                });
            }

            /*
             * Intelligent learning. Slave mode uses its own preference storage and must
             * never activate learning, so the whole category is removed there.
             */
            PreferenceGroup learningCategory = (PreferenceGroup) getPreferenceScreen()
                    .findPreference("learning_settings");
            if (slaveMode) {
                if (learningCategory != null) {
                    getPreferenceScreen().removePreference(learningCategory);
                }
            }
            else if (learningCategory != null) {
                setupLearningPreferences();
            }

            /*
             * Listeners for list preference entries
             */
            ListPreference lp = (ListPreference) findPreference(Preferences.KEY_TIME_WITHOUT_DETECTION);
            lp.setOnPreferenceChangeListener(new ListPreferenceUpdate(lp));

            lp = (ListPreference) findPreference(Preferences.KEY_UI_ELEMENTS_SIZE);
            lp.setOnPreferenceChangeListener(new ListPreferenceUpdate(lp));
        }

        @Override
        public void onResume() {
            super.onResume();
            if (mLearnedScreens != null || mLearnedInteractions != null) {
                updateLearningSummaries();
            }
        }

        /**
         * Wire the intelligent learning category. The category is not present in slave
         * mode, so this is only called in the main (accessibility service) settings.
         */
        private void setupLearningPreferences() {
            LearningManager.get().attach(getActivity().getApplicationContext());

            mLearnedScreens = findPreference("learning_screens");
            mLearnedInteractions = findPreference("learning_interactions");

            updateLearningSummaries();

            Preference reset = findPreference("learning_reset");
            if (reset != null) {
                reset.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        showResetLearnedDataDialog();
                        return true;
                    }
                });
            }

            /*
             * Only the summaries need to follow the toggle: the learning manager listens
             * to the preference itself, so enabling/disabling takes effect immediately.
             */
            Preference enable = findPreference(Preferences.KEY_LEARNING_ENABLED);
            if (enable != null) {
                enable.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
                    @Override
                    public boolean onPreferenceChange(Preference preference, Object newValue) {
                        boolean enabled = Boolean.TRUE.equals(newValue);
                        preference.setSummary(enabled
                                ? R.string.settings_learning_enable_summary
                                : R.string.settings_learning_enable_summary_off);
                        return true;
                    }
                });
            }
        }

        private void updateLearningSummaries() {
            LearningManager learningManager = LearningManager.get();

            if (mLearnedScreens != null) {
                mLearnedScreens.setSummary(getString(
                        R.string.settings_learning_screens_summary,
                        learningManager.getLearnedScreenCount()));
            }

            if (mLearnedInteractions != null) {
                mLearnedInteractions.setSummary(getString(
                        R.string.settings_learning_interactions_summary,
                        learningManager.getRecordedInteractionCount()));
            }
        }

        private void showResetLearnedDataDialog() {
            new AlertDialog.Builder(getActivity())
                    .setTitle(R.string.learning_reset_title)
                    .setMessage(R.string.learning_reset_message)
                    .setNegativeButton(R.string.learning_reset_cancel, null)
                    .setPositiveButton(R.string.learning_reset_confirm,
                            new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            boolean success = LearningManager.get().resetLearnedData();
                            updateLearningSummaries();

                            Toast.makeText(getActivity(),
                                    success ? R.string.learning_reset_done
                                            : R.string.learning_reset_failed,
                                    Toast.LENGTH_LONG).show();
                        }
                    })
                    .show();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        /*
         * Check is started for slave mode
         */
        String value= getIntent().getDataString();
        boolean slaveMode= value!= null && value.compareTo("slave_mode")== 0;
        Bundle extras= getIntent().getExtras();
        if (extras!= null) {
            slaveMode= slaveMode || extras.getBoolean("slave_mode", false);
        }

        // Display the fragment as the main content.
        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, SettingsFragment.newInstance(slaveMode))
                .commit();

        if (slaveMode) {
            setTitle(getResources().getText(R.string.slave_settings_mouse_preferences));
        }
    }
}
