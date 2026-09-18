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
package com.android.server.pm;

import static com.android.server.pm.HsumBootUserInitializer.getFullAdminFilter;
import static android.os.UserManager.USER_TYPE_SYSTEM_HEADLESS;

import android.Manifest;
import android.annotation.Nullable;
import android.annotation.UserIdInt;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;

import androidx.annotation.RequiresPermission;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.utils.Slogf;

import java.util.List;

/**
 * Class responsible for device provisioning related activities for when the device boots in
 * headless system user mode. This class is not thread safe.
 */
final class HsuDeviceProvisioner extends ContentObserver {

    private static final String TAG = HsuDeviceProvisioner.class.getSimpleName();
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    private final ContentResolver mContentResolver;
    private final PackageManager mPm;
    private final UserManagerService mUms;

    /**
     * Constructs a new HsuDeviceProvisioner.
     *
     * <p>This code runs within the system_server process. The provided {@code context}
     * is therefore associated with {@link UserHandle#USER_SYSTEM} (user 0).
     *
     * <p>Consequently, the {@link PackageManager} obtained via {@code context.getPackageManager()}
     * will operate with the privileges and scope of the system user.
     */
    HsuDeviceProvisioner(Context context, Handler handler, UserManagerService ums) {
        super(handler);
        mContentResolver = context.getContentResolver();
        mPm = context.getPackageManager();
        mUms = ums;
    }

