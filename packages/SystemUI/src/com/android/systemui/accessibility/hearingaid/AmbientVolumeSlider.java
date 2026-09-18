/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.systemui.accessibility.hearingaid;

import static android.bluetooth.AudioInputControl.MUTE_DISABLED;
import static android.bluetooth.AudioInputControl.MUTE_MUTED;
import static android.bluetooth.AudioInputControl.MUTE_NOT_MUTED;

import static com.android.settingslib.bluetooth.hearingdevices.ui.AmbientVolumeUi.AMBIENT_VOLUME_LEVEL_NUMBER;

import android.content.Context;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.systemui.res.R;

import com.google.android.material.slider.Slider;

import java.util.ArrayList;
import java.util.List;

/**
 * A view of ambient volume slider.
 * <p> It consists by a title {@link TextView} with a volume control {@link Slider}.
 */
public class AmbientVolumeSlider extends LinearLayout {

    private final TextView mTitle;
    private final Slider mSlider;
    private final ViewGroup mMuteIconFrame;
    private final ImageView mMuteIcon;
    private final List<OnChangeListener> mChangeListeners = new ArrayList<>();

    private boolean mTrackingTouch = false;
    private int mMuteState = MUTE_NOT_MUTED;


    public AmbientVolumeSlider(@Nullable Context context) {
        this(context, /* attrs= */ null);
    }

