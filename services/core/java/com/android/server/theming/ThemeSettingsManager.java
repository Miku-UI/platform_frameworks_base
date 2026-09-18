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

import static android.content.theming.FieldColorSource.VALUE_HOME_WALLPAPER;
import static android.content.theming.FieldColorSource.VALUE_PRESET;

import android.annotation.FlaggedApi;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.UserIdInt;
import android.app.WallpaperManager;
import android.content.ContentResolver;
import android.content.theming.FieldColor;
import android.content.theming.FieldColorList;
import android.content.theming.FieldColorSource;
import android.content.theming.FieldThemeStyle;
import android.content.theming.ThemeSettings;
import android.content.theming.ThemeSettingsField;
import android.content.theming.ThemeStyle;
import android.graphics.Color;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Pair;
import android.util.Slog;
import android.util.SparseArray;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;

import org.json.JSONException;
import org.json.JSONObject;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages the loading, saving, and caching of theme settings.
 * This class handles the persistence of theme settings to and from the system settings,
 * and maintains an in-memory cache for quick access.
 *
 * @hide
 */
@FlaggedApi(android.server.Flags.FLAG_ENABLE_THEME_SERVICE)
@VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
public class ThemeSettingsManager {
    private static final String TAG = "ThemeSettingsManager";

    static final String TIMESTAMP = "_applied_timestamp";
    private static final String KEY_PREFIX = "android.theme.customization.";
    static final String OVERLAY_CATEGORY_ACCENT_COLOR = KEY_PREFIX + "accent_color";
    static final String OVERLAY_CATEGORY_SYSTEM_PALETTE = KEY_PREFIX + "system_palette";
    static final String OVERLAY_SEED_COLOR_LIST = KEY_PREFIX + "seed_color_list";
    static final String OVERLAY_CATEGORY_THEME_STYLE = KEY_PREFIX + "theme_style";
    static final String OVERLAY_COLOR_SOURCE = KEY_PREFIX + "color_source";

    private final ThemeWallpaperManager mWallpaperManager;
    private final ThemeConfig mConfig;

    private ThemeSettings mDeviceDefaultSettings;

    static final Map<String, ThemeSettingsField<?, ?>> ALL_FIELDS = Map.ofEntries(
            Map.entry(OVERLAY_CATEGORY_SYSTEM_PALETTE, new FieldColor()),
            Map.entry(OVERLAY_CATEGORY_ACCENT_COLOR, new FieldColor()),
            Map.entry(OVERLAY_SEED_COLOR_LIST, new FieldColorList()),
            Map.entry(OVERLAY_COLOR_SOURCE, new FieldColorSource()),
            Map.entry(OVERLAY_CATEGORY_THEME_STYLE, new FieldThemeStyle()));

    private final Object mLock = new Object();

    @GuardedBy("mLock")
    private final SparseArray<ThemeSettings> mSettingsCache = new SparseArray<>();

    ThemeSettingsManager(ThemeWallpaperManager wallpaperManager,
            ThemeConfig config) {
        mWallpaperManager = wallpaperManager;
        mConfig = config;
    }

    /**
     * Resolves the device-wide default theme settings once.
     */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
    public void initializeDefaults() {
        if (mDeviceDefaultSettings != null) return;
        mDeviceDefaultSettings = resolveDeviceDefaultSettings();
        Slog.i(TAG, "Device default theme resolved: " + mDeviceDefaultSettings);
    }

    /**
     * Retrieves the theme settings for the specified user, utilizing an in-memory cache.
     *
     * @param userId          The ID of the user.
     * @param contentResolver The content resolver to use if reading from disk is necessary.
     * @return The {@link ThemeSettings} for the user, or null if none exist.
     */
    @Nullable
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
    public ThemeSettings getSettings(@UserIdInt int userId,
            @NonNull ContentResolver contentResolver) {
        synchronized (mLock) {
            int idx = mSettingsCache.indexOfKey(userId);
            if (idx >= 0) {
                return mSettingsCache.valueAt(idx);
            }
        }

        // Read from disk outside the lock to avoid blocking other users if I/O is slow.
        ThemeSettings settings = readFromDisk(userId, contentResolver);

        synchronized (mLock) {
            mSettingsCache.put(userId, settings);
        }

        return settings;
    }

