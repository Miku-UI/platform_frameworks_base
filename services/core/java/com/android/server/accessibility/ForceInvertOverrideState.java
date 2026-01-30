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
package com.android.server.accessibility;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.UiModeManager;
import android.content.ContentResolver;
import android.content.Context;
import android.provider.Settings;
import android.text.TextUtils;

import com.android.internal.util.ArrayUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Holds the state of the force invert overrides from Settings.
 *
 * @param packagesToEnable list of package names that will always have force invert applied
 * @param packagesToDisable list of package names that will never have force invert applied
 */
public record ForceInvertOverrideState(
        List<String> packagesToEnable, List<String> packagesToDisable) {
    /**
     * An empty force invert override state.
     */
    public static final ForceInvertOverrideState EMPTY = new ForceInvertOverrideState(List.of(),
            List.of());

    private static final String DELIMITER = ",";

    /** Loads the force invert override state from Settings using a system_server context. */
    @NonNull
    public static ForceInvertOverrideState loadFrom(Context context, int userId) {
        var resolver = context.getContentResolver();
        var packageDisableList = parseCsv(resolver, userId,
                Settings.System.ACCESSIBILITY_FORCE_INVERT_COLOR_OVERRIDE_PACKAGES_TO_DISABLE);
        var packageEnableList = parseCsv(resolver, userId,
                Settings.System.ACCESSIBILITY_FORCE_INVERT_COLOR_OVERRIDE_PACKAGES_TO_ENABLE);

        var blocklistArray = context.getResources().getStringArray(
                com.android.internal.R.array.config_forceInvertPackageBlocklist);
        var packageBlocklist = blocklistArray == null ? new ArrayList<String>()
                : ArrayUtils.toList(blocklistArray);

        // Overrides take precedence over config blocklist
        packageBlocklist.removeAll(packageEnableList);
        packageDisableList.addAll(packageBlocklist);

        return new ForceInvertOverrideState(packageEnableList, packageDisableList);
    }

    /**
     * Returns the override state for the given package.
     */
    @UiModeManager.ForceInvertPackageOverrideState
    public int getStateForPackage(@Nullable String packageName) {
        if (TextUtils.isEmpty(packageName)) {
            return UiModeManager.FORCE_INVERT_PACKAGE_ALLOWED;
        }

        if (packagesToDisable.contains(packageName)) {
            return UiModeManager.FORCE_INVERT_PACKAGE_ALWAYS_DISABLE;
        }

        if (packagesToEnable.contains(packageName)) {
            return UiModeManager.FORCE_INVERT_PACKAGE_ALWAYS_ENABLE;
        }

        return UiModeManager.FORCE_INVERT_PACKAGE_ALLOWED;
    }

    @NonNull
    private static List<String> parseCsv(ContentResolver resolver, int userId, String settingsKey) {
        var csv = Settings.System.getStringForUser(resolver, settingsKey, userId);
        if (csv == null) {
            return new ArrayList<>();
        }

        return ArrayUtils.toList(TextUtils.split(csv, DELIMITER));
    }
}
