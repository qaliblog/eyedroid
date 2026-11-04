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

package com.qali.eyedroid.util;

import android.content.Context;
import android.content.res.TypedArray;
import android.preference.DialogPreference;
import android.preference.Preference;
import android.text.InputType;
import android.util.AttributeSet;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import com.qali.eyedroid.R;

/**
 * A {@link Preference} that lets the user type an integer value within a range.
 *
 * This is the counterpart of {@link NumberPickerPreference} for settings that need
 * to be adjusted precisely (thresholds, gains, timings...).
 */
public class NumberEditPreference extends DialogPreference {
    private static final int DEFAULT_MIN_VALUE = 0;
    private static final int DEFAULT_MAX_VALUE = 100;

    private EditText mEditText;
    private int mValue;
    private int mMinValue;
    private int mMaxValue;
    private CharSequence mTitle;

    public NumberEditPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        if (attrs == null) return;

        TypedArray arr = context.obtainStyledAttributes(attrs, R.styleable.NumberEditPreference);
        mMinValue = arr.getInteger(R.styleable.NumberEditPreference_minValue, DEFAULT_MIN_VALUE);
        mMaxValue = arr.getInteger(R.styleable.NumberEditPreference_maxValue, DEFAULT_MAX_VALUE);
        arr.recycle();

        mTitle = this.getTitle();
    }

    @SuppressWarnings("unused")
    public NumberEditPreference(Context context, AttributeSet attrs) {
        this(context, attrs, android.R.attr.dialogPreferenceStyle);
    }

    @Override
    protected View onCreateDialogView() {
        View view = View.inflate(getContext(), R.layout.dialog_number_edit, null);

        mEditText = (EditText) view.findViewById(R.id.number_edit);
        mEditText.setInputType(InputType.TYPE_CLASS_NUMBER);

        TextView range = (TextView) view.findViewById(R.id.number_range);
        range.setText(mMinValue + " - " + mMaxValue);

        return view;
    }

    @Override
    protected void onBindDialogView(View view) {
        super.onBindDialogView(view);

        mEditText.setText(Integer.toString(getValue()));
        mEditText.setSelection(mEditText.getText().length());
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        if (!positiveResult) return;

        mEditText.clearFocus();

        int value;
        try {
            value = Integer.parseInt(mEditText.getText().toString().trim());
        }
        catch (NumberFormatException e) {
            // invalid input, keep the previous value
            return;
        }

        if (value < mMinValue) value = mMinValue;
        if (value > mMaxValue) value = mMaxValue;

        setValue(value);
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        return a.getInt(index, DEFAULT_MIN_VALUE);
    }

    @Override
    protected void onSetInitialValue(boolean restorePersistedValue, Object defaultValue) {
        if (restorePersistedValue) {
            setValue(getPersistedInt(DEFAULT_MIN_VALUE));
        }
        else {
            setValue((Integer) defaultValue);
        }
    }

    public void setValue(int value) {
        Preference.OnPreferenceChangeListener listener = getOnPreferenceChangeListener();
        boolean update = true;
        if (listener != null) {
            update = listener.onPreferenceChange(this, value);
        }
        if (update) {
            mValue = value;
            setTitle(mTitle + ": " + Integer.toString(value));
            persistInt(mValue);
        }
    }

    public int getValue() {
        return mValue;
    }
}
