/*
 * Copyright 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.display.brightness.clamper;

import static android.os.PowerManager.BRIGHTNESS_MAX;
import static android.os.PowerManager.BRIGHTNESS_MIN;

import android.annotation.FloatRange;
import android.hardware.display.BrightnessInfo;
import android.hardware.display.DisplayManagerInternal;
import android.os.Handler;
import android.util.ArrayMap;
import android.util.Log;

import com.android.server.display.DisplayBrightnessState;
import com.android.server.display.brightness.BrightnessReason;

import java.io.PrintWriter;
import java.util.Map;

class ExternalBrightnessModifier
        implements BrightnessStateModifier, BrightnessClamperController.StatefulModifier {

    private static final String TAG = ExternalBrightnessModifier.class.getSimpleName();
    private final Handler mHandler;
    private final Map<Integer, Float> mBrightnessCapMap = new ArrayMap<>();
    private final BrightnessClamperController.ClamperChangeListener mChangeListener;

    private float mBrightnessCap = BRIGHTNESS_MAX;

    @BrightnessInfo.BrightnessMaxReason
    private int mBrightnessCapReason = BrightnessInfo.BRIGHTNESS_MAX_REASON_NONE;

    private boolean mApplied = false;
    private boolean mStopped = false;

    ExternalBrightnessModifier(
            Handler handler, BrightnessClamperController.ClamperChangeListener listener) {
        mHandler = handler;
        mChangeListener = listener;
    }

    // region BrightnessStateModifier
    @Override
    public void apply(
            DisplayManagerInternal.DisplayPowerRequest request,
            DisplayBrightnessState.Builder stateBuilder) {
        if (shouldApplyCap() && stateBuilder.getMaxBrightness() > mBrightnessCap) {
            stateBuilder.setMaxBrightness(mBrightnessCap);
            stateBuilder.setBrightness(Math.min(stateBuilder.getBrightness(), mBrightnessCap));
            stateBuilder.setBrightnessMaxReason(mBrightnessCapReason);
            stateBuilder.getBrightnessReason().addModifier(BrightnessReason.MODIFIER_THROTTLED);
            // set fast change only when modifier is activated.
            // this will allow auto brightness to apply slow change even when modifier is active
            if (!mApplied) {
                stateBuilder.setIsSlowChange(false);
            }
            mApplied = true;
        } else {
            mApplied = false;
        }
    }

    @Override
    public void stop() {
        mStopped = true;
    }

    @Override
    public void dump(PrintWriter writer) {
        writer.println("BrightnessExternalModifier:");
        StringBuilder map = new StringBuilder("[");
        for (int reason : mBrightnessCapMap.keySet()) {
            map.append("{")
                    .append(reason)
                    .append("}:")
                    .append(mBrightnessCapMap.get(reason))
                    .append(",");
        }
        map.append("]");
        writer.println("  mBrightnessCapMap: " + map);
        writer.println("  mBrightnessCap: " + mBrightnessCap);
        writer.println("  mBrightnessCapReason: " + mBrightnessCapReason);
        writer.println("  mApplied: " + mApplied);
        writer.println("  mStopped: " + mStopped);
    }

    @Override
    public boolean shouldListenToLightSensor() {
        return false;
    }

    @Override
    public void setAmbientLux(float lux) {
        // noop
    }

    // endregion

    // region StatefulModifier
    @Override
    public void applyStateChange(
            BrightnessClamperController.ModifiersAggregatedState aggregatedState) {
        if (shouldApplyCap() && aggregatedState.mMaxBrightness > mBrightnessCap) {
            aggregatedState.mMaxBrightness = mBrightnessCap;
            aggregatedState.mMaxBrightnessReason = mBrightnessCapReason;
        }
    }

    // endregion

    public void setBrightnessCap(
            @FloatRange(from = 0f, to = 1f) float cap,
            @BrightnessInfo.BrightnessMaxReason int reason) {
        if (cap < BRIGHTNESS_MIN || cap > BRIGHTNESS_MAX) {
            Log.e(TAG, "Bad value for cap: " + cap, new Exception());
            return;
        }

        mHandler.post(
                () -> {
                    float lastCap = mBrightnessCap;
                    int lastReason = mBrightnessCapReason;
                    mBrightnessCapMap.put(reason, cap);
                    reconfigureBrightnessCap();
                    if (lastCap != mBrightnessCap || lastReason != mBrightnessCapReason) {
                        mChangeListener.onChanged();
                    }
                });
    }

    private void reconfigureBrightnessCap() {
        float newCap = BRIGHTNESS_MAX;
        int newReason = BrightnessInfo.BRIGHTNESS_MAX_REASON_NONE;
        for (int reason : mBrightnessCapMap.keySet()) {
            float cap = mBrightnessCapMap.get(reason);
            if (cap < newCap) {
                newReason = reason;
                newCap = cap;
            }
        }

        mBrightnessCap = newCap;
        mBrightnessCapReason = newReason;
    }

    private boolean shouldApplyCap() {
        // mBrightnessCap is always set to a valid value so here we should only check for whether it
        // is equal to the BRIGHTNESS_MAX or not.
        return !mStopped && mBrightnessCap != BRIGHTNESS_MAX;
    }
}
