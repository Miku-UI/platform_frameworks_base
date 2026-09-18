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

package com.android.systemui.lowlightclock;

import android.content.Context;
import android.content.res.Resources;
import android.os.BatteryManager;

import com.android.internal.util.Preconditions;
import com.android.keyguard.KeyguardUpdateMonitor;
import com.android.keyguard.KeyguardUpdateMonitorCallback;
import com.android.settingslib.fuelgauge.BatteryStatus;
import com.android.systemui.res.R;
import com.android.systemui.statusbar.KeyguardIndicationController;

import dagger.Lazy;

import java.text.NumberFormat;

import javax.inject.Inject;

/**
 * Provides charging status as a string to a registered callback such that it can be displayed to
 * the user (e.g. on the low-light clock).
 */
public class ChargingStatusProvider {

    private final KeyguardUpdateMonitor mKeyguardUpdateMonitor;
    private final BatteryState mBatteryState = new BatteryState();
    // This callback is registered with KeyguardUpdateMonitor, which only keeps weak references to
    // its callbacks. Therefore, an explicit reference needs to be kept here to avoid the
    // callback being GC'd.
    private ChargingStatusCallback mChargingStatusCallback;

    private final Lazy<KeyguardIndicationController> mKeyguardIndicationController;

    private Callback mCallback;

    @Inject
    public ChargingStatusProvider(
            Context context,
            KeyguardUpdateMonitor keyguardUpdateMonitor,
            Lazy<KeyguardIndicationController> keyguardIndicationController) {

        mKeyguardUpdateMonitor = keyguardUpdateMonitor;
        mKeyguardIndicationController = keyguardIndicationController;
    }

    /**
     * Start using the {@link ChargingStatusProvider}.
     * @param callback A callback to be called when the charging status changes.
     */
    public void startUsing(Callback callback) {
        Preconditions.checkState(
                mCallback == null, "ChargingStatusProvider already started!");
        mCallback = callback;
        mChargingStatusCallback = new ChargingStatusCallback();
        mKeyguardUpdateMonitor.registerCallback(mChargingStatusCallback);
        reportStatusToCallback();
    }

    /**
     * Stop using the {@link ChargingStatusProvider}.
     */
    public void stopUsing() {
        mCallback = null;

        if (mChargingStatusCallback != null) {
            mKeyguardUpdateMonitor.removeCallback(mChargingStatusCallback);
            mChargingStatusCallback = null;
        }
    }

    private void reportStatusToCallback() {
        if (mCallback != null) {
            final boolean shouldShowStatus =
                    mBatteryState.isPowerPluggedIn() || mBatteryState.isBatteryDefenderEnabled();
            mCallback.onChargingStatusChanged(shouldShowStatus, getChargingString());
        }
    }

    private String getChargingString() {
        return mKeyguardIndicationController.get().getPowerChargingString();
    }

    private class ChargingStatusCallback extends KeyguardUpdateMonitorCallback {
        @Override
        public void onRefreshBatteryInfo(BatteryStatus status) {
            mBatteryState.setBatteryStatus(status);
            reportStatusToCallback();
        }
    }

    /***
     * A callback to be called when the charging status changes.
     */
    public interface Callback {
        /***
         * Called when the charging status changes.
         * @param shouldShowStatus Whether or not to show a charging status message.
         * @param statusMessage A charging status message.
         */
        void onChargingStatusChanged(boolean shouldShowStatus, String statusMessage);
    }

    /***
     * A wrapper around {@link BatteryStatus} for fetching various properties of the current
     * battery and charging state.
     */
    private static class BatteryState {
        private BatteryStatus mBatteryStatus;

        public void setBatteryStatus(BatteryStatus batteryStatus) {
            mBatteryStatus = batteryStatus;
        }

        public boolean isValid() {
            return mBatteryStatus != null;
        }

        public boolean isBatteryDefenderEnabled() {
            return isValid() && mBatteryStatus.isPluggedIn() && isBatteryDefender();
        }

        public boolean isBatteryDefender() {
            return isValid() && mBatteryStatus.isBatteryDefender();
        }

        public boolean isPowerPluggedIn() {
            return isValid() && mBatteryStatus.isPluggedIn() && isChargingOrFull();
        }

        private boolean isChargingOrFull() {
            return isValid()
                    && (mBatteryStatus.status == BatteryManager.BATTERY_STATUS_CHARGING
                        || mBatteryStatus.isCharged());
        }
    }
}