    public AmbientVolumeSlider(@Nullable Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, /* defStyleAttr= */ 0);
    }

    public AmbientVolumeSlider(@Nullable Context context, @Nullable AttributeSet attrs,
            int defStyleAttr) {
        this(context, attrs, defStyleAttr, /* defStyleRes= */ 0);
    }

    public AmbientVolumeSlider(@Nullable Context context, @Nullable AttributeSet attrs,
            int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);

        inflate(context, R.layout.hearing_device_ambient_volume_slider, /* root= */ this);
        mTitle = requireViewById(R.id.ambient_volume_slider_title);
        mSlider = requireViewById(R.id.ambient_volume_slider);
        Slider.OnSliderTouchListener sliderTouchListener = new Slider.OnSliderTouchListener() {
            @Override
            public void onStartTrackingTouch(@NonNull Slider slider) {
                mTrackingTouch = true;
            }

            @Override
            public void onStopTrackingTouch(@NonNull Slider slider) {
                mTrackingTouch = false;
                final int value = Math.round(slider.getValue());
                for (OnChangeListener listener : mChangeListeners) {
                    listener.onValueChange(AmbientVolumeSlider.this, value);
                }
            }
        };
        mSlider.addOnSliderTouchListener(sliderTouchListener);
        Slider.OnChangeListener sliderChangeListener = new Slider.OnChangeListener() {
            @Override
            public void onValueChange(@NonNull Slider slider, float value, boolean fromUser) {
                if (fromUser && !mTrackingTouch) {
                    final int roundedValue = Math.round(value);
                    for (OnChangeListener listener : mChangeListeners) {
                        listener.onValueChange(AmbientVolumeSlider.this, roundedValue);
                    }
                }
            }
        };
        mSlider.addOnChangeListener(sliderChangeListener);

        setFocusable(false);
        setClickable(false);
        mSlider.setFocusable(false);
        mSlider.setClickable(false);

        mMuteIconFrame = requireViewById(R.id.mute_icon_frame);
        mMuteIcon = requireViewById(R.id.mute_icon);
    }

    /**
     * Sets title for the ambient volume slider.
     * <p> If text is null or empty, then {@link TextView} is hidden.
     */
    public void setTitle(@Nullable String text) {
        mTitle.setText(text);
        mTitle.setVisibility(TextUtils.isEmpty(text) ? GONE : VISIBLE);
    }

    /** Gets title for the ambient volume slider. */
    public CharSequence getTitle() {
        return mTitle.getText();
    }

    /**
     * Adds the callback to the ambient volume slider to get notified when the value is changed by
     * user.
     * <p> Note: The {@link OnChangeListener#onValueChange(AmbientVolumeSlider, int)} will be
     * called when user's finger take off from the slider.
     */
    public void addOnChangeListener(@Nullable OnChangeListener listener) {
        if (listener == null) {
            return;
        }
        mChangeListeners.add(listener);
    }

    /** Sets max value to the ambient volume slider. */
    public void setMax(float max) {
        mSlider.setValueTo(max);
    }

    /** Gets max value from the ambient volume slider. */
    public float getMax() {
        return mSlider.getValueTo();
    }

    /** Sets min value to the ambient volume slider. */
    public void setMin(float min) {
        mSlider.setValueFrom(min);
    }

    /** Gets min value from the ambient volume slider. */
    public float getMin() {
        return mSlider.getValueFrom();
    }

    /** Sets value to the ambient volume slider. */
    public void setValue(float value) {
        mSlider.setValue(value);
    }

    /** Gets value from the ambient volume slider. */
    public float getValue() {
        return mSlider.getValue();
    }

    /** Sets the enable state to the ambient volume slider. */
    public void setEnabled(boolean enabled) {
        mSlider.setEnabled(enabled);
        if (!enabled) {
            mSlider.setValue(mSlider.getValueFrom());
        }
    }

    /** Gets the enable state of the ambient volume slider. */
    public boolean isEnabled() {
        return mSlider.isEnabled();
    }

    /**
     * Sets a listener for the mute icon.
     * <p>
     * The listener is invoked *after* the internal mute state is automatically toggled
     * between muted and unmuted. Clicks are ignored if the state is
     * {@link android.bluetooth.AudioInputControl#MUTE_DISABLED}.
     *
     * @param listener The listener to call on a mute icon click.
     */
    public void setOnMuteIconClickListener(View.OnClickListener listener) {
        mMuteIconFrame.setOnClickListener(v -> {
            if (mMuteState == MUTE_DISABLED) {
                return;
            }
            int newMuteState =  mMuteState == MUTE_MUTED ? MUTE_NOT_MUTED : MUTE_MUTED;
            setMuteState(newMuteState);
            if (listener != null) {
                listener.onClick(v);
            }
        });
    }

    /**
     * Sets the mute state and updates the UI accordingly.
     * <p>
     * This controls the mute icon's visibility, image, and content description. When the state
     * is set to {@code MUTE_MUTED}, the slider value is also reset to its minimum.
     *
     * @param muteState The current mute state, which will be one of
     *                  {@link android.bluetooth.AudioInputControl#MUTE_DISABLED},
     *                  {@link android.bluetooth.AudioInputControl#MUTE_MUTED}, or
     *                  {@link android.bluetooth.AudioInputControl#MUTE_NOT_MUTED}.
     */
    public void setMuteState(int muteState) {
        mMuteState = muteState;
        mMuteIconFrame.setVisibility(muteState == MUTE_DISABLED ? GONE : VISIBLE);
        if (muteState == MUTE_MUTED) {
            mSlider.setValue(mSlider.getValueFrom());
            mMuteIcon.setImageResource(com.android.settingslib.R.drawable.ic_ambient_mute);
            mMuteIconFrame.setContentDescription(
                    mContext.getString(R.string.hearing_devices_ambient_unmute));
        } else if (muteState == MUTE_NOT_MUTED) {
            mMuteIcon.setImageResource(com.android.settingslib.R.drawable.ic_ambient_unmute);
            mMuteIconFrame.setContentDescription(
                    mContext.getString(R.string.hearing_devices_ambient_mute));
        }
    }

    /**
     * Gets the current mute state of the control.
     *
     * @return The current mute state, which will be one of
     *         {@link android.bluetooth.AudioInputControl#MUTE_DISABLED},
     *         {@link android.bluetooth.AudioInputControl#MUTE_MUTED}, or
     *         {@link android.bluetooth.AudioInputControl#MUTE_NOT_MUTED}.
     * @see #setMuteState(int)
     */
    public int getMuteState() {
        return mMuteState;
    }

    /**
     * Gets the volume value of the ambient volume slider.
     * <p> The volume level is divided into 5 levels:
     * Level 0 corresponds to the minimum volume value. The range between the minimum and maximum
     * volume is divided into 4 equal intervals, represented by levels 1 to 4.
     */
    public int getVolumeLevel() {
        if (!mSlider.isEnabled() || mMuteState != MUTE_NOT_MUTED) {
            return 0;
        }
        final double min = mSlider.getValueFrom();
        final double max = mSlider.getValueTo();
        final double levelGap = (max - min) / (double) (AMBIENT_VOLUME_LEVEL_NUMBER - 1);
        final double value = mSlider.getValue();
        return (int) Math.ceil((value - min) / levelGap);
    }

    /** Sets the content description to the ambient volume slider. */
    public void setSliderContentDescription(CharSequence contentDescription) {
        if (mSlider != null) {
            mSlider.setContentDescription(contentDescription);
        }
    }

    /** Interface definition for a callback invoked when a slider's value is changed. */
    public interface OnChangeListener {
        /** Called when the finger is take off from the slider. */
        void onValueChange(@NonNull AmbientVolumeSlider slider, int value);
    }
}
