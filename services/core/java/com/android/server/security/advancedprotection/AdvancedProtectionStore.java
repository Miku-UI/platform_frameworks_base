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

package com.android.server.security.advancedprotection;

import static android.provider.Settings.Secure.AAPM_USB_DATA_PROTECTION;
import static android.provider.Settings.Secure.ADVANCED_PROTECTION_MODE;

import android.annotation.NonNull;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.os.UserHandle;
import android.provider.Settings;
import android.security.advancedprotection.AdvancedProtectionManager.FeatureId;
import android.security.advancedprotection.AdvancedProtectionManager.SupportDialogType;
import android.util.ArrayMap;
import java.io.File;

class AdvancedProtectionStore {
    // Settings.Secure values
    private static final int ON = 1;
    private static final int OFF = 0;

    // Shared preferences keys
    private static final String PREFERENCE = "advanced_protection_preference";
    private static final String FEATURE_ADMIN_PROVISIONING_MODE_PREFIX =
            "feature_admin_provisioned_";
    private static final String FEATURE_ADB_PROVISIONING_MODE_PREFIX =
            "feature_adb_provisioned_";
    private static final String ENABLED_CHANGE_TIME = "enabled_change_time";
    private static final String LAST_DIALOG_FEATURE_ID = "last_dialog_feature_id";
    private static final String LAST_DIALOG_TYPE = "last_dialog_type";
    private static final String LAST_DIALOG_HOURS_SINCE_ENABLED = "last_dialog_hours_since_enabled";
    private static final String LAST_DIALOG_LEARN_MORE_CLICKED = "last_dialog_learn_more_clicked";

    private final Context mContext;
    private SharedPreferences mSharedPreferences;
    private final ArrayMap<Integer, Boolean> mFeatureIdToAdminProvisioned = new ArrayMap<>();
    private final ArrayMap<Integer, Boolean> mFeatureIdToAdbProvisioned = new ArrayMap<>();

    AdvancedProtectionStore(@NonNull Context context) {
        mContext = context;
    }

    void saveAdvancedProtectionModeEnabled(boolean enabled) {
        storeInt(ADVANCED_PROTECTION_MODE, enabled ? ON : OFF);
    }

    // Advanced protection mode is disabled by default
    boolean retrieveAdvancedProtectionModeEnabled() {
        return retrieveInt(ADVANCED_PROTECTION_MODE, OFF) == ON;
    }

    void saveUsbDataProtectionEnabled(boolean enabled) {
        storeInt(AAPM_USB_DATA_PROTECTION, enabled ? ON : OFF);
    }

    // Usb data protection is enabled by default
    boolean retrieveUsbDataProtectionEnabled() {
        return retrieveInt(AAPM_USB_DATA_PROTECTION, ON) == ON;
    }

    void saveFeatureAdminProvisioned(@FeatureId int featureId, boolean isProvisioned) {
        mFeatureIdToAdminProvisioned.put(featureId, isProvisioned);
        getSharedPreferences()
                .edit()
                .putBoolean(FEATURE_ADMIN_PROVISIONING_MODE_PREFIX + featureId, isProvisioned)
                .apply();
    }

    /**
     * @return if the feature admin has provisioned the feature, or null if it is not saved
     */
    Boolean retrieveFeatureAdminProvisioned(@FeatureId int featureId) {
        if (mFeatureIdToAdminProvisioned.containsKey(featureId)) {
            return mFeatureIdToAdminProvisioned.get(featureId);
        }

        if (getSharedPreferences().contains(FEATURE_ADMIN_PROVISIONING_MODE_PREFIX + featureId)) {
            boolean isProvisioned =
                    getSharedPreferences()
                            .getBoolean(FEATURE_ADMIN_PROVISIONING_MODE_PREFIX + featureId, false);
            mFeatureIdToAdminProvisioned.put(featureId, isProvisioned);
            return isProvisioned;
        } else {
            mFeatureIdToAdminProvisioned.put(featureId, null);
            return null;
        }
    }

