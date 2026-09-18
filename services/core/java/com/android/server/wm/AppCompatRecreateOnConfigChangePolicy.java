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

package com.android.server.wm;

import static android.content.pm.ActivityInfo.CONFIG_COLOR_MODE;
import static android.content.pm.ActivityInfo.CONFIG_KEYBOARD;
import static android.content.pm.ActivityInfo.CONFIG_KEYBOARD_HIDDEN;
import static android.content.pm.ActivityInfo.CONFIG_NAVIGATION;
import static android.content.pm.ActivityInfo.CONFIG_TOUCHSCREEN;

import static com.android.window.flags.Flags.enableLessActivityRecreationOnConfigChange;

import android.annotation.NonNull;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.UserHandle;
import android.util.Slog;

import com.android.internal.annotations.VisibleForTesting;

import java.io.PrintWriter;

/**
 * Policy to determine whether the activity should be recreated on configuration changes, based on
 * whether they contain resources for that specific configuration.
 */
public class AppCompatRecreateOnConfigChangePolicy {
    private static final String TAG = "AppCompatRecreateOnConfigChangePolicy";

    @NonNull
    final ActivityRecord mActivityRecord;

    /**
     * Bitmask to store whether the package has resources for a specific configuration type.
     * If a configuration change occurs that is covered by this mask, recreate the activity.
     */
    private int mRecreateConfigMask;

    /**
     * Indicates whether the configurations of the package resources has been checked.
     */
    private boolean mResourcesChecked;

    AppCompatRecreateOnConfigChangePolicy(@NonNull ActivityRecord activityRecord) {
        mActivityRecord = activityRecord;
    }

    /**
     * Check whether the package has resources for each specific type of configuration.
     *
     * @return A bit mask of configuration change kinds that should cause the activity to be
     * recreated by the system to ensure the activity uses the correct resources.
     **/
    @VisibleForTesting
    int getRecreateConfigMask() {
        if (!enableLessActivityRecreationOnConfigChange()) {
            return 0;
        }

        if (!mActivityRecord.info.isChangeEnabled(
                ActivityInfo.SKIP_ACTIVITY_RECREATION_ON_CONFIG_CHANGE)) {
            return 0;
        }

        if (mResourcesChecked) {
            return mRecreateConfigMask;
        }

        checkConfigResources();
        return mRecreateConfigMask;
    }

    /**
     * Check whether the package has resources for {@link CONFIG_KEYBOARD},
     * {@link CONFIG_KEYBOARD_HIDDEN}, {@link CONFIG_NAVIGATION}, {@link CONFIG_TOUCHSCREEN} and
     * {@link CONFIG_COLOR_MODE}.
     */
    private void checkConfigResources() {
        mResourcesChecked = true;

        Resources packageResources = null;
        try {
            packageResources = mActivityRecord.mAtmService.mContext
                    .createPackageContextAsUser(mActivityRecord.packageName,
                            /*flags=*/ 0, UserHandle.of(mActivityRecord.mUserId)).getResources();
        } catch (PackageManager.NameNotFoundException e) {
            Slog.w(TAG, "Failed to find the package: " + mActivityRecord.packageName, e);
        }

        if (packageResources == null) {
            return;
        }

        final Configuration[] configs = packageResources.getResourceConfigurations();

        if (configs == null) {
            return;
        }

        for (Configuration config : configs) {
            if (config.keyboard != Configuration.KEYBOARD_UNDEFINED) {
                mRecreateConfigMask |= CONFIG_KEYBOARD;
            }
            if (config.keyboardHidden != Configuration.KEYBOARDHIDDEN_UNDEFINED) {
                mRecreateConfigMask |= CONFIG_KEYBOARD_HIDDEN;
            }
            if (config.navigation != Configuration.NAVIGATION_UNDEFINED) {
                mRecreateConfigMask |= CONFIG_NAVIGATION;
            }
            if (config.touchscreen != Configuration.TOUCHSCREEN_UNDEFINED) {
                mRecreateConfigMask |= CONFIG_TOUCHSCREEN;
            }
            if (config.colorMode != Configuration.COLOR_MODE_UNDEFINED) {
                mRecreateConfigMask |= CONFIG_COLOR_MODE;
            }
        }
    }

    void dump(@NonNull PrintWriter pw, @NonNull String prefix) {
        if (mResourcesChecked) {
            pw.println(prefix +  "mRecreateConfigMask=" + mRecreateConfigMask);
        }
    }
}
