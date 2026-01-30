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
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.UserIdInt;
import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.content.theming.FieldColorSource;
import android.content.theming.ThemeSettings;
import android.content.theming.ThemeStyle;
import android.database.ContentObserver;
import android.graphics.Color;
import android.net.Uri;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.VisibleForTesting;

import com.android.internal.os.BackgroundThread;
import com.android.server.SystemService;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;

/**
 * The ThemeService is a system service that manages the theming of the device.
 * It is responsible for loading and applying theme settings, and for notifying
 * other components of changes to the theme. It also handles the registration of
 * content observers for theme-related settings.
 */
@FlaggedApi(android.server.Flags.FLAG_ENABLE_THEME_SERVICE)
public class ThemeManagerService extends SystemService {
    private static final String TAG = "ThemeService";

    private final ThemeManagerInternal mInternal;
    private final ThemeBinderService mPublic;
    private final Context mContext;
    private final ThemeSettingsManager mThemeSettingsManager;
    private final Resources mResources;
    private final SystemPropertiesReader mSystemPropertiesReader;

    public ThemeManagerService(@NonNull Context context) {
        this(context, new SystemPropertiesReaderImpl());
    }

    @VisibleForTesting
    ThemeManagerService(@NonNull Context context, @NonNull SystemPropertiesReader systemPropertiesReader) {
        super(context);
        mContext = context;
        mResources = context.getResources();
        mThemeSettingsManager = new ThemeSettingsManager();
        mSystemPropertiesReader = systemPropertiesReader;
        ThemeSettings defaultSettings = createDefaultThemeSettings();

        mInternal = new ThemeManagerInternal(mContext, mThemeSettingsManager, defaultSettings);
        mPublic = new ThemeBinderService(mContext, mInternal);
    }

    @Override
    public void onStart() {
        publishLocalService(ThemeManagerInternal.class, mInternal);
        publishBinderService(Context.THEME_SERVICE, mPublic.asBinder());
    }

    @Override
    public void onBootPhase(@BootPhase int phase) {
        if (phase == SystemService.PHASE_SYSTEM_SERVICES_READY) {
            setupListeners();
        }
    }

    // HELPER METHODS

    private void setupListeners() {
        ContentResolver resolver = mContext.getContentResolver();
        Handler bgHandler = BackgroundThread.getHandler();

        // Style Change
        resolver.registerContentObserver(
                Settings.Secure.getUriFor(Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES),
                false, new ContentObserver(bgHandler) {
                    @Override
                    public void onChange(boolean selfChange, @NonNull Collection<Uri> uris,
                            int flags, @UserIdInt int userId) {
                        Context userContext = mContext.createContextAsUser(UserHandle.of(userId),
                                Context.CONTEXT_IGNORE_SECURITY);

                        // notifies other listeners of the Theme Settings
                        mInternal.notifySettingsChange(userId,
                                mThemeSettingsManager.readSettings(userId,
                                        userContext.getContentResolver()));
                    }
                }, UserHandle.USER_ALL);
    }

    @VisibleForTesting
    ThemeSettings createDefaultThemeSettings() {
        String deviceColorProperty = "ro.boot.hardware.color";
        String[] themeData = mResources.getStringArray(
                com.android.internal.R.array.theming_defaults);

        // The 'theming_defaults' resource is a string array where each entry is formatted as:
        // "hardware_color_name|STYLE_NAME|#hex_color_or_home_wallpaper"
        HashMap<String, Pair<Integer, String>> themeMap = new HashMap<>();
        for (String themeEntry : themeData) {
            String[] themeComponents = themeEntry.split("\\|");
            if (themeComponents.length != 3) {
                continue;
            }
            try {
                themeMap.put(themeComponents[0],
                        new Pair<>(ThemeStyle.valueOf(themeComponents[1]), themeComponents[2]));
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "Invalid style in theming_defaults: " + themeComponents[1], e);
            }
        }

        Pair<Integer, String> fallbackTheme = themeMap.get("*");
        if (fallbackTheme == null) {
            // This is a device configuration error. A wildcard fallback is required.
            throw new IllegalStateException("Theming resource 'theming_defaults' must contain a"
                    + " wildcard ('*') entry for fallback.");
        }

        String deviceColorPropertyValue = mSystemPropertiesReader.get(deviceColorProperty, "");
        Pair<Integer, String> styleAndSource = themeMap.get(deviceColorPropertyValue);
        if (styleAndSource == null) {
            Log.d(TAG, "Sysprop `" + deviceColorProperty + "` of value '"
                    + deviceColorPropertyValue
                    + "' not found in theming_defaults: " + Arrays.toString(themeData)
                    + ". Using wildcard fallback.");
            styleAndSource = fallbackTheme;
        }

        @ThemeStyle.Type int style = styleAndSource.first;
        String colorSourceString = styleAndSource.second;

        ThemeSettings.Builder builder = ThemeSettings.builder(0, style);

        if (FieldColorSource.VALUE_HOME_WALLPAPER.equals(colorSourceString)) {
            // The service's responsibility is to declare that the default is wallpaper-based.
            // We default to `colorBoth=true` as we can't know the state here.
            return builder.buildFromWallpaper(true);
        } else {
            // The default is a preset color. Parse it and create the setting. An invalid color
            // string here is a device configuration error and should cause a crash.
            Color seedColor = Color.valueOf(Color.parseColor(colorSourceString));
            return builder.buildFromPreset(seedColor, seedColor);
        }
    }

    private static class SystemPropertiesReaderImpl implements SystemPropertiesReader {
        @Override
        @NonNull
        public String get(@NonNull String key, @Nullable String def) {
            return android.os.SystemProperties.get(key, def);
        }
    }
}
