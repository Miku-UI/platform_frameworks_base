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

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.UserIdInt;
import android.content.theming.IThemeChangedCallback;
import android.content.theming.IThemeSettingsCallback;
import android.content.theming.ThemeInfo;
import android.content.theming.ThemeSettings;
import android.os.FabricatedOverlayInternal;

import java.io.PrintWriter;

/**
 * Interface for the internal implementation of the theme manager.
 *
 * @hide
 */
public interface ThemeManagerInternal {
    /**
     * Generates dynamic overlays based on the current theme state of the user.
     *
     * <p>This method allows other system services to generate dynamic overlays
     * with specific seed color, style, and contrast values. If any of these
     * parameters are null, the current values from the user's theme state
     * will be used. If the userId is not a full user, it will throw an exception.
     *
     * @param userId  The ID of the user whose current theme state will be used as a base
     *                for any unspecified theme properties in {@code options}.
     * @param options The {@link ThemeInfo} with the desired seed color, style, and contrast.
     * @return The generated {@link FabricatedOverlayInternal}.
     */
    @Nullable
    FabricatedOverlayInternal generateDynamicColorOverlay(@UserIdInt int userId,
            @NonNull ThemeInfo options);

    /**
     * Returns the current {@link ThemeInfo} for a given user.
     *
     * @param userId The ID of a Full User for whom to retrieve the theme information.
     * @return The {@link ThemeInfo} containing the user's current theme settings.
     */
    @Nullable
    ThemeInfo getUserThemeInfo(@UserIdInt int userId);

    /**
     * Registers a callback to receive notifications of theme settings changes.
     *
     * <p>This method allows clients to register an {@link IThemeSettingsCallback}
     * to be notified whenever the theme settings for the specified user are changed.
     *
     * @param userId The ID of a Full User to register the callback for.
     * @param cb     final IThemeSettingsCallback to register.
     * @return {@code true} if the callback was successfully registered, {@code false} otherwise.
     */
    boolean registerThemeSettingsCallback(@UserIdInt int userId,
            @NonNull IThemeSettingsCallback cb);

    /**
     * Unregisters a previously registered theme settings change callback.
     *
     * <p>This method allows clients to unregister an {@link IThemeSettingsCallback}
     * that was previously registered using
     * {@link #registerThemeSettingsCallback(int, IThemeSettingsCallback)}}.
     *
     * @param userId The ID of a Full User to unregister the callback from.
     * @param cb     The {@link IThemeSettingsCallback} to unregister.
     * @return {@code true} if the callback was successfully unregistered, {@code false} otherwise.
     */
    boolean unregisterThemeSettingsCallback(@UserIdInt int userId,
            @NonNull IThemeSettingsCallback cb);

    /**
     * Registers a callback for theme changed events.
     *
     * @param userId   The ID of a Full User to register the callback for.
     * @param callback The {@link IThemeChangedCallback}  to add.
     */
    boolean registerThemeChangedCallback(@UserIdInt int userId,
            @NonNull IThemeChangedCallback callback);

    /**
     * Unregisters a callback for theme changed events.
     *
     * @param userId   The ID of a Full User to unregister the callback from.
     * @param callback The {@link IThemeChangedCallback}  to remove.
     */
    boolean unregisterThemeChangedCallback(@UserIdInt int userId,
            @NonNull IThemeChangedCallback callback);

    /**
     * Updates the theme settings for the current user.
     *
     * <p>This method allows clients to update the theme settings for the current user. The
     * provided {@link ThemeSettings} object should contain the new theme settings. Any
     * settings not explicitly set in the {@link ThemeSettings} object will remain unchanged.
     *
     * <p>Use the {@link ThemeSettings}'s builder() to construct {@link ThemeSettings} objects.
     *
     * @param userId      The user ID to update theme settings for.
     * @param newSettings The {@link ThemeSettings} object containing the new theme settings.
     *                    If the userId is not a full user, it will throw an exception.
     * @return {@code true} if the settings were successfully updated, {@code false} otherwise.
     */
    boolean updateThemeSettings(@UserIdInt int userId, ThemeSettings newSettings);

    /**
     * Retrieves the theme settings for the specified user.
     *
     * <p>This method allows clients to retrieve the current theme settings for a given user.
     *
     * @param userId The ID of a Full User to retrieve theme settings for.
     * @return The {@link ThemeSettings} object containing the current theme settings,
     * or {@code null} if an error occurs or no settings are found.
     */
    @Nullable
    ThemeSettings getThemeSettings(@UserIdInt int userId);

    /**
     * Retrieves the theme settings for the specified user, or the default settings if no
     * custom settings are found.
     *
     * @param userId The ID of a Full User to retrieve theme settings for.
     * @return The {@link ThemeSettings} object containing the current theme settings,
     * or the default settings if no custom settings are found.
     */
    @Nullable
    ThemeSettings getThemeSettingsOrDefault(@UserIdInt int userId);

    /**
     * Dumps the current state of the ThemeManager service.
     *
     * @param pw The PrintWriter to dump the state to.
     */
    void dump(PrintWriter pw);
}
