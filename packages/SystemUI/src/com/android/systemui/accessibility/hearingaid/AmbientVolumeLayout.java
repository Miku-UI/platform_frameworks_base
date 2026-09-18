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

import static com.android.settingslib.bluetooth.HearingAidInfo.DeviceSide.SIDE_LEFT;
import static com.android.settingslib.bluetooth.HearingAidInfo.DeviceSide.SIDE_RIGHT;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.settingslib.bluetooth.hearingdevices.ui.AmbientVolumeUi;
import com.android.systemui.res.R;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.primitives.Ints;

import java.util.Map;
import java.util.Set;

/**
 * A view of ambient volume controls.
 *
 * <p> It consists of a header with an expand icon and volume sliders for unified control and
 * separated control for devices in the same set. Toggle the expand icon will make the UI switch
 * between unified and separated control.
 */
public class AmbientVolumeLayout extends LinearLayout implements AmbientVolumeUi {

    @Nullable
    private AmbientVolumeUiListener mListener;
    private ImageView mExpandIcon;
    private ImageView mVolumeIcon;
    private final BiMap<Integer, AmbientVolumeSlider> mSideToSliderMap = HashBiMap.create();
    private boolean mExpandable = true;
    private boolean mExpanded = false;

    private HearingDevicesUiEventLogger mUiEventLogger;
    private int mLaunchSourceId;

    private final AmbientVolumeSlider.OnChangeListener mSliderOnChangeListener =
            (slider, value) -> {
                final Integer side = mSideToSliderMap.inverse().get(slider);
                if (side != null) {
                    logMetrics(side == SIDE_UNIFIED
                            ? HearingDevicesUiEvent.HEARING_DEVICES_AMBIENT_CHANGE_UNIFIED
                            : HearingDevicesUiEvent.HEARING_DEVICES_AMBIENT_CHANGE_SEPARATED);
                    if (mListener != null) {
                        mListener.onSliderValueChange(side, value);
                    }
                }
            };

    public AmbientVolumeLayout(@Nullable Context context) {
        this(context, /* attrs= */ null);
    }

