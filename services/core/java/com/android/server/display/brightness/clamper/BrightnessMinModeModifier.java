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

package com.android.server.display.brightness.clamper;

import android.annotation.NonNull;
import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.hardware.display.BrightnessInfo;
import android.hardware.display.DisplayManagerInternal;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.display.DisplayBrightnessState;
import com.android.server.display.brightness.BrightnessReason;

import java.io.PrintWriter;

public class BrightnessMinModeModifier
        implements BrightnessStateModifier,
                BrightnessClamperController.DisplayDeviceDataListener,
                BrightnessClamperController.StatefulModifier {

    public static final int MINMODE_ACTIVE = 1;
    public static final int MINMODE_INACTIVE = 0;

    private final Context mContext;

    private final ContentObserver mSettingsObserver;
    protected final Handler mHandler;
    protected final BrightnessClamperController.ClamperChangeListener mChangeListener;

    private float mBrightnessCap;
    private boolean mIsActive = false;
    private boolean mApplied = false;

    BrightnessMinModeModifier(
            Handler handler,
            Context context,
            BrightnessClamperController.ClamperChangeListener listener,
            MinModeBrightnessData data) {
        this(new Injector(), handler, context, listener, data);
    }

    @VisibleForTesting
    BrightnessMinModeModifier(
            Injector injector,
            Handler handler,
            Context context,
            BrightnessClamperController.ClamperChangeListener listener,
            MinModeBrightnessData data) {
        mHandler = handler;
        mChangeListener = listener;
        mContext = context;
        mBrightnessCap = data.getBrightnessMinModeCap();
        mSettingsObserver =
                new ContentObserver(mHandler) {
                    @Override
                    public void onChange(boolean selfChange) {
                        final int minModeSetting =
                                Settings.Global.getInt(
                                        mContext.getContentResolver(),
                                        Settings.Global.MINMODE_ACTIVE,
                                        MINMODE_INACTIVE);
                        mIsActive = minModeSetting == MINMODE_ACTIVE;
                        mChangeListener.onChanged();
                    }
                };
        injector.registerMinModeSettingObserver(context.getContentResolver(), mSettingsObserver);
    }

    // region BrightnessStateModifier
    @Override
    public void apply(
            DisplayManagerInternal.DisplayPowerRequest request,
            DisplayBrightnessState.Builder stateBuilder) {
        if (mIsActive && stateBuilder.getMaxBrightness() > mBrightnessCap) {
            stateBuilder.setMaxBrightness(mBrightnessCap);
            stateBuilder.setBrightness(Math.min(stateBuilder.getBrightness(), mBrightnessCap));
            stateBuilder.setBrightnessMaxReason(BrightnessInfo.BRIGHTNESS_MAX_REASON_MINMODE);
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
        mContext.getContentResolver().unregisterContentObserver(mSettingsObserver);
    }

    @Override
    public void dump(PrintWriter writer) {
        writer.println("BrightnessMinModeModifier:");
        writer.println("  mBrightnessCap: " + mBrightnessCap);
        writer.println("  mIsActive: " + mIsActive);
        writer.println("  mApplied: " + mApplied);
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

    // region DisplayDeviceDataListener
    @Override
    public void onDisplayChanged(BrightnessClamperController.DisplayDeviceData data) {
        mHandler.post(
                () -> {
                    mBrightnessCap = data.getBrightnessMinModeCap();
                    mChangeListener.onChanged();
                });
    }

    // endregion

    // region StatefulModifier
    @Override
    public void applyStateChange(
            BrightnessClamperController.ModifiersAggregatedState aggregatedState) {
        if (mIsActive && aggregatedState.mMaxBrightness > mBrightnessCap) {
            aggregatedState.mMaxBrightness = mBrightnessCap;
            aggregatedState.mMaxBrightnessReason = BrightnessInfo.BRIGHTNESS_MAX_REASON_MINMODE;
        }
    }

    // endregion

    interface MinModeBrightnessData {
        float getBrightnessMinModeCap();
    }

    @VisibleForTesting
    static class Injector {
        void registerMinModeSettingObserver(
                @NonNull ContentResolver cr, @NonNull ContentObserver observer) {
            cr.registerContentObserver(
                    Settings.Global.getUriFor(Settings.Global.MINMODE_ACTIVE),
                    /* notifyForDescendants= */ false,
                    observer,
                    UserHandle.USER_ALL);
        }
    }
}
