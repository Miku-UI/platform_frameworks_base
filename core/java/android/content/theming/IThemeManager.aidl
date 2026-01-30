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

package android.content.theming;

import android.content.theming.ThemeSettings;
import android.content.theming.IThemeSettingsCallback;

/** @hide */
interface IThemeManager {
    /** @hide */
    boolean registerThemeSettingsCallback(in IThemeSettingsCallback callback);
    /** @hide */
    boolean unregisterThemeSettingsCallback(in IThemeSettingsCallback callback);

    /** @hide */
    @EnforcePermission("UPDATE_THEME_SETTINGS")
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.UPDATE_THEME_SETTINGS)")
    boolean updateThemeSettings(in ThemeSettings newSettings);

    /** @hide */
    ThemeSettings getThemeSettings();

    /** @hide */
    ThemeSettings getThemeSettingsOrDefault();
}