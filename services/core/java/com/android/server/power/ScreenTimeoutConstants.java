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

package com.android.server.power;

import android.content.Context;
import android.os.UserHandle;
import android.provider.Settings;

import java.io.PrintWriter;

/**
 * Manages configuration settings related to screen timeouts.
 *
 * This class holds and provides access to various timeout durations
 * used for screen dimming, screen off, timeouts
 */
public final class ScreenTimeoutConstants {
    // Default timeout in milliseconds.  This is only used until the settings
    // provider populates the actual default value (R.integer.def_screen_off_timeout).
    public static final int DEFAULT_SCREEN_OFF_TIMEOUT = 15 * 1000;

    private static final int DEFAULT_SLEEP_TIMEOUT = -1;

    // Default value for attentive timeout.
    private int mAttentiveTimeoutConfig;

    // How long to show a warning message to user before the device goes to sleep
    // after long user inactivity, even if wakelocks are held.
    private long mAttentiveTimeoutSetting;

    // The minimum screen off timeout, in milliseconds.
    private long mMinimumScreenOffTimeoutConfig;

    // The screen dim duration, in milliseconds.
    // This is subtracted from the end of the screen off timeout so the
    // minimum screen off timeout should be longer than this.
    private long mMaximumScreenDimDurationConfig;

    // The maximum screen dim time expressed as a ratio relative to the screen
    // off timeout.  If the screen off timeout is very short then we want the
    // dim timeout to also be quite short so that most of the time is spent on.
    // Otherwise the user won't get much screen on time before dimming occurs.
    private float mMaximumScreenDimRatioConfig;

    // The maximum allowable screen off timeout according to the device
    // administration policy.  Overrides other settings.
    public long mMaximumScreenOffTimeoutFromDeviceAdmin = Long.MAX_VALUE;

    // The screen off timeout setting value in milliseconds.
    private long mScreenOffTimeoutSetting;

    // The sleep timeout setting value in milliseconds.
    private long mSleepTimeoutSetting;

    /**
     * Reloads the configs
     */
    public void readConfigLocked(Context context) {
        mAttentiveTimeoutConfig = context.getResources().getInteger(
                com.android.internal.R.integer.config_attentiveTimeout);
        mMinimumScreenOffTimeoutConfig = context.getResources().getInteger(
                com.android.internal.R.integer.config_minimumScreenOffTimeout);
        mMaximumScreenDimDurationConfig = context.getResources().getInteger(
                com.android.internal.R.integer.config_maximumScreenDimDuration);
        mMaximumScreenDimRatioConfig = context.getResources().getFraction(
                com.android.internal.R.fraction.config_maximumScreenDimRatio, 1, 1);
    }

    /**
     * Reloads the settings
     */
    public void updateSettingsLocked(Context context) {
        mScreenOffTimeoutSetting = Settings.System.getIntForUser(context.getContentResolver(),
                Settings.System.SCREEN_OFF_TIMEOUT, DEFAULT_SCREEN_OFF_TIMEOUT,
                UserHandle.USER_CURRENT);
        mSleepTimeoutSetting = Settings.Secure.getIntForUser(context.getContentResolver(),
                Settings.Secure.SLEEP_TIMEOUT, DEFAULT_SLEEP_TIMEOUT,
                UserHandle.USER_CURRENT);
        mAttentiveTimeoutSetting = Settings.Secure.getIntForUser(context.getContentResolver(),
                Settings.Secure.ATTENTIVE_TIMEOUT,
                mAttentiveTimeoutConfig,
                UserHandle.USER_CURRENT);
    }

    /**
     * Gets the attentive timeout. Synchronized on PowerManagerService mLock
     */
    public long getAttentiveTimeoutLocked() {
        long timeout = mAttentiveTimeoutSetting;
        if (timeout <= 0) {
            return -1;
        }

        return Math.max(timeout, mMinimumScreenOffTimeoutConfig);
    }


