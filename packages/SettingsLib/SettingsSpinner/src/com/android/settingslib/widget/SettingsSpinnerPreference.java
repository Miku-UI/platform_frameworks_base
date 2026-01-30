/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.settingslib.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.widget.AdapterView;
import android.widget.Spinner;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.Preference.OnPreferenceClickListener;
import androidx.preference.PreferenceViewHolder;

import com.android.settingslib.widget.spinner.R;

/**
 * This preference uses Spinner & SettingsSpinnerAdapter which provide default layouts for
 * both view and drop down view of the Spinner.
 */
public class SettingsSpinnerPreference extends Preference
        implements OnPreferenceClickListener, GroupSectionDividerMixin {

    private SettingsSpinnerAdapter mAdapter;
    private AdapterView.OnItemSelectedListener mListener;
    private int mPosition;
    private boolean mShouldPerformClick;
    private int mOffset;
    private CharSequence mContentDescription;

    /**
     * Perform inflation from XML and apply a class-specific base style.
     *
     * @param context The {@link Context} this is associated with, through which it can access the
     *     current theme, resources, {@link SharedPreferences}, etc.
     * @param attrs The attributes of the XML tag that is inflating the preference
     * @param defStyle An attribute in the current theme that contains a reference to a style
     *     resource that supplies default values for the view. Can be 0 to not look for defaults.
     */
    public SettingsSpinnerPreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        initAttributes(context, attrs, defStyle);
        setOnPreferenceClickListener(this);
    }

    /**
     * Perform inflation from XML and apply a class-specific base style.
     *
     * @param context The {@link Context} this is associated with, through which it can access the
     *     current theme, resources, {@link SharedPreferences}, etc.
     * @param attrs The attributes of the XML tag that is inflating the preference
     */
    public SettingsSpinnerPreference(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        initAttributes(context, attrs, 0);
        setOnPreferenceClickListener(this);
    }

    /**
     * Constructor to create a preference.
     *
     * @param context The Context this is associated with.
     */
    public SettingsSpinnerPreference(@NonNull Context context) {
        this(context, null);
        initAttributes(context, null, 0);
    }

    public enum Style {
        NORMAL,
        LARGE,
        FULL_WIDTH,
        OUTLINED,
        LARGE_OUTLINED,
        FULL_OUTLINED,
    }

    private void initAttributes(
            @NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        final TypedArray a = context.obtainStyledAttributes(
                attrs, R.styleable.SettingsSpinnerPreference, defStyleAttr, 0);
        int style = a.getInteger(R.styleable.SettingsSpinnerPreference_style, 0);
        int layoutRes = switch (style) {
            case 2 -> R.layout.settings_expressive_spinner_preference_full;
            case 3 -> R.layout.settings_expressive_spinner_preference_outlined;
            case 4 -> R.layout.settings_expressive_spinner_preference_outlined;
            case 5 -> R.layout.settings_expressive_spinner_preference_full_outlined;
            default -> R.layout.settings_spinner_preference;
        };
        setLayoutResource(layoutRes);

        int size = switch (style) {
            case 1, 4 -> R.dimen.settingslib_spinner_dropdown_vertical_offset_large;
            case 2, 5 -> R.dimen.settingslib_spinner_dropdown_vertical_offset_full;
            default -> R.dimen.settingslib_spinner_dropdown_vertical_offset_normal;
        };
        mOffset =  context.getResources().getDimensionPixelSize(size);

        a.recycle();
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        mShouldPerformClick = true;
        notifyChanged();
        return true;
    }

    /** Sets adapter of the spinner. */
    public <T extends SettingsSpinnerAdapter> void setAdapter(T adapter) {
        mAdapter = adapter;
        notifyChanged();
    }

    /** Sets item selection listener of the spinner. */
    public void setOnItemSelectedListener(AdapterView.OnItemSelectedListener listener) {
        mListener = listener;
    }

    /** Gets selected item of the spinner. */
    public Object getSelectedItem() {
        return mAdapter == null ? null : mAdapter.getItem(mPosition);
    }

    /** Gets selection position of the spinner */
    public void setSelection(int position) {
        if (mPosition == position) {
            return;
        }
        mPosition = position;
        notifyChanged();
    }

    /** Sets the content description of the spinner. */
    public void setContentDescription(@Nullable CharSequence contentDescription) {
        mContentDescription = contentDescription;
        notifyChanged();
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        final Spinner spinner = (Spinner) holder.findViewById(R.id.spinner);
        if (spinner == null) {
            return;
        }
        if (mAdapter != null) {
            mAdapter.setSelectedPosition(mPosition);
        }
        spinner.setAdapter(mAdapter);
        spinner.setSelection(mPosition);
        spinner.setOnItemSelectedListener(mOnSelectedListener);
        spinner.setLongClickable(false);
        spinner.setDropDownVerticalOffset(mOffset);
        spinner.setAccessibilityDelegate(
                new View.AccessibilityDelegate() {
                    @Override
                    public void sendAccessibilityEvent(View host, int eventType) {
                        if (eventType == AccessibilityEvent.TYPE_VIEW_SELECTED) {
                            // Ignore the INTERRUPT events TYPE_VIEW_SELECTED or Talkback will speak
                            // for it while fragment updating.
                            return;
                        }
                        super.sendAccessibilityEvent(host, eventType);
                    }
                });
        if (mShouldPerformClick) {
            mShouldPerformClick = false;
            // To show dropdown view.
            spinner.performClick();
        }
        if (!TextUtils.isEmpty(mContentDescription)) {
            spinner.setContentDescription(mContentDescription);
        }
    }

    private final AdapterView.OnItemSelectedListener mOnSelectedListener =
            new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(
                        AdapterView<?> parent, View view, int position, long id) {
                    if (mPosition == position) return;
                    mPosition = position;
                    mAdapter.setSelectedPosition(mPosition);
                    if (mListener != null) {
                        mListener.onItemSelected(parent, view, position, id);
                    }
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                    if (mListener != null) {
                        mListener.onNothingSelected(parent);
                    }
                }
            };
}