    /**
     * Retrieves the theme settings for the specified user, or the default settings if no
     * custom settings are found.
     *
     * @param userId          The ID of a Full User to retrieve theme settings for.
     * @param contentResolver The content resolver to use.
     * @return The {@link ThemeSettings} object containing the current or default theme settings.
     */
    @NonNull
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
    public ThemeSettings getSettingsOrDefault(int userId, ContentResolver contentResolver) {
        ThemeSettings storedSettings = getSettings(userId, contentResolver);
        if (storedSettings != null) {
            return storedSettings;
        }
        return createDefaultThemeSettings(userId);
    }

    /**
     * Saves the specified theme settings for the given user to persistent storage and updates
     * the cache.
     *
     * @param userId          The ID of the user.
     * @param contentResolver The content resolver to use.
     * @param newSettings     The {@link ThemeSettings} to save.
     * @return true if the settings were successfully written to storage.
     */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
    public boolean setSettings(@UserIdInt int userId, @NonNull ContentResolver contentResolver,
            @NonNull ThemeSettings newSettings) {

        boolean success = writeToDisk(userId, contentResolver, newSettings);

        if (!success) return false;

        // Update cache immediately so subsequent reads see the new value.
        synchronized (mLock) {
            mSettingsCache.put(userId, newSettings);
        }

        return true;
    }

    /**
     * Invalidates the settings cache for a specific user.
     * This forces the next getSettings call to read from disk.
     */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
    public void invalidateCache(@UserIdInt int userId) {
        synchronized (mLock) {
            mSettingsCache.remove(userId);
        }
    }

    @Nullable
    private ThemeSettings readFromDisk(@UserIdInt int userId, ContentResolver contentResolver) {
        try {
            final String jsonString = Settings.Secure.getStringForUser(contentResolver,
                    Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES, userId);
            Pair<ThemeSettings, Boolean> result = fromJson(jsonString, userId);
            if (result == null) return null;

            if (result.second) {
                // If the settings were migrated during read, persist them immediately.
                writeToDisk(userId, contentResolver, result.first);
                Slog.d(TAG, "Persisted migrated theme settings for user " + userId);
            }
            return result.first;
        } catch (Exception e) {
            Slog.w(TAG, "Error loading theme settings for user " + userId + ": " + e);
            return null;
        }
    }

