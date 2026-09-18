/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.server.display.brightness.clamper;

import static com.android.server.display.BrightnessMappingStrategy.INVALID_LUX;

import android.hardware.display.BrightnessInfo;
import android.hardware.display.DisplayManagerInternal;
import android.os.Handler;
import android.os.PowerManager;

import androidx.annotation.Nullable;

import com.android.internal.display.BrightnessSynchronizer;
import com.android.server.display.AutomaticBrightnessController;
import com.android.server.display.DisplayBrightnessState;
import com.android.server.display.DisplayDeviceConfig;
import com.android.server.display.brightness.BrightnessReason;

import java.io.PrintWriter;
import java.util.Map;

/**
 * A {@link BrightnessStateModifier} that clamps the maximum brightness based on the ambient lux
 * level and whether High Brightness Mode (HBM) is allowed.
 *
 * This modifier uses brightness limit maps defined in {@link DisplayDeviceConfig} to determine
 * the maximum allowed brightness. The limits can differ based on whether adaptive brightness
 * is enabled.
 */
public class BrightnessMaxLuxModifier implements BrightnessStateModifier,
        BrightnessClamperController.DisplayDeviceDataListener,
        BrightnessClamperController.StatefulModifier {
    private Map<DisplayDeviceConfig.BrightnessLimitMapType, Map<Float, Float>> mMaxBrightnessLimits;
    private float mAmbientLux = INVALID_LUX;
    private boolean mAutoBrightnessEnabled;
    private boolean mIsHbmAllowed;
    private float mMaxBrightness = PowerManager.BRIGHTNESS_MAX;
    private final Handler mHandler;
    private final BrightnessClamperController.ClamperChangeListener mChangeListener;

    public BrightnessMaxLuxModifier(Handler handler,
            BrightnessClamperController.ClamperChangeListener listener,
            BrightnessClamperController.DisplayDeviceData data) {
        mHandler = handler;
        mChangeListener = listener;
        mMaxBrightnessLimits = data.getMaxBrightnessLimits();
        mHandler.post(this::recalculateMaxBrightness);
    }

    @Override
    public void apply(DisplayManagerInternal.DisplayPowerRequest request,
            DisplayBrightnessState.Builder stateBuilder) {
        if (stateBuilder.getMaxBrightness() > mMaxBrightness) {
            stateBuilder.setMaxBrightness(mMaxBrightness);
            stateBuilder.setBrightness(Math.min(stateBuilder.getBrightness(), mMaxBrightness));
            stateBuilder.setBrightnessMaxReason(BrightnessInfo.BRIGHTNESS_MAX_REASON_LUX);
            if (BrightnessSynchronizer.floatEquals(stateBuilder.getBrightness(), mMaxBrightness)) {
                stateBuilder.getBrightnessReason().addModifier(BrightnessReason.MODIFIER_MAX_LUX);
            }
        }
    }

    @Override
    public void applyStateChange(
            BrightnessClamperController.ModifiersAggregatedState aggregatedState) {
        if (aggregatedState.mMaxBrightness > mMaxBrightness) {
            aggregatedState.mMaxBrightness = mMaxBrightness;
            aggregatedState.mMaxBrightnessReason = BrightnessInfo.BRIGHTNESS_MAX_REASON_LUX;
        }
    }

    @Override
    public void stop() {}

    @Override
    public boolean shouldListenToLightSensor() {
        Map<Float, Float> maxBrightnessPoints = getMaxBrightnessPoints();
        return maxBrightnessPoints != null && !maxBrightnessPoints.isEmpty();
    }

    @Override
    public void setAmbientLux(float ambientLux) {
        mAmbientLux = ambientLux;
        recalculateMaxBrightness();
    }

    @Override
    public void onDisplayChanged(BrightnessClamperController.DisplayDeviceData displayData) {
        mHandler.post(() -> {
            mMaxBrightnessLimits = displayData.getMaxBrightnessLimits();
            recalculateMaxBrightness();
        });
    }

    @Override
    public void setAutoBrightnessState(int state) {
        boolean isEnabled = state == AutomaticBrightnessController.AUTO_BRIGHTNESS_ENABLED;
        if (isEnabled != mAutoBrightnessEnabled) {
            mAutoBrightnessEnabled = isEnabled;
            recalculateMaxBrightness();
        }
    }

    @Override
    public void setIsHbmAllowed(boolean isHbmAllowed) {
        if (isHbmAllowed != mIsHbmAllowed) {
            mIsHbmAllowed = isHbmAllowed;
            recalculateMaxBrightness();
        }
    }

    @Override
    public void dump(PrintWriter pw) {
        pw.println("BrightnessMaxLuxModifier:");
        pw.println("  mAutoBrightnessEnabled=" + mAutoBrightnessEnabled);
        pw.println("  mIsHbmAllowed=" + mIsHbmAllowed);
        pw.println("  mAmbientLux=" + mAmbientLux);
        pw.println("  mMaxBrightness=" + mMaxBrightness);
        pw.println("  mMaxBrightnessLimits=" + mMaxBrightnessLimits);
        pw.println("  shouldListenToLightSensor=" + shouldListenToLightSensor());
    }

    @Nullable
    private Map<Float, Float> getMaxBrightnessPoints() {
        Map<Float, Float> maxBrightnessPoints = null;
        if (mAutoBrightnessEnabled) {
            maxBrightnessPoints = mMaxBrightnessLimits.get(
                    DisplayDeviceConfig.BrightnessLimitMapType.ADAPTIVE);
        }
        if (maxBrightnessPoints == null) {
            maxBrightnessPoints = mMaxBrightnessLimits.get(
                    DisplayDeviceConfig.BrightnessLimitMapType.DEFAULT);
        }
        return maxBrightnessPoints;
    }

    private void recalculateMaxBrightness() {
        float foundAmbientBoundary = Float.MAX_VALUE;
        float foundMaxBrightness = PowerManager.BRIGHTNESS_MAX;

        if (!mIsHbmAllowed && mAmbientLux != INVALID_LUX) {
            Map<Float, Float> maxBrightnessPoints = getMaxBrightnessPoints();
            if (maxBrightnessPoints != null) {
                for (Map.Entry<Float, Float> brightnessPoint : maxBrightnessPoints.entrySet()) {
                    float ambientBoundary = brightnessPoint.getKey();
                    // find ambient lux upper boundary closest to current ambient lux
                    if (ambientBoundary > mAmbientLux && ambientBoundary < foundAmbientBoundary) {
                        foundMaxBrightness = brightnessPoint.getValue();
                        foundAmbientBoundary = ambientBoundary;
                    }
                }
            }
        }

        if (!BrightnessSynchronizer.floatEquals(mMaxBrightness, foundMaxBrightness)) {
            mMaxBrightness = foundMaxBrightness;
            mChangeListener.onChanged();
        }
    }
}
