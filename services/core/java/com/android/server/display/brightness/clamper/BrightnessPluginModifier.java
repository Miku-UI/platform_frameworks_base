/*
 * Copyright (C) 2025 The Android Open Source Project
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

import static com.android.server.display.DisplayBrightnessState.CUSTOM_ANIMATION_RATE_NOT_SET;

import android.annotation.Nullable;
import android.annotation.SuppressLint;
import android.content.Context;
import android.hardware.display.BrightnessInfo;
import android.hardware.display.DisplayManagerInternal;
import android.os.Handler;
import android.os.PowerManager;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.display.DisplayBrightnessState;
import com.android.server.display.brightness.BrightnessReason;
import com.android.server.display.feature.DisplayManagerFlags;
import com.android.server.display.plugin.PluginManager;
import com.android.server.display.plugin.PluginType;
import com.android.server.display.plugin.types.MaxBrightnessCapOverride;

import java.io.PrintWriter;
import java.util.Objects;

public class BrightnessPluginModifier implements BrightnessStateModifier,
        BrightnessClamperController.DisplayDeviceDataListener,
        BrightnessClamperController.StatefulModifier {

    private final PluginManager.PluginChangeListener<MaxBrightnessCapOverride>
            mMaxBrightnessCapOverrideListener = new PluginManager.PluginChangeListener<>() {
                @Override
                public void onChanged(@Nullable MaxBrightnessCapOverride value) {
                    mHandler.post(() -> onMaxBrightnessCapOverrideChanged(value));
                }
            };
    private final Handler mHandler;
    private final Injector mInjector;
    private final BrightnessClamperController.ClamperChangeListener mClamperChangeListener;

    // unique displayId for PluginChangeListener subscription
    private String mRegisteredUniqueDisplayId;
    private float mMaxBrightnessCap = PowerManager.BRIGHTNESS_MAX;
    @BrightnessReason.Modifier
    private int mReasonModifier = BrightnessReason.MODIFIER_NONE;
    private float mTransitionRate = CUSTOM_ANIMATION_RATE_NOT_SET;

    BrightnessPluginModifier(Handler handler, Context context, DisplayManagerFlags flags,
            PluginManager pluginManager,
            BrightnessClamperController.ClamperChangeListener clamperChangeListener,
            BrightnessClamperController.DisplayDeviceData displayData) {
        this(new Handler(handler.getLooper()), clamperChangeListener,
                new Injector(pluginManager), displayData);
    }

    @VisibleForTesting
    BrightnessPluginModifier(Handler handler,
            BrightnessClamperController.ClamperChangeListener clamperChangeListener,
            Injector injector, BrightnessClamperController.DisplayDeviceData displayData) {
        mHandler = handler;
        mClamperChangeListener = clamperChangeListener;
        mInjector = injector;
        mHandler.post(() -> onDisplayChanged(displayData));
    }

    @Override
    public void apply(DisplayManagerInternal.DisplayPowerRequest request,
            DisplayBrightnessState.Builder stateBuilder) {
        if (mMaxBrightnessCap < stateBuilder.getMaxBrightness()) {
            stateBuilder.setMaxBrightness(mMaxBrightnessCap);
            stateBuilder.setCustomAnimationRate(mTransitionRate);
            stateBuilder.setBrightnessMaxReason(BrightnessInfo.BRIGHTNESS_MAX_REASON_PLUGIN);
            stateBuilder.getBrightnessReason().addModifier(mReasonModifier);

        }
    }

    @Override
    public void dump(PrintWriter pw) {
        pw.println("BrightnessPluginModifier:");
        pw.println("  mMaxBrightnessCap=" + mMaxBrightnessCap);
        pw.println("  mReasonModifier=" + mReasonModifier);
        pw.println("  mRegisteredUniqueDisplayId=" + mRegisteredUniqueDisplayId);
    }

    @Override
    public void stop() {
        unregisterMaxBrightnessCapOverrideListener();
        mHandler.removeCallbacksAndMessages(null);
    }

    @Override
    public boolean shouldListenToLightSensor() {
        return false;
    }

    @Override
    public void setAmbientLux(float lux) {

    }

    @Override
    public void onDisplayChanged(BrightnessClamperController.DisplayDeviceData displayData) {
        registerMaxBrightnessCapOverrideListener(displayData.getUniqueDisplayId());
    }

    private void registerMaxBrightnessCapOverrideListener(String uniqueDisplayId) {
        if (Objects.equals(mRegisteredUniqueDisplayId, uniqueDisplayId)) {
            return;
        }
        unregisterMaxBrightnessCapOverrideListener();
        if (uniqueDisplayId != null) {
            mInjector.registerMaxBrightnessCapOverrideListener(uniqueDisplayId,
                    mMaxBrightnessCapOverrideListener);
            mRegisteredUniqueDisplayId = uniqueDisplayId;
        }
    }

    private void unregisterMaxBrightnessCapOverrideListener() {
        if (mRegisteredUniqueDisplayId != null) {
            mInjector.unregisterMaxBrightnessCapOverrideListener(
                    mRegisteredUniqueDisplayId, mMaxBrightnessCapOverrideListener);
            mRegisteredUniqueDisplayId = null;
            mMaxBrightnessCap = PowerManager.BRIGHTNESS_MAX;
            mTransitionRate = CUSTOM_ANIMATION_RATE_NOT_SET;
        }
    }

    private void onMaxBrightnessCapOverrideChanged(@Nullable MaxBrightnessCapOverride
            maxBrightnessCapOverride) {
        if (maxBrightnessCapOverride != null) {
            float newMaxBrightnessCap = maxBrightnessCapOverride.maxBrightnessCap();
            @BrightnessReason.Modifier
            int reasonModifier = maxBrightnessCapOverride.reasonModifier();
            if (mMaxBrightnessCap != newMaxBrightnessCap || reasonModifier != mReasonModifier) {
                mMaxBrightnessCap = newMaxBrightnessCap;
                mReasonModifier = reasonModifier;
                mTransitionRate = maxBrightnessCapOverride.customTransitionRate();
                mClamperChangeListener.onChanged();
            }
        }
    }

    @Override
    public void applyStateChange(
            BrightnessClamperController.ModifiersAggregatedState aggregatedState) {
        if (aggregatedState.mMaxBrightness > mMaxBrightnessCap) {
            aggregatedState.mMaxBrightness = mMaxBrightnessCap;
            aggregatedState.mMaxBrightnessReason = BrightnessInfo.BRIGHTNESS_MAX_REASON_PLUGIN;
        }
    }

    @SuppressLint("MissingPermission")
    static class Injector {
        private final PluginManager mPluginManager;

        Injector(PluginManager pluginManager) {
            mPluginManager = pluginManager;
        }

        void registerMaxBrightnessCapOverrideListener(String uniqueDisplayId,
                PluginManager.PluginChangeListener<MaxBrightnessCapOverride>
                        maxBrightnessCapOverrideListener) {
            mPluginManager.subscribe(PluginType.MAX_BRIGHTNESS_CAP_OVERRIDE, uniqueDisplayId,
                    maxBrightnessCapOverrideListener);
        }

        void unregisterMaxBrightnessCapOverrideListener(String uniqueDisplayId,
                PluginManager.PluginChangeListener<MaxBrightnessCapOverride>
                        maxBrightnessCapOverrideListener) {
            mPluginManager.unsubscribe(PluginType.MAX_BRIGHTNESS_CAP_OVERRIDE, uniqueDisplayId,
                    maxBrightnessCapOverrideListener);
        }
    }
}