    private boolean writeToDisk(@UserIdInt int userId, ContentResolver contentResolver,
            ThemeSettings newSettings) {
        try {
            final String oldJsonString = Settings.Secure.getStringForUser(contentResolver,
                    Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES, userId);
            final String newJsonString = toJson(newSettings, oldJsonString);
            return Settings.Secure.putStringForUser(contentResolver,
                    Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES, newJsonString,
                    userId);
        } catch (Exception e) {
            Slog.w(TAG, "Error writing theme settings for user " + userId + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Creates the default theme settings for a given user based on device configuration.
     *
     * @param userId The ID of the user.
     * @return The default theme settings.
     */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
    public ThemeSettings createDefaultThemeSettings(@UserIdInt int userId) {
        // Ensure defaults are resolved
        initializeDefaults();

        // If the device default is a fixed preset, we can share it across all users.
        if (mDeviceDefaultSettings.colorSource().equals(VALUE_PRESET)) {
            return mDeviceDefaultSettings;
        }

        // If the device default is "home_wallpaper", we MUST resolve the seed for this specific
        // user.
        List<Integer> wallpaperSeeds = mWallpaperManager.getSeedColors(
                mWallpaperManager.getWallpaperColors(WallpaperManager.FLAG_SYSTEM, userId), true);

        if (wallpaperSeeds.isEmpty()) {
            // If wallpaper isn't ready, use the "stored" version (which uses hardcoded fallback)
            return mDeviceDefaultSettings;
        }

        List<Color> paletteList = wallpaperSeeds.stream().map(Color::valueOf).toList();

        return new ThemeSettings.Builder()
                .setThemeStyle(mDeviceDefaultSettings.themeStyle())
                .setColorSource(VALUE_HOME_WALLPAPER)
                .setSeedColors(paletteList)
                .build();
    }

    private ThemeSettings resolveDeviceDefaultSettings() {
        String deviceColorProperty = "ro.boot.hardware.color";

        HashMap<String, Pair<Integer, String>> themeMap = new HashMap<>();
        for (String themeEntry : mConfig.defaultThemeData()) {
            String[] themeComponents = themeEntry.split("\\|");
            if (themeComponents.length != 3) {
                continue;
            }
            try {
                themeMap.put(themeComponents[0],
                        new Pair<>(ThemeStyle.valueOf(themeComponents[1]), themeComponents[2]));
            } catch (IllegalArgumentException e) {
                Slog.w(TAG, "Invalid style in theming_defaults: " + themeComponents[1], e);
            }
        }

        Pair<Integer, String> fallbackTheme = themeMap.get("*");
        if (fallbackTheme == null) {
            throw new IllegalStateException("Theming resource 'theming_defaults' must contain a"
                    + " wildcard ('*') entry for fallback.");
        }

        String deviceColorPropertyValue = mConfig.hardwareColorCode();
        Pair<Integer, String> styleAndSource = themeMap.get(deviceColorPropertyValue);
        if (styleAndSource == null) {
            Slog.d(TAG, "Sysprop `" + deviceColorProperty + "` of value '"
                    + deviceColorPropertyValue
                    + "' not found in theming_defaults: " + Arrays.toString(
                    mConfig.defaultThemeData())
                    + ". Using wildcard fallback.");
            styleAndSource = fallbackTheme;
        }

        try {
            return buildSettingsFromConfig(styleAndSource, UserHandle.USER_SYSTEM);
        } catch (Exception e) {
            Slog.w(TAG, "Failed to build from device config, trying wildcard.", e);
            try {
                return buildSettingsFromConfig(fallbackTheme, UserHandle.USER_SYSTEM);
            } catch (Exception e2) {
                Slog.e(TAG, "Wildcard also failed! Using hardcoded.", e2);
                return mConfig.hardcodedFallback();
            }
        }
    }

    private ThemeSettings buildSettingsFromConfig(Pair<Integer, String> config,
            @UserIdInt int userId) {
        @ThemeStyle.Type int style = config.first;
        String colorSourceString = config.second;
        List<Color> seedColors = new ArrayList<>();
        String colorSource;

        if (colorSourceString.equals(VALUE_HOME_WALLPAPER)) {
            List<Integer> wallpaperSeeds = mWallpaperManager.getSeedColors(
                    mWallpaperManager.getWallpaperColors(WallpaperManager.FLAG_SYSTEM, userId),
                    true);
            if (wallpaperSeeds.isEmpty()) {
                Slog.i(TAG, "User's " + userId + " Wallpaper colors not yet available. "
                        + "Using fallback palette for HOME_WALLPAPER source.");
                seedColors.addAll(mConfig.hardcodedFallback().seedColors());
            } else {
                for (int seed : wallpaperSeeds) {
                    seedColors.add(Color.valueOf(seed));
                }
            }
            colorSource = VALUE_HOME_WALLPAPER;
        } else {
            String[] colorStrings = colorSourceString.split(",");
            for (String colorStr : colorStrings) {
                seedColors.add(Color.valueOf(Color.parseColor(colorStr.trim())));
            }
            colorSource = VALUE_PRESET;
        }

        return new ThemeSettings.Builder()
                .setThemeStyle(style)
                .setColorSource(colorSource)
                .setSeedColors(seedColors)
                .build();
    }

    private static <T, J> T parseAndValidate(@NonNull JSONObject json, @NonNull String key,
            @Nullable T defaultValue) throws JSONException {
        if (!json.has(key)) {
            return defaultValue;
        }

        ThemeSettingsField<T, J> handler = (ThemeSettingsField<T, J>) ALL_FIELDS.get(key);
        if (handler == null) {
            // This can happen for unknown fields, which is fine. We just can't parse them.
            return defaultValue;
        }

        Object primitive = json.get(key);
        // Cast to J, the expected JSON primitive type for the handler.
        T parsedValue = handler.parse((J) primitive);

        if (parsedValue != null && !handler.validate(parsedValue)) {
            throw new IllegalArgumentException("Invalid value for key '" + key + "': " + primitive);
        }

        return parsedValue;
    }

    @Nullable
    private Pair<ThemeSettings, Boolean> fromJson(@Nullable String jsonString,
            @UserIdInt int userId)
            throws JSONException {
        if (TextUtils.isEmpty(jsonString)) {
            return null;
        }
        JSONObject json = new JSONObject(jsonString);

        Instant timestamp;
        if (json.has(TIMESTAMP)) {
            timestamp = Instant.ofEpochMilli(json.getLong(TIMESTAMP));
        } else {
            Slog.w(TAG, "JSON missing timestamp, using current time as fallback.");
            timestamp = Instant.EPOCH;
        }

        int themeStyle = parseAndValidate(json, OVERLAY_CATEGORY_THEME_STYLE,
                ThemeStyle.TONAL_SPOT);
        String colorSource = parseAndValidate(json, OVERLAY_COLOR_SOURCE, VALUE_HOME_WALLPAPER);

        List<Color> seedColors = parseAndValidate(json, OVERLAY_SEED_COLOR_LIST, null);
        Color legacyPrimary = parseAndValidate(json, OVERLAY_CATEGORY_SYSTEM_PALETTE, null);
        Color legacySecondary = parseAndValidate(json, OVERLAY_CATEGORY_ACCENT_COLOR, null);

        boolean migrated = false;

        // We don't have a list, we are migrating previous json format
        if (seedColors == null) {
            migrated = true;

            seedColors = new ArrayList<>();
            // Repair logic: if one legacy color is missing, use the other.
            if (legacyPrimary == null && legacySecondary != null) {
                legacyPrimary = legacySecondary;
            } else if (legacySecondary == null && legacyPrimary != null) {
                legacySecondary = legacyPrimary;
            }

            if (legacyPrimary == null && VALUE_HOME_WALLPAPER.equals(colorSource)) {
                List<Integer> seeds = mWallpaperManager.getSeedColors(
                        mWallpaperManager.getWallpaperColors(WallpaperManager.FLAG_SYSTEM, userId),
                        true);
                if (!seeds.isEmpty()) {
                    for (int seed : seeds) {
                        seedColors.add(Color.valueOf(seed));
                    }
                } else {
                    legacyPrimary = mConfig.hardcodedFallback().seedColors().getFirst();
                    Slog.d(TAG, "Legacy settings for user " + userId + " missing palette. "
                            + "Wallpaper color missing, using fallback.");
                }
            }

            if (seedColors.isEmpty() && legacyPrimary != null) {
                seedColors.add(legacyPrimary);
                if (legacySecondary != null && !legacySecondary.equals(legacyPrimary)) {
                    seedColors.add(legacySecondary);
                }
            }
        } else {
            // We do have a list, but let's check if its values are outdated comparing with legacy
            // Check if legacy keys were updated externally and need to override the list.
            if (legacyPrimary != null && !legacyPrimary.equals(seedColors.get(0))) {
                seedColors.set(0, legacyPrimary);
                migrated = true;
            }
            if (legacySecondary != null) {
                Color expectedAccent = seedColors.size() > 1 ? seedColors.get(1)
                        : seedColors.get(0);
                if (!legacySecondary.equals(expectedAccent)) {
                    if (seedColors.size() > 1) {
                        seedColors.set(1, legacySecondary);
                    } else {
                        seedColors.add(1, legacySecondary);
                    }
                    migrated = true;
                }
            }
        }

        ThemeSettings settings = new ThemeSettings.Builder()
                .setAppliedTimestamp(timestamp)
                .setThemeStyle(themeStyle)
                .setColorSource(colorSource)
                .setSeedColors(seedColors)
                .build();

        return new Pair<>(settings, migrated);
    }

    @NonNull
    private String toJson(@NonNull ThemeSettings settings, @Nullable String oldJsonString) {
        JSONObject json;
        try {
            // Start with the original JSON data to preserve unknown fields.
            json = TextUtils.isEmpty(oldJsonString)
                    ? new JSONObject() : new JSONObject(oldJsonString);
        } catch (JSONException e) {
            Slog.w(TAG, "Failed to parse existing settings, overwriting", e);
            json = new JSONObject();
        }

        try {
            // Update the known fields with the current values.
            json.put(TIMESTAMP, settings.timeStamp().toEpochMilli());
            putSetting(json, OVERLAY_CATEGORY_THEME_STYLE, settings.themeStyle());
            putSetting(json, OVERLAY_COLOR_SOURCE, settings.colorSource());

            List<Color> palettes = settings.seedColors();
            putSetting(json, OVERLAY_SEED_COLOR_LIST, palettes);

            Color primary = palettes.get(0);
            putSetting(json, OVERLAY_CATEGORY_SYSTEM_PALETTE, primary);

            // For backward compatibility, write accent_color as well if color source is preset,
            if (settings.colorSource().equals(VALUE_PRESET)) {
                // If we have a second seed, it goes to accent_color. Otherwise, primary.
                Color secondary = palettes.size() > 1 ? palettes.get(1) : primary;
                putSetting(json, OVERLAY_CATEGORY_ACCENT_COLOR, secondary);
            }

            return json.toString();
        } catch (JSONException e) {
            // This should not happen with controlled inputs.
            throw new RuntimeException("Error creating JSON for ThemeSettings", e);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> void putSetting(JSONObject json, String key, T value) throws JSONException {
        if (value == null) {
            json.remove(key);
            return;
        }
        ThemeSettingsField<T, ?> field = (ThemeSettingsField<T, ?>) ALL_FIELDS.get(key);
        json.put(key, field.serialize(value));
    }
}
