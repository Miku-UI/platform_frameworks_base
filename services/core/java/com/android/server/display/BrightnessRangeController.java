/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.server.display;

import android.annotation.Nullable;
import android.os.IBinder;

import com.android.internal.annotations.VisibleForTesting;

import java.io.PrintWriter;
import java.util.function.BooleanSupplier;

class BrightnessRangeController {

    private final HighBrightnessModeController mHbmController;
    private final NormalBrightnessModeController mNormalBrightnessModeController;

    private final Runnable mModeChangeCallback;

    BrightnessRangeController(HighBrightnessModeController hbmController,
            Runnable modeChangeCallback, DisplayDeviceConfig displayDeviceConfig) {
        this(hbmController, modeChangeCallback, displayDeviceConfig,
                new NormalBrightnessModeController());
    }

    @VisibleForTesting
    BrightnessRangeController(HighBrightnessModeController hbmController,
            Runnable modeChangeCallback, DisplayDeviceConfig displayDeviceConfig,
            NormalBrightnessModeController normalBrightnessModeController) {
        mHbmController = hbmController;
        mModeChangeCallback = modeChangeCallback;
        mNormalBrightnessModeController = normalBrightnessModeController;
        mNormalBrightnessModeController.resetNbmData(
                displayDeviceConfig.getLuxThrottlingData());
        // HDR boost is handled by HdrBrightnessModifier and should be disabled in HbmController
        mHbmController.disableHdrBoost();
    }

    void dump(PrintWriter pw) {
        pw.println("BrightnessRangeController:");
        mHbmController.dump(pw);
        mNormalBrightnessModeController.dump(pw);
    }

    void onAmbientLuxChange(float ambientLux) {
        applyChanges(
                () -> mNormalBrightnessModeController.onAmbientLuxChange(ambientLux),
                () -> mHbmController.onAmbientLuxChange(ambientLux)
        );
    }

    float getNormalBrightnessMax() {
        return mHbmController.getNormalBrightnessMax();
    }

    void loadFromConfig(@Nullable HighBrightnessModeMetadata hbmMetadata, IBinder token,
            DisplayDeviceInfo info, DisplayDeviceConfig displayDeviceConfig) {
        applyChanges(
                () -> mNormalBrightnessModeController.resetNbmData(
                        displayDeviceConfig.getLuxThrottlingData()),
                () -> {
                    mHbmController.setHighBrightnessModeMetadata(hbmMetadata);
                    mHbmController.resetHbmData(info.width, info.height, token, info.uniqueId,
                            displayDeviceConfig.getHighBrightnessModeData(),
                            displayDeviceConfig::getHdrBrightnessFromSdr);
                }
        );
    }

    void stop() {
        mHbmController.stop();
    }

    void setAutoBrightnessEnabled(int state) {
        applyChanges(
                () -> mNormalBrightnessModeController.setAutoBrightnessState(state),
                () ->  mHbmController.setAutoBrightnessEnabled(state)
        );
    }

    void onBrightnessChanged(float brightness, float unthrottledBrightness,
            DisplayBrightnessState state) {
        mHbmController.onHdrBoostApplied(
                state.getHdrBrightness() != DisplayBrightnessState.BRIGHTNESS_NOT_SET);
        mHbmController.onBrightnessChanged(brightness, unthrottledBrightness,
                state.getBrightnessMaxReason());
    }

    float getCurrentBrightnessMin() {
        return mHbmController.getCurrentBrightnessMin();
    }


    float getCurrentBrightnessMax() {
        // nbmController might adjust maxBrightness only if device does not support HBM or
        // hbm is currently not allowed
        if (!mHbmController.deviceSupportsHbm() || !mHbmController.isHbmCurrentlyAllowed()) {
            return Math.min(mHbmController.getCurrentBrightnessMax(),
                    mNormalBrightnessModeController.getCurrentBrightnessMax());
        }
        return mHbmController.getCurrentBrightnessMax();
    }

    int getHighBrightnessMode() {
        return mHbmController.getHighBrightnessMode();
    }

    float getHdrBrightnessValue() {
        return  mHbmController.getHdrBrightnessValue();
    }

    float getTransitionPoint() {
        return mHbmController.getTransitionPoint();
    }

    private void applyChanges(BooleanSupplier nbmChangesFunc, Runnable hbmChangesFunc) {
        boolean nbmTransitionChanged = nbmChangesFunc.getAsBoolean();
        hbmChangesFunc.run();
        // if nbm transition changed - trigger callback
        // HighBrightnessModeController handles sending changes itself
        if (nbmTransitionChanged) {
            mModeChangeCallback.run();
        }
    }
}
