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

package com.android.server.security.authenticationpolicy.settings;

import android.app.admin.DevicePolicyManager;
import android.hardware.usb.IUsbManagerInternal;
import android.hardware.usb.UsbManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Map;

/** Interface for managing secure lock device settings. */
public interface SecureLockDeviceSettingsManager {

    /**
     * Gets the current managed settings.
     *
     * @return The current managed settings.
     */
    @NonNull
    Map<String, ManagedSetting<?>> getManagedSettings();

    /** Initializes system service dependencies that are not ready until boot is completed */
    void initSettingsControllerDependencies(@Nullable DevicePolicyManager devicePolicyManager,
            @Nullable UsbManager usbManager, @Nullable IUsbManagerInternal usbManagerInternal);

    /**
     * Resets map of {@link ManagedSetting} for security features in secure lock device. This is
     * used
     * to store the original value of settings when secure lock device is enabled, apply the secure
     * lock device value for settings, and restore the original value of settings when secure lock
     * device is disabled.
     */
    void resetManagedSettings();

    /** Applies secure lock device value from {@link SettingState#getSecureLockDeviceValue()}. */
    void enableSecurityFeaturesFromBoot(int userId);

    /**
     * Stores the current state of each setting using {@link SettingState#setOriginalValue(Object)}
     * and applies secure lock device value from {@link SettingState#getSecureLockDeviceValue()}.
     */
    void enableSecurityFeatures(int userId);

    /**
     * Restores the original values to all managed settings.
     *
     * @param userId The user ID to restore the settings for.
     */
    void restoreOriginalSettings(int userId);

    /**
     * Sets flag that certain security features that interfere with testing (e.g. USB, ADB) should
     * be
     * skipped.
     *
     * @param skipSecurityFeaturesForTest Whether to skip security features for test.
     */
    void setSkipSecurityFeaturesForTest(boolean skipSecurityFeaturesForTest);
}