    /**
     * Gets the sleep timeout. Synchronized on PowerManagerService mLock
     */
    public long getSleepTimeoutLocked(long attentiveTimeout) {
        long timeout = getSleepTimeoutSettingLocked();
        if (timeout <= 0) {
            return DEFAULT_SLEEP_TIMEOUT;
        }
        if (attentiveTimeout >= 0) {
            timeout = Math.min(timeout, attentiveTimeout);
        }
        return Math.max(timeout, getMinimumScreenOffTimeoutConfigLocked());
    }

    /**
     * Gets the attentive timeout config value
     */
    public int getAttentiveTimeoutConfig() {
        return mAttentiveTimeoutConfig;
    }

    /**
     * Gets the minimum screen off timeout. Synchronized on PowerManagerService mLock
     */
    public long getMinimumScreenOffTimeoutConfigLocked() {
        return mMinimumScreenOffTimeoutConfig;
    }

    /**
     * Gets the maximum screen off timeout. Synchronized on PowerManagerService mLock
     */
    public long getMaximumScreenDimDurationConfig() {
        return mMaximumScreenDimDurationConfig;
    }

    /**
     * Gets the maximum screen dim ratio. Synchronized on PowerManagerService mLock
     */
    public float getMaximumScreenDimRatioConfig() {
        return mMaximumScreenDimRatioConfig;
    }

    /**
     * Sets he maximum screen off timeout from device admin. Synchronized on PowerManagerService
     * mLock
     */
    public void setMaximumScreenOffTimeoutFromDeviceAdminLocked(
            long maximumScreenOffTimeoutFromDeviceAdmin) {
        mMaximumScreenOffTimeoutFromDeviceAdmin =
                maximumScreenOffTimeoutFromDeviceAdmin;
    }

    /**
     * Gets the maximum screen off timeout from device admin. Synchronized on PowerManagerService
     * mLock
     */
    public long getMaximumScreenOffTimeoutFromDeviceAdminLocked() {
        return mMaximumScreenOffTimeoutFromDeviceAdmin;
    }

    /**
     * Gets if the maximum screen off timeout config is overridden or not. Synchronized on
     * PowerManagerService mLock
     */
    public boolean isMaximumScreenOffTimeoutFromDeviceAdminEnforcedLocked() {
        return mMaximumScreenOffTimeoutFromDeviceAdmin >= 0
                && mMaximumScreenOffTimeoutFromDeviceAdmin < Long.MAX_VALUE;
    }

    /**
     * Gets the Screen off timeout setting. Synchronized on PowerManagerService mLock
     */
    public long getScreenOffTimeoutSettingLocked() {
        return mScreenOffTimeoutSetting;
    }

    /**
     * Gets the Sleep timeout setting. Synchronized on PowerManagerService mLock
     */
    public long getSleepTimeoutSettingLocked() {
        return mSleepTimeoutSetting;
    }

    /**
     * Gets the attentive timeout setting. Synchronized on PowerManagerService mLock
     */
    public long getAttentiveTimeoutSettingLocked() {
        return mAttentiveTimeoutSetting;
    }

    /**
     * Dumpsys the current state of this class
     */
    public void dumpsys(PrintWriter pw) {
        pw.println("ScreenTimeoutConstants: ");
        pw.println("  mAttentiveTimeoutConfig=" + mAttentiveTimeoutConfig);
        pw.println("  mAttentiveTimeoutSetting=" + mAttentiveTimeoutSetting);
        pw.println("  mMinimumScreenOffTimeoutConfig=" + mMinimumScreenOffTimeoutConfig);
        pw.println("  mMaximumScreenDimDurationConfig=" + mMaximumScreenDimDurationConfig);
        pw.println("  mMaximumScreenDimRatioConfig=" + mMaximumScreenDimRatioConfig);
        pw.println("  mMaximumScreenOffTimeoutFromDeviceAdmin="
                + mMaximumScreenOffTimeoutFromDeviceAdmin + " (enforced="
                + isMaximumScreenOffTimeoutFromDeviceAdminEnforcedLocked() + ")");
        pw.println("  mScreenOffTimeoutSetting=" + mScreenOffTimeoutSetting);
        pw.println("  mSleepTimeoutSetting=" + mSleepTimeoutSetting);
    }
}
