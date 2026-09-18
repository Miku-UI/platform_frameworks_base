/*
 * Copyright (C) 2026 The Android Open Source Project
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

import android.app.WallpaperColors;

/**
 * Interface for delegating system theme events from observers to the core implementation.
 *
 * @hide
 */
public interface ThemeEventDispatcher {
    /**
     * Called when a user starts.
     *
     * @param userId The ID of the user starting.
     */
    void onUserStart(int userId);

    /**
     * Called when users switch.
     *
     * @param fromUserId The ID of the user being switched from.
     * @param toUserId   The ID of the user being switched to.
     */
    void onUserSwitching(int fromUserId, int toUserId);

    /**
     * Called when a profile is added to a parent user.
     *
     * @param parentId  The ID of the parent user.
     * @param profileId The ID of the newly added profile.
     */
    void onProfileAdded(int parentId, int profileId);

    /**
     * Notifies that the theme for a specific user has changed and listeners should be updated.
     *
     * @param userId The ID of the user whose theme changed.
     */
    void notifyThemeChanged(int userId);

    /**
     * Called when wallpaper colors change.
     *
     * @param userId            The ID of the user whose wallpaper changed.
     * @param wallpaperColors   The new wallpaper colors.
     * @param fromForegroundApp Whether the change was triggered by a foreground app.
     */
    void onWallpaperColorsChanged(int userId, WallpaperColors wallpaperColors,
            boolean fromForegroundApp);

    /**
     * Called when the system contrast level changes.
     *
     * @param userId   The ID of the user.
     * @param contrast The new contrast level.
     */
    void onContrastChanged(int userId, float contrast);

    /**
     * Called when the device is locked.
     */
    void onDeviceLocked();

    /**
     * Called when a user's theme settings change.
     *
     * @param userId The ID of the user whose settings changed.
     */
    void onThemeSettingsChanged(int userId);
}