    void saveFeatureAdbProvisioned(@FeatureId int featureId, boolean isProvisioned) {
        mFeatureIdToAdbProvisioned.put(featureId, isProvisioned);
        getSharedPreferences()
                .edit()
                .putBoolean(FEATURE_ADB_PROVISIONING_MODE_PREFIX + featureId, isProvisioned)
                .apply();
    }

    void removeFeatureAdbProvisioning(@FeatureId int featureId) {
        mFeatureIdToAdbProvisioned.put(featureId, null);
        getSharedPreferences()
                .edit()
                .remove(FEATURE_ADB_PROVISIONING_MODE_PREFIX + featureId)
                .apply();
    }

    /**
     * @return if adb has provisioned the feature, or null if it is not saved
     */
    Boolean retrieveFeatureAdbProvisioned(@FeatureId int featureId) {
        if (mFeatureIdToAdbProvisioned.containsKey(featureId)) {
            return mFeatureIdToAdbProvisioned.get(featureId);
        }

        if (getSharedPreferences().contains(FEATURE_ADB_PROVISIONING_MODE_PREFIX + featureId)) {
            boolean isProvisioned =
                    getSharedPreferences()
                            .getBoolean(FEATURE_ADB_PROVISIONING_MODE_PREFIX + featureId, false);
            mFeatureIdToAdbProvisioned.put(featureId, isProvisioned);
            return isProvisioned;
        } else {
            mFeatureIdToAdbProvisioned.put(featureId, null);
            return null;
        }
    }

    void saveEnabledChangeTime(long value) {
        getSharedPreferences().edit().putLong(ENABLED_CHANGE_TIME, value).apply();
    }

    long retrieveEnabledChangeTime() {
        return getSharedPreferences().getLong(ENABLED_CHANGE_TIME, -1);
    }

    void saveDialogShown(
            @FeatureId int featureId,
            @SupportDialogType int type,
            boolean learnMoreClicked,
            int hoursSinceEnabled) {
        getSharedPreferences()
                .edit()
                .putInt(LAST_DIALOG_FEATURE_ID, featureId)
                .putInt(LAST_DIALOG_TYPE, type)
                .putBoolean(LAST_DIALOG_LEARN_MORE_CLICKED, learnMoreClicked)
                .putInt(LAST_DIALOG_HOURS_SINCE_ENABLED, hoursSinceEnabled)
                .apply();
    }

    int retrieveLastDialogFeatureId() {
        return getSharedPreferences().getInt(LAST_DIALOG_FEATURE_ID, -1);
    }

    int retrieveLastDialogType() {
        return getSharedPreferences().getInt(LAST_DIALOG_TYPE, -1);
    }

    boolean retrieveLastDialogLearnMoreClicked() {
        return getSharedPreferences().getBoolean(LAST_DIALOG_LEARN_MORE_CLICKED, false);
    }

    int retrieveLastDialogHoursSinceEnabled() {
        return getSharedPreferences().getInt(LAST_DIALOG_HOURS_SINCE_ENABLED, -1);
    }

    SharedPreferences getSharedPreferences() {
        if (mSharedPreferences == null) {
            initSharedPreferences();
        }
        return mSharedPreferences;
    }

    private void storeInt(String key, int value) {
        Settings.Secure.putIntForUser(
                mContext.getContentResolver(), key, value, UserHandle.USER_SYSTEM);
    }

    private int retrieveInt(String key, int defaultValue) {
        return Settings.Secure.getIntForUser(
                mContext.getContentResolver(), key, defaultValue, UserHandle.USER_SYSTEM);
    }

    private synchronized void initSharedPreferences() {
        if (mSharedPreferences == null) {
            Context deviceContext = mContext.createDeviceProtectedStorageContext();
            File sharedPrefs = new File(Environment.getDataSystemDirectory(), PREFERENCE);
            mSharedPreferences =
                    deviceContext.getSharedPreferences(sharedPrefs, Context.MODE_PRIVATE);
        }
    }
}