    /**
     * Initialize this object.
     *
     * <p>It will register itself as a content observer for the settings changes if necessary.
     */
    public void init() {
        if (isDeviceProvisioned()) {
            if (mPm.isDeviceUpgrading()) {
                onDeviceUpgrading();
            }
            return;
        }

        // Device is not provisioned yet. Override the HSU activities allowlist as to temporarily
        // disable it. This will be reset once the device is provisioned (via onChange()).
        overrideHsuActivitiesAllowlistDisallowedStatus();

        mContentResolver.registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.DEVICE_PROVISIONED), false, this);
    }

    @Override
    public void onChange(boolean selfChange) {
        if (DEBUG) {
            Slogf.d(TAG, "onChange(%b): isDeviceProvisioned=%b", selfChange, isDeviceProvisioned());
        }
        if (!isDeviceProvisioned()) {
            return;
        }

        // Device is now provisioned. Reset the HSU activities allowlist overridden status.
        resetHsuActivitiesAllowlistOverriddenDisallowedStatus();

        Slogf.i(TAG, "Making changes on first boot");
        // Set USER_SETUP_COMPLETE for the (headless) system user only when the device
        // has been set up at least once.
        Slogf.i(TAG, "Marking USER_SETUP_COMPLETE");
        Settings.Secure.putInt(mContentResolver, Settings.Secure.USER_SETUP_COMPLETE, 1);
        mContentResolver.unregisterContentObserver(this);
        disableSetupWizardHomeForSystemUser();
        // Copy settings from the Real user to the system user.
        copySecureSettingFromFirstAdmin();
    }

    @VisibleForTesting
    void onDeviceUpgrading() {
        Slogf.i(TAG, "Making changes when device is updating");
        //TODO(b/446947591):Remove check after OTA launch
        copySecureSettingFromFirstAdmin();
    }

    private boolean isDeviceProvisioned() {
        try {
            return Settings.Global.getInt(mContentResolver, Settings.Global.DEVICE_PROVISIONED)
                    == 1;
        } catch (Exception e) {
            Slogf.wtf(
                TAG,
                "DEVICE_PROVISIONED setting (%s) not found: %s",
                Settings.Global.DEVICE_PROVISIONED,
                e);
            return false;
        }
    }

    /**
     * Overrides the status of the HSU activities allowlist to be disallowed.
     * This is to temporarily disable the allowlist while the device is unprovisioned.
     */
    private void overrideHsuActivitiesAllowlistDisallowedStatus() {
        final UserActivitiesAllowlist hsuActivitiesAllowlist =
                mUms.getActivitiesAllowlist(USER_TYPE_SYSTEM_HEADLESS);
        if (hsuActivitiesAllowlist == null) {
            Slogf.d(TAG, "HSU activities allowlist is null. Skipping overriding status.");
            return;
        }
        Slogf.i(TAG, "Overriding HSU activities allowlist status");
        hsuActivitiesAllowlist.overrideDisallowedStatus(
                GenericAllowlist.STATUS_ALLOWED_ALLOWLISTING_DISABLED_WHILE_DEVICE_IS_PROVISIONING);
    }

    /**
     * Resets the overridden status of the HSU activities allowlist.
     * This should be called when the device is provisioned to clear any overrides that were set
     * while the device was unprovisioned.
     */
    private void resetHsuActivitiesAllowlistOverriddenDisallowedStatus() {
        final UserActivitiesAllowlist hsuActivitiesAllowlist =
                mUms.getActivitiesAllowlist(USER_TYPE_SYSTEM_HEADLESS);
        if (hsuActivitiesAllowlist == null) {
            Slogf.d(TAG, "HSU activities allowlist is null. Skipping resetting overridden status.");
            return;
        }
        Slogf.i(TAG, "Resetting HSU activities allowlist overridden status");
        hsuActivitiesAllowlist.overrideDisallowedStatus(null);
    }

    @VisibleForTesting
    void copySecureSettingFromSourceUser(@UserIdInt int userId) {
        copySecureSettingFromSourceUser(userId, Settings.Secure.BUGREPORT_IN_POWER_MENU,
                /* defaultValue= */ 0);
    }

    @VisibleForTesting
    void copySecureSettingFromFirstAdmin() {
        var filter = getFullAdminFilter();
        var users = mUms.getUsers(filter);
        if (users.isEmpty()) {
            Slogf.wtf(TAG, "No users found matching filter %s", filter);
            return;
        }
        int firstUserId = users.get(0).id;
        Slogf.i(TAG, "copySecureSettingFromFirstAdmin(): will copy settings from user %d",
                firstUserId);

        copySecureSettingFromSourceUser(firstUserId);
    }

    private void copySecureSettingFromSourceUser(
             @UserIdInt int userId, String settingName, int defaultValue) {
        if (userId == UserHandle.USER_SYSTEM) {
            if (DEBUG) {
                Slogf.d(TAG, "Skipping copySecureSettingFromSourceUser for %s: "
                        + "source user is system user", settingName);
            }
            return;
        }
        int settingValue =
                Settings.Secure.getIntForUser(
                        mContentResolver, settingName, defaultValue, userId);
        Slogf.i(TAG, "copySecureSettingFromSourceUser(userId=%d): %s=%d",
                userId, settingName, settingValue);
        Settings.Secure.putIntForUser(
                mContentResolver, settingName, settingValue, UserHandle.USER_SYSTEM);
    }

    @RequiresPermission(Manifest.permission.CHANGE_COMPONENT_ENABLED_STATE)
    @SuppressWarnings("AndroidFrameworkRequiresPermission")
    @VisibleForTesting
    void disableSetupWizardHomeForSystemUser() {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_SETUP_WIZARD);

        int flags = PackageManager.MATCH_SYSTEM_ONLY | PackageManager.MATCH_DIRECT_BOOT_AWARE
            | PackageManager.MATCH_DIRECT_BOOT_UNAWARE | PackageManager.MATCH_DISABLED_COMPONENTS;

        List<ResolveInfo> matches = mPm.queryIntentActivities(intent, flags);

        if (matches == null || matches.isEmpty()) {
            Slogf.w(TAG, "Could not find Setup Wizard component for system user");
            return;
        }

        ComponentName setupWizardHomeComponent =
            matches.get(0).getComponentInfo().getComponentName();

        if (setupWizardHomeComponent != null) {
            Slogf.i(TAG, "Disabling Setup Wizard component (%s) for system user",
                setupWizardHomeComponent.flattenToString());

            try {
                mPm.setComponentEnabledSetting(setupWizardHomeComponent,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP);
                Slogf.i(TAG, "Successfully disabled %s",
                    setupWizardHomeComponent.flattenToString());
            }  catch (Exception e) {
                Slogf.e(TAG, e, "Exception disabling component: %s",
                    setupWizardHomeComponent.flattenToString());
            }
        }
    }
}