    public AmbientVolumeLayout(@Nullable Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, /* defStyleAttr= */ 0);
    }

    public AmbientVolumeLayout(@Nullable Context context, @Nullable AttributeSet attrs,
            int defStyleAttr) {
        this(context, attrs, defStyleAttr, /* defStyleRes= */ 0);
    }

    public AmbientVolumeLayout(@Nullable Context context, @Nullable AttributeSet attrs,
            int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        inflate(context, R.layout.hearing_device_ambient_volume_layout, /* root= */ this);
        init();
    }

    private void init() {
        mVolumeIcon = requireViewById(R.id.ambient_volume_icon);
        mVolumeIcon.setImageResource(com.android.settingslib.R.drawable.ic_ambient_volume);
        updateVolumeIcon();

        mExpandIcon = requireViewById(R.id.ambient_expand_icon);
        mExpandIcon.setOnClickListener(v -> {
            if (!isControlExpandable()) {
                return;
            }
            setControlExpanded(!mExpanded);
            logMetrics(mExpanded
                    ? HearingDevicesUiEvent.HEARING_DEVICES_AMBIENT_EXPAND_CONTROLS
                    : HearingDevicesUiEvent.HEARING_DEVICES_AMBIENT_COLLAPSE_CONTROLS);
            if (mListener != null) {
                mListener.onExpandIconClick();
            }
        });
        updateExpandIcon();
    }

    @Override
    public void setVisible(boolean visible) {
        setVisibility(visible ? VISIBLE : GONE);
    }

    @Override
    public void setControlExpandable(boolean expandable) {
        if (mExpandable != expandable) {
            mExpandable = expandable;
            if (!mExpandable) {
                setControlExpanded(false);
            }
            updateExpandIcon();
        }
    }

    @Override
    public boolean isControlExpandable() {
        return mExpandable;
    }

    @Override
    public void setControlExpanded(boolean expanded) {
        if (mExpanded != expanded) {
            mExpanded = expanded;
            updateExpandIcon();
            updateLayout();
        }
    }

    @Override
    public boolean isControlExpanded() {
        return mExpanded;
    }

    @Override
    public void setSliderMuteState(int side, int muteState) {
        AmbientVolumeSlider slider = mSideToSliderMap.get(side);
        if (slider != null) {
            slider.setMuteState(muteState);
            updateMuteStateFromSide(side);
        }
    }

    @Override
    public int getSliderMuteState(int side) {
        AmbientVolumeSlider slider = mSideToSliderMap.get(side);
        return slider == null ? MUTE_DISABLED : slider.getMuteState();
    }

    @Override
    public void setListener(@Nullable AmbientVolumeUiListener listener) {
        mListener = listener;
    }

    @Override
    public void setupSliders(@NonNull Set<Integer> sides) {
        sides.forEach(this::createSlider);
        createSlider(SIDE_UNIFIED);

        LinearLayout controlContainer = requireViewById(R.id.ambient_control_container);
        controlContainer.removeAllViews();
        if (!mSideToSliderMap.isEmpty()) {
            for (int side : VALID_SIDES) {
                final AmbientVolumeSlider slider = mSideToSliderMap.get(side);
                if (slider != null) {
                    controlContainer.addView(slider);
                }
            }
        }
        updateLayout();
    }

    @Override
    public void setSliderValue(int side, int value) {
        AmbientVolumeSlider slider = mSideToSliderMap.get(side);
        if (slider != null && slider.getValue() != value && slider.getMin() <= value
                && slider.getMax() >= value) {
            slider.setValue(value);
            updateVolumeIcon();
        }
    }

    @Override
    public void setSliderEnabled(int side, boolean enabled) {
        AmbientVolumeSlider slider = mSideToSliderMap.get(side);
        if (slider != null) {
            slider.setEnabled(enabled);
            updateVolumeIcon();
        }
    }

    @Override
    public void setSliderRange(int side, int min, int max) {
        AmbientVolumeSlider slider = mSideToSliderMap.get(side);
        if (slider != null) {
            slider.setMin(min);
            slider.setMax(max);
        }
    }

    void setUiEventLogger(HearingDevicesUiEventLogger uiEventLogger, int launchSourceId) {
        mUiEventLogger = uiEventLogger;
        mLaunchSourceId = launchSourceId;
    }

    private void updateLayout() {
        mSideToSliderMap.forEach((side, slider) -> {
            if (side == SIDE_UNIFIED) {
                slider.setVisibility(mExpanded ? GONE : VISIBLE);
            } else {
                slider.setVisibility(mExpanded ? VISIBLE : GONE);
            }
        });
        updateVolumeIcon();
    }

    private void updateVolumeIcon() {
        int leftLevel, rightLevel;
        if (isControlExpanded()) {
            final AmbientVolumeSlider leftSlider = mSideToSliderMap.get(SIDE_LEFT);
            final AmbientVolumeSlider rightSlider = mSideToSliderMap.get(SIDE_RIGHT);
            leftLevel = leftSlider == null ? 0 : leftSlider.getVolumeLevel();
            rightLevel = rightSlider == null ? 0 : rightSlider.getVolumeLevel();
        } else {
            final AmbientVolumeSlider unifiedSlider = mSideToSliderMap.get(SIDE_UNIFIED);
            final int unifiedLevel = unifiedSlider == null ? 0 : unifiedSlider.getVolumeLevel();
            leftLevel = unifiedLevel;
            rightLevel = unifiedLevel;
        }
        int volumeLevel = Ints.constrainToRange(
                leftLevel * AMBIENT_VOLUME_LEVEL_NUMBER + rightLevel,
                AMBIENT_VOLUME_LEVEL_MIN,
                AMBIENT_VOLUME_LEVEL_MAX);
        mVolumeIcon.setImageLevel(volumeLevel);
    }

    private void updateExpandIcon() {
        mExpandIcon.setVisibility(isControlExpandable() ? VISIBLE : GONE);
        mExpandIcon.setRotation(isControlExpanded() ? ROTATION_EXPANDED : ROTATION_COLLAPSED);
        if (isControlExpandable()) {
            final int stringRes = isControlExpanded()
                    ? R.string.hearing_devices_ambient_collapse_controls
                    : R.string.hearing_devices_ambient_expand_controls;
            mExpandIcon.setContentDescription(mContext.getString(stringRes));
        } else {
            mExpandIcon.setContentDescription(null);
        }
    }

    private void updateMuteStateFromSide(int side) {
        if (side == SIDE_UNIFIED) {
            // propagate the mute state to all other sliders
            mSideToSliderMap.forEach((entrySide, entrySlider) -> {
                if (entrySide != SIDE_UNIFIED) {
                    entrySlider.setMuteState(getSliderMuteState(SIDE_UNIFIED));
                }
            });
        } else {
            AmbientVolumeSlider unifiedSlider = mSideToSliderMap.get(SIDE_UNIFIED);
            if (unifiedSlider != null) {
                boolean allSideDisabled = mSideToSliderMap.entrySet().stream()
                        .filter(entry -> entry.getKey() != SIDE_UNIFIED)
                        .allMatch(entry -> entry.getValue().getMuteState() == MUTE_DISABLED);
                boolean allSideMuted = mSideToSliderMap.entrySet().stream()
                        .filter(entry -> entry.getKey() != SIDE_UNIFIED)
                        .allMatch(entry -> entry.getValue().getMuteState() == MUTE_MUTED);
                if (allSideDisabled) {
                    unifiedSlider.setMuteState(MUTE_DISABLED);
                } else if (allSideMuted) {
                    unifiedSlider.setMuteState(MUTE_MUTED);
                } else {
                    unifiedSlider.setMuteState(MUTE_NOT_MUTED);
                }
            }
        }
        updateVolumeIcon();
    }

    private void createSlider(int side) {
        if (mSideToSliderMap.containsKey(side)) {
            return;
        }
        int titleResId = 0;
        int contentResId;
        if (side == SIDE_LEFT) {
            titleResId = R.string.hearing_devices_ambient_control_left;
            contentResId = R.string.hearing_devices_ambient_control_left_description;
        } else if (side == SIDE_RIGHT) {
            titleResId = R.string.hearing_devices_ambient_control_right;
            contentResId = R.string.hearing_devices_ambient_control_right_description;
        } else {
            contentResId = R.string.hearing_devices_ambient_control_description;
        }
        String title = titleResId == 0 ? null : mContext.getString(titleResId);
        String content = contentResId == 0 ? null : mContext.getString(contentResId);

        AmbientVolumeSlider slider = new AmbientVolumeSlider(mContext);
        slider.setTitle(title);
        slider.setContentDescription(title);
        slider.setSliderContentDescription(content);
        slider.addOnChangeListener(mSliderOnChangeListener);
        slider.setOnMuteIconClickListener((v) -> {
            updateMuteStateFromSide(side);

            boolean muted = slider.getMuteState() == MUTE_MUTED;
            logMetrics(muted ? HearingDevicesUiEvent.HEARING_DEVICES_AMBIENT_MUTE
                    : HearingDevicesUiEvent.HEARING_DEVICES_AMBIENT_UNMUTE);
            if (mListener != null) {
                mListener.onSliderMuteChange(side, muted);
            }
        });
        mSideToSliderMap.put(side, slider);
    }

    private void logMetrics(HearingDevicesUiEvent event) {
        if (mUiEventLogger != null) {
            mUiEventLogger.log(event, mLaunchSourceId);
        }
    }

    @VisibleForTesting
    ImageView getVolumeIcon() {
        return mVolumeIcon;
    }

    @VisibleForTesting
    ImageView getExpandIcon() {
        return mExpandIcon;
    }

    @VisibleForTesting
    Map<Integer, AmbientVolumeSlider> getSliders() {
        return mSideToSliderMap;
    }
}
