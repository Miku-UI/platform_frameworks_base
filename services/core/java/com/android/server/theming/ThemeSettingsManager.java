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

package com.android.server.theming;

import android.annotation.FlaggedApi;
import android.annotation.Nullable;
import android.annotation.UserIdInt;
import android.content.ContentResolver;
import android.content.theming.ThemeSettings;
import android.content.theming.ThemeSettingsField;
import android.provider.Settings;
import android.telecom.Log;

/**
 * Manages the loading and saving of theme settings. This class handles the persistence of theme
 * settings to and from the system settings. It utilizes a collection of {@link ThemeSettingsField}
 * objects to represent individual theme setting fields.
 *
 * @hide
 */
@FlaggedApi(android.server.Flags.FLAG_ENABLE_THEME_SERVICE)
class ThemeSettingsManager {
    private static final String TAG = ThemeSettingsManager.class.getSimpleName();

    /**
     * Loads the theme settings for the specified user.
     *
     * @param userId          The ID of the user.
     * @param contentResolver The content resolver to use.
     * @return The loaded {@link ThemeSettings}.
     */
    @Nullable
    ThemeSettings readSettings(@UserIdInt int userId, ContentResolver contentResolver) {
        try {
            return ThemeSettings.fromJson(Settings.Secure.getStringForUser(contentResolver,
                    Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES, userId));
        } catch (Exception e) {
            Log.w(TAG, "Error loading theme settings: " + e);
            return null;
        }
    }

    /**
     * Saves the specified theme settings for the given user.
     *
     * @param userId          The ID of the user.
     * @param contentResolver The content resolver to use.
     * @param newSettings     The {@link ThemeSettings} to save.
     */
    boolean writeSettings(@UserIdInt int userId, ContentResolver contentResolver,
            ThemeSettings newSettings) {
        try {
            Settings.Secure.putStringForUser(contentResolver,
                    Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES, newSettings.toString(),
                    userId);

            return true;
        } catch (Exception e) {
            Log.w(TAG, "Error writings theme settings:" + e.getMessage());

            return false;
        }
    }
}
