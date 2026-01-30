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

package android.app;

import android.annotation.FlaggedApi;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.RequiresPermission;
import android.annotation.SystemService;
import android.content.Context;
import android.content.theming.IThemeManager;
import android.content.theming.IThemeSettingsCallback;
import android.content.theming.ThemeSettings;
import android.os.RemoteException;
import android.os.ServiceManager;


/**
 * Provides access to the system Theme Service.
 *
 * <p>This class allows applications to interact with the system's Theme Service, enabling them to
 * register for theme settings change notifications, update theme settings, and retrieve the
 * current theme settings.
 *
 * <p>Theme Settings are managed on a per-user basis. This means that all operations performed
 * through this class are scoped to the user associated with the context from which this
 * {@link ThemeManager} instance was obtained.
 *
 * <p>To obtain an instance of this class, use {@link Context#getSystemService(Class)} with
 * {@link ThemeManager}.
 *
 * @hide
 */
@SystemService(Context.THEME_SERVICE)
@FlaggedApi(android.server.Flags.FLAG_ENABLE_THEME_SERVICE)
public class ThemeManager {
    private final IThemeManager mService;

    /**
     * @hide
     */
    public ThemeManager() {
        try {
            mService = IThemeManager.Stub.asInterface(
                    ServiceManager.getServiceOrThrow(Context.THEME_SERVICE));
        } catch (ServiceManager.ServiceNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Registers a callback to receive notifications of theme settings changes.
     *
     * <p>This method allows clients to register an {@link IThemeSettingsCallback}
     * to be notified whenever the theme settings for the current user are changed.
     *
     * @param callback The {@link IThemeSettingsCallback} to register.
     * @return {@code true} if the callback was successfully registered, {@code false} otherwise.
     * @hide
     */
    public boolean registerThemeSettingsCallback(@NonNull IThemeSettingsCallback callback) {
        try {
            return mService.registerThemeSettingsCallback(callback);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Unregisters a previously registered theme settings change callback.
     *
     * <p>This method allows clients to unregister an {@link IThemeSettingsCallback}
     * that was previously registered using
     * {@link #registerThemeSettingsCallback(IThemeSettingsCallback)}.
     *
     * @param callback The {@link IThemeSettingsCallback} to unregister.
     * @return {@code true} if the callback was successfully unregistered, {@code false} otherwise.
     */
    public boolean unregisterThemeSettingsCallback(@NonNull IThemeSettingsCallback callback) {
        try {
            return mService.unregisterThemeSettingsCallback(callback);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Updates the theme settings for the current user.
     *
     * <p>This method allows clients to update the theme settings for the current user.
     * The provided {@link ThemeSettings} object should contain the new theme settings.
     * Any settings not explicitly set in the {@link ThemeSettings} object will remain unchanged.
     * Specifically, any null properties within the provided {@link ThemeSettings} object
     * will be skipped, and the corresponding existing theme settings will be preserved.
     *
     * <p>It is recommended to use the {@link android.content.theming.ThemeSettings} to
     * construct
     * {@link ThemeSettings} objects, especially when only updating a subset of theme properties.
     * This ensures that only the intended properties are modified, and avoids accidentally
     * resetting other settings to default or null values.
     *
     * @param newSettings The {@link ThemeSettings} object containing the new theme settings.
     * @hide
     */
    @RequiresPermission(android.Manifest.permission.UPDATE_THEME_SETTINGS)
    public boolean updateThemeSettings(@NonNull ThemeSettings newSettings) {
        try {
            return mService.updateThemeSettings(newSettings);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Retrieves the theme settings for the current user.
     *
     * <p>This method allows clients to retrieve the current theme settings the calling user.
     *
     * @return The {@link ThemeSettings} object containing the current theme settings,
     * or {@code null} if an error occurs or no settings are found.
     * @hide
     */
    @Nullable
    public ThemeSettings getThemeSettings() {
        try {
            return mService.getThemeSettings();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Retrieves the theme settings for the current user, or the system default if none is set.
     *
     * <p>This method allows clients to retrieve the current theme settings for the calling user.
     * If no theme has been explicitly set, it returns a system-generated default. This method
     * will never return {@code null}.
     *
     * @return The non-null {@link ThemeSettings} object containing the current or default theme
     * settings.
     * @hide
     */
    @NonNull
    public ThemeSettings getThemeSettingsOrDefault() {
        try {
            return mService.getThemeSettingsOrDefault();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }
}
