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

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import android.app.ActivityManagerInternal;
import android.app.WallpaperColors;
import android.app.WallpaperManager;
import android.content.ContentResolver;
import android.content.theming.FieldColorSource;
import android.content.theming.ThemeSettings;
import android.content.theming.ThemeStyle;
import android.graphics.Color;
import android.provider.Settings;
import android.testing.TestableContext;
import android.testing.TestableResources;

import androidx.test.runner.AndroidJUnit4;

import com.android.internal.R;
import com.android.server.LocalServices;
import com.android.server.pm.UserManagerInternal;
import com.android.server.wallpaper.WallpaperManagerInternal;
import com.android.systemui.monet.ColorScheme;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Instant;

@RunWith(AndroidJUnit4.class)
public class ThemeSettingsManagerTests {
    private final int mUserId = 0;

    @Rule
    public final HardwareColorRule mHardwareColorRule = new HardwareColorRule();

    @Mock
    private WallpaperManagerInternal mMockWmi;
    @Mock
    private ActivityManagerInternal mMockAmi;
    @Mock
    private UserManagerInternal mMockUmi;

    @Rule
    public final TestableContext mContext = new TestableContext(
            getInstrumentation().getTargetContext(), null);

    private ContentResolver mContentResolver;
    private ThemeSettingsManager mManager;
    private ThemeEnvironment mEnvironment;

    private static final String UNKNOWN_FIELDS_JSON = """
                    {
                      "_applied_timestamp": 1749626671504,
                      "android.theme.customization.color_source": "preset",
                      "android.theme.customization.theme_style": "TONAL_SPOT",
                      "android.theme.customization.system_palette": "FF1A73E8",
                      "exotic_property": "some_value",
                      "another_one": { "nested": true }
                    }
            """;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        mContentResolver = mContext.getContentResolver();

        TestableResources userResources = mContext.getOrCreateTestableResources();
        userResources.addOverride(R.array.theming_defaults, mHardwareColorRule.options);

        when(mMockUmi.isHeadlessSystemUserMode()).thenReturn(false);

        mEnvironment = new ThemeEnvironment(mContext, mHardwareColorRule.sysPropReader);

        // We need to register WallpaperManagerInternal for ThemeWallpaperManager
        LocalServices.removeServiceForTest(WallpaperManagerInternal.class);
        LocalServices.addService(WallpaperManagerInternal.class, mMockWmi);

        mManager = new ThemeSettingsManager(new ThemeWallpaperManager(), mEnvironment.getConfig());

        Settings.Secure.putStringForUser(mContentResolver,
                Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES, null, mUserId);
    }

    @Test
    public void getSettings_noSettings_returnsNull() {
        ThemeSettings settings = mManager.getSettings(mUserId, mContentResolver);
        assertThat(settings).isNull();
    }

    @Test
    public void getSettings_emptyJSON_returnsFallback() {
        Settings.Secure.putStringForUser(mContentResolver,
                Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES, "{}", mUserId);
        ThemeSettings settings = mManager.getSettings(mUserId, mContentResolver);

        assertThat(settings).isNotNull();
        assertThat(settings.colorSource()).isEqualTo(FieldColorSource.VALUE_HOME_WALLPAPER);
        assertThat(settings.themeStyle()).isEqualTo(ThemeStyle.TONAL_SPOT);
    }

    @Test
    public void getSettings_invalidJSON_returnsNull() {
        Settings.Secure.putStringForUser(mContentResolver,
                Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES, "{invalid_json", mUserId);
        ThemeSettings settings = mManager.getSettings(mUserId, mContentResolver);
        assertThat(settings).isNull();
    }

    @Test
    public void setSettings_writesPresetToProvider() throws Exception {
        long currentTime = System.currentTimeMillis();
        ThemeSettings presetSettings = new ThemeSettings.Builder()
                .setThemeStyle(ThemeStyle.MONOCHROMATIC)
                .setColorSource(FieldColorSource.VALUE_PRESET)
                .setSeedColors(Color.valueOf(0xFF112233))
                .build();

        boolean success = mManager.setSettings(mUserId, mContentResolver, presetSettings);
        assertThat(success).isTrue();

        String settingsString = Settings.Secure.getStringForUser(mContentResolver,
                Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES, mUserId);

        JSONObject settingsJson = new JSONObject(settingsString);

        assertThat(settingsJson.has(ThemeSettingsManager.TIMESTAMP)).isTrue();
        assertThat(settingsJson.getLong(ThemeSettingsManager.TIMESTAMP)).isAtLeast(currentTime);
        assertThat(settingsJson.getString(ThemeSettingsManager.OVERLAY_CATEGORY_SYSTEM_PALETTE))
                .isEqualTo("FF112233");
        // For backward compatibility, accent_color must be written with the same value.
        assertThat(settingsJson.getString(ThemeSettingsManager.OVERLAY_CATEGORY_ACCENT_COLOR))
                .isEqualTo("FF112233");
        assertThat(settingsJson.getString(ThemeSettingsManager.OVERLAY_COLOR_SOURCE))
                .isEqualTo(FieldColorSource.VALUE_PRESET);
        assertThat(settingsJson.getString(ThemeSettingsManager.OVERLAY_CATEGORY_THEME_STYLE))
                .isEqualTo(ThemeStyle.toString(ThemeStyle.MONOCHROMATIC));

        // Now has seed_color_list
        assertThat(settingsJson.has(ThemeSettingsManager.OVERLAY_SEED_COLOR_LIST)).isTrue();
        assertThat(settingsJson.length()).isEqualTo(6);
    }

    @Test
    public void setSettings_writesMultiSeedToProvider() throws Exception {
        ThemeSettings multiSeedSettings = new ThemeSettings.Builder()
                .setThemeStyle(ThemeStyle.VIBRANT)
                .setColorSource(FieldColorSource.VALUE_PRESET)
                .setSeedColors(Color.valueOf(Color.RED), Color.valueOf(Color.BLUE))
                .build();

        boolean success = mManager.setSettings(mUserId, mContentResolver, multiSeedSettings);
        assertThat(success).isTrue();

        String settingsString = Settings.Secure.getStringForUser(mContentResolver,
                Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES, mUserId);
        JSONObject settingsJson = new JSONObject(settingsString);

        assertThat(settingsJson.getString(ThemeSettingsManager.OVERLAY_CATEGORY_SYSTEM_PALETTE))
                .isEqualTo("FFFF0000");
        assertThat(settingsJson.getString(ThemeSettingsManager.OVERLAY_CATEGORY_ACCENT_COLOR))
                .isEqualTo("FF0000FF");

        JSONArray list = settingsJson.getJSONArray(ThemeSettingsManager.OVERLAY_SEED_COLOR_LIST);
        assertThat(list.length()).isEqualTo(2);
        assertThat(list.getString(0)).isEqualTo("FFFF0000");
        assertThat(list.getString(1)).isEqualTo("FF0000FF");
    }

    @Test
    public void setSettings_writesWallpaperToProvider() throws Exception {
        ThemeSettings wallpaperSettings = new ThemeSettings.Builder()
                .setThemeStyle(ThemeStyle.VIBRANT)
                .setColorSource(FieldColorSource.VALUE_HOME_WALLPAPER)
                .setSeedColors(Color.valueOf(Color.BLUE))
                .build();

        boolean success = mManager.setSettings(mUserId, mContentResolver, wallpaperSettings);
        assertThat(success).isTrue();

        String settingsString = Settings.Secure.getStringForUser(mContentResolver,
                Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES, mUserId);
        JSONObject settingsJson = new JSONObject(settingsString);

        assertThat(settingsJson.has(ThemeSettingsManager.TIMESTAMP)).isTrue();
        assertThat(settingsJson.getLong(ThemeSettingsManager.TIMESTAMP)).isEqualTo(
                wallpaperSettings.timeStamp().toEpochMilli());
        assertThat(settingsJson.getString(ThemeSettingsManager.OVERLAY_COLOR_SOURCE)).isEqualTo(
                FieldColorSource.VALUE_HOME_WALLPAPER);
        assertThat(settingsJson.getString(
                ThemeSettingsManager.OVERLAY_CATEGORY_THEME_STYLE)).isEqualTo(
                ThemeStyle.toString(ThemeStyle.VIBRANT));

        assertThat(settingsJson.has(ThemeSettingsManager.OVERLAY_CATEGORY_SYSTEM_PALETTE)).isTrue();
        assertThat(settingsJson.has(ThemeSettingsManager.OVERLAY_CATEGORY_ACCENT_COLOR)).isFalse();
        assertThat(settingsJson.length()).isEqualTo(5);
    }

    @Test
    public void setAndGetSettings_persistsAndReadsCorrectly() {
        // Test Wallpaper case
        ThemeSettings originalWallpaper = new ThemeSettings.Builder()
                .setThemeStyle(ThemeStyle.EXPRESSIVE)
                .setColorSource(FieldColorSource.VALUE_HOME_WALLPAPER)
                .setSeedColors(Color.valueOf(Color.RED))
                .build();

        mManager.setSettings(mUserId, mContentResolver, originalWallpaper);
        ThemeSettings loadedWallpaper = mManager.getSettings(mUserId, mContentResolver);

        assertThat(loadedWallpaper).isNotNull();
        assertThat(loadedWallpaper.timeStamp().toEpochMilli()).isEqualTo(
                originalWallpaper.timeStamp().toEpochMilli());
        assertThat(loadedWallpaper.themeStyle()).isEqualTo(originalWallpaper.themeStyle());
        assertThat(loadedWallpaper.colorSource()).isEqualTo(originalWallpaper.colorSource());
        assertThat(loadedWallpaper.seedColors()).isEqualTo(originalWallpaper.seedColors());

        // Test Multi-Seed case
        ThemeSettings originalMulti = new ThemeSettings.Builder()
                .setThemeStyle(ThemeStyle.SPRITZ)
                .setColorSource(FieldColorSource.VALUE_PRESET)
                .setSeedColors(Color.valueOf(Color.GREEN), Color.valueOf(Color.BLUE),
                        Color.valueOf(Color.RED))
                .build();

        mManager.setSettings(mUserId, mContentResolver, originalMulti);
        ThemeSettings loadedMulti = mManager.getSettings(mUserId, mContentResolver);

        assertThat(loadedMulti).isNotNull();
        assertThat(loadedMulti.seedColors()).hasSize(3);
        assertThat(loadedMulti.seedColors().get(0)).isEqualTo(Color.valueOf(Color.GREEN));
        assertThat(loadedMulti.seedColors().get(1)).isEqualTo(Color.valueOf(Color.BLUE));
        assertThat(loadedMulti.seedColors().get(2)).isEqualTo(Color.valueOf(Color.RED));
    }

    @Test
    public void setAndGetSettings_preservesUnknownFields() throws Exception {
        // Manually write JSON with unknown fields
        Settings.Secure.putStringForUser(mContentResolver,
                Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES, UNKNOWN_FIELDS_JSON, mUserId);

        // Read settings, then write them back without modification
        ThemeSettings settings = mManager.getSettings(mUserId, mContentResolver);
        assertThat(settings).isNotNull();
        mManager.setSettings(mUserId, mContentResolver, settings);

        // Read the raw JSON again and verify unknown fields are still present
        String finalJsonString = Settings.Secure.getStringForUser(mContentResolver,
                Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES, mUserId);
        JSONObject finalJson = new JSONObject(finalJsonString);

        assertThat(finalJson.getString("exotic_property")).isEqualTo("some_value");
        assertThat(finalJson.getJSONObject("another_one").getBoolean("nested")).isTrue();
        // Verify known fields are also correct
        assertThat(finalJson.getString(ThemeSettingsManager.OVERLAY_COLOR_SOURCE)).isEqualTo(
                "preset");
    }

    @Test
    @HardwareColors(color = "RED_DEV", options = {
            "RED_DEV|VIBRANT|#FF0000",
            "*|TONAL_SPOT|#00FF00"
    })
    public void createDefaultThemeSettings_matchesHardwareColor() {
        ThemeSettings defaultSettings = mManager.createDefaultThemeSettings(mUserId);

        assertThat(defaultSettings.colorSource()).isEqualTo(FieldColorSource.VALUE_PRESET);
        assertThat(defaultSettings.themeStyle()).isEqualTo(ThemeStyle.VIBRANT);
        assertThat(defaultSettings.seedColors().getFirst().toArgb()).isEqualTo(
                Color.parseColor("#FFFF0000"));
    }

    @Test
    @HardwareColors(color = "MULTI_DEV", options = {
            "MULTI_DEV|EXPRESSIVE|#FF0000,#0000FF",
            "*|TONAL_SPOT|#00FF00"
    })
    public void createDefaultThemeSettings_multiSeedPreset() {
        ThemeSettings defaultSettings = mManager.createDefaultThemeSettings(mUserId);

        assertThat(defaultSettings.colorSource()).isEqualTo(FieldColorSource.VALUE_PRESET);
        assertThat(defaultSettings.themeStyle()).isEqualTo(ThemeStyle.EXPRESSIVE);
        assertThat(defaultSettings.seedColors()).hasSize(2);
        assertThat(defaultSettings.seedColors().get(0)).isEqualTo(Color.valueOf(Color.RED));
        assertThat(defaultSettings.seedColors().get(1)).isEqualTo(Color.valueOf(Color.BLUE));
    }

    @Test
    @HardwareColors(color = "BLUE_DEV", options = {
            "RED_DEV|VIBRANT|#FF0000",
            "*|TONAL_SPOT|#00FF00"
    })
    public void createDefaultThemeSettings_usesWildcardFallback_preset() {
        ThemeSettings defaultSettings = mManager.createDefaultThemeSettings(mUserId);

        assertThat(defaultSettings.colorSource()).isEqualTo(FieldColorSource.VALUE_PRESET);
        assertThat(defaultSettings.themeStyle()).isEqualTo(ThemeStyle.TONAL_SPOT);
        assertThat(defaultSettings.seedColors().getFirst().toArgb()).isEqualTo(
                Color.parseColor("#FF00FF00"));
    }

    @Test
    @HardwareColors(color = "BLUE_DEV", options = {
            "RED_DEV|VIBRANT|#FF0000",
            "*|EXPRESSIVE|home_wallpaper"
    })
    public void createDefaultThemeSettings_usesWildcardFallback_wallpaper() {
        Color cyan = Color.valueOf(Color.CYAN);
        WallpaperColors wallpaperColors = new WallpaperColors(cyan, null, null);
        when(mMockWmi.getWallpaperColors(eq(WallpaperManager.FLAG_SYSTEM), anyInt()))
                .thenReturn(wallpaperColors);

        ThemeSettings defaultSettings = mManager.createDefaultThemeSettings(mUserId);

        assertThat(defaultSettings.colorSource()).isEqualTo(FieldColorSource.VALUE_HOME_WALLPAPER);
        assertThat(defaultSettings.themeStyle()).isEqualTo(ThemeStyle.EXPRESSIVE);
        assertThat(defaultSettings.seedColors().getFirst()).isEqualTo(
                Color.valueOf(ColorScheme.getSeedColor(wallpaperColors)));
    }

    @Test
    @HardwareColors(color = "BLUE_DEV", options = {
            "RED_DEV|VIBRANT|#FF0000",
            "*|EXPRESSIVE|home_wallpaper"
    })
    public void createDefaultThemeSettings_wallpaperNoColors_usesWildcardFallback() {
        when(mMockWmi.getWallpaperColors(anyInt(), anyInt())).thenReturn(null);

        ThemeSettings defaultSettings = mManager.createDefaultThemeSettings(mUserId);

        // Since the primary source (wallpaper) failed, it should fall back to the wildcard,
        // which is also wallpaper. Since that will also fail, it uses the hardcoded fallback
        // palette but keeps the source as HOME_WALLPAPER.
        assertThat(defaultSettings.colorSource()).isEqualTo(FieldColorSource.VALUE_HOME_WALLPAPER);
        assertThat(defaultSettings.themeStyle()).isEqualTo(ThemeStyle.EXPRESSIVE);
        assertThat(defaultSettings.seedColors().getFirst()).isEqualTo(Color.valueOf(0xFF1b6ef3));
    }

    @Test
    @HardwareColors(color = "ANY", options = {
            "RED_DEV|VIBRANT|#FF0000"
            // No wildcard
    })
    public void createDefaultThemeSettings_noWildcard_throwsException() {
        // Redefine environment for this test to bypass setup
        ThemeEnvironment env = new ThemeEnvironment(mContext, (key, def) -> "");
        ThemeSettingsManager manager = new ThemeSettingsManager(new ThemeWallpaperManager(),
                env.getConfig());

        assertThrows(IllegalStateException.class,
                () -> manager.createDefaultThemeSettings(mUserId));
    }

    @Test
    @HardwareColors(color = "ANY", options = {
            "*|TONAL_SPOT|invalid-color"
    })
    public void createDefaultThemeSettings_malformedColor_fallsBackToHardcoded() {
        ThemeSettings defaultSettings = mManager.createDefaultThemeSettings(mUserId);
        assertThat(defaultSettings.colorSource()).isEqualTo(FieldColorSource.VALUE_PRESET);
        assertThat(defaultSettings.themeStyle()).isEqualTo(ThemeStyle.TONAL_SPOT);
        assertThat(defaultSettings.seedColors().getFirst()).isEqualTo(
                mEnvironment.getConfig().hardcodedFallback().seedColors().getFirst());
    }

    @Test
    public void getSettings_missingTimestamp_returnsSettingsWithCurrentTimestamp() {
        String jsonWithoutTimestamp = "{"
                + "\"android.theme.customization.color_source\":\"preset\","
                + "\"android.theme.customization.theme_style\":\"TONAL_SPOT\","
                + "\"android.theme.customization.system_palette\":\"FF1A73E8\""
                + "}";
        Settings.Secure.putStringForUser(mContentResolver,
                Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES, jsonWithoutTimestamp,
                mUserId);

        ThemeSettings settings = mManager.getSettings(mUserId, mContentResolver);

        assertThat(settings).isNotNull();
        assertThat(settings.timeStamp().toEpochMilli()).isAtLeast(Instant.EPOCH.toEpochMilli());
        assertThat(settings.colorSource()).isEqualTo(FieldColorSource.VALUE_PRESET);
        assertThat(settings.themeStyle()).isEqualTo(ThemeStyle.TONAL_SPOT);
    }

    @Test
    public void getSettings_cachesValue() {
        // 1. Write initial value to disk
        ThemeSettings initialSettings = new ThemeSettings.Builder()
                .setThemeStyle(ThemeStyle.VIBRANT)
                .setColorSource(FieldColorSource.VALUE_PRESET)
                .setSeedColors(Color.valueOf(Color.RED))
                .build();
        mManager.setSettings(mUserId, mContentResolver, initialSettings);

        // 2. First read should hit disk and populate cache
        ThemeSettings cachedSettings = mManager.getSettings(mUserId, mContentResolver);
        assertThat(cachedSettings).isEqualTo(initialSettings);

        // 3. Modify disk directly (bypassing manager) to simulate external change or to prove
        // cache hit
        Settings.Secure.putStringForUser(mContentResolver,
                Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES, "{}", mUserId);

        // 4. Second read should still return the cached value, ignoring disk change
        ThemeSettings secondRead = mManager.getSettings(mUserId, mContentResolver);
        assertThat(secondRead).isEqualTo(initialSettings);
    }

    @Test
    public void setSettings_updatesCache() {
        // 1. Set initial settings via manager
        ThemeSettings initialSettings = new ThemeSettings.Builder()
                .setThemeStyle(ThemeStyle.TONAL_SPOT)
                .setColorSource(FieldColorSource.VALUE_PRESET)
                .setSeedColors(Color.valueOf(Color.BLUE))
                .build();
        mManager.setSettings(mUserId, mContentResolver, initialSettings);

        // 2. Verify it's in cache by corrupting disk and reading back
        Settings.Secure.putStringForUser(mContentResolver,
                Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES, "invalid_json", mUserId);
        ThemeSettings cachedSettings = mManager.getSettings(mUserId, mContentResolver);
        assertThat(cachedSettings).isEqualTo(initialSettings);

        // 3. Update settings via manager
        ThemeSettings newSettings = new ThemeSettings.Builder()
                .setThemeStyle(ThemeStyle.EXPRESSIVE)
                .setColorSource(FieldColorSource.VALUE_PRESET)
                .setSeedColors(Color.valueOf(Color.GREEN))
                .build();
        mManager.setSettings(mUserId, mContentResolver, newSettings);

        // 4. Verify cache is updated
        ThemeSettings updatedCachedSettings = mManager.getSettings(mUserId, mContentResolver);
        assertThat(updatedCachedSettings).isEqualTo(newSettings);
    }

    @Test
    public void getSettings_legacyJSON_migratesColorFromWallpaper() {
        // Setup legacy JSON without system_palette
        String legacyJson = "{"
                + "\"android.theme.customization.color_source\":\"home_wallpaper\","
                + "\"android.theme.customization.theme_style\":\"EXPRESSIVE\""
                + "}";
        Settings.Secure.putStringForUser(mContentResolver,
                Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES, legacyJson, mUserId);

        // Mock wallpaper color
        Color wallpaperColor = Color.valueOf(Color.MAGENTA);
        WallpaperColors colors = new WallpaperColors(wallpaperColor, null, null);
        when(mMockWmi.getWallpaperColors(eq(WallpaperManager.FLAG_SYSTEM), eq(mUserId)))
                .thenReturn(colors);

        ThemeSettings settings = mManager.getSettings(mUserId, mContentResolver);

        assertThat(settings).isNotNull();
        assertThat(settings.themeStyle()).isEqualTo(ThemeStyle.EXPRESSIVE);
        assertThat(settings.colorSource()).isEqualTo(FieldColorSource.VALUE_HOME_WALLPAPER);
        // Should have picked up MAGENTA from wallpaper
        assertThat(settings.seedColors().getFirst()).isEqualTo(wallpaperColor);
    }

    @Test
    public void getSettings_externalUpdate_detectsAccentColorChange() throws Exception {
        // Setup initial JSON with same colors
        String initialJson = """
                {
                  "android.theme.customization.color_source": "preset",
                  "android.theme.customization.system_palette": "FF00FF00",
                  "android.theme.customization.accent_color": "FF00FF00"
                }
                """;
        Settings.Secure.putStringForUser(mContentResolver,
                Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES, initialJson, mUserId);

        ThemeSettings initialSettings = mManager.getSettings(mUserId, mContentResolver);
        assertThat(initialSettings.seedColors()).hasSize(1);

        // Invalidate cache to simulate external process change
        mManager.invalidateCache(mUserId);

        // Update accent_color externally
        String updatedJson = """
                {
                  "android.theme.customization.color_source": "preset",
                  "android.theme.customization.system_palette": "FF00FF00",
                  "android.theme.customization.accent_color": "FFFF0000"
                }
                """;
        Settings.Secure.putStringForUser(mContentResolver,
                Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES, updatedJson, mUserId);

        ThemeSettings updatedSettings = mManager.getSettings(mUserId, mContentResolver);
        assertThat(updatedSettings.seedColors()).hasSize(2);
        assertThat(updatedSettings.seedColors().get(0)).isEqualTo(Color.valueOf(Color.GREEN));
        assertThat(updatedSettings.seedColors().get(1)).isEqualTo(Color.valueOf(Color.RED));
    }

    @Test
    public void getSettings_repairsLegacyUpdate_systemPaletteChanged() throws Exception {
        // Initial state: list of 2 colors
        ThemeSettings initial = new ThemeSettings.Builder()
                .setThemeStyle(ThemeStyle.TONAL_SPOT)
                .setColorSource(FieldColorSource.VALUE_PRESET)
                .setSeedColors(Color.valueOf(Color.RED), Color.valueOf(Color.BLUE))
                .build();
        mManager.setSettings(mUserId, mContentResolver, initial);
        mManager.invalidateCache(mUserId);

        // Update system_palette externally (e.g. by legacy app)
        String json = Settings.Secure.getStringForUser(mContentResolver,
                Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES, mUserId);
        JSONObject obj = new JSONObject(json);
        obj.put(ThemeSettingsManager.OVERLAY_CATEGORY_SYSTEM_PALETTE, "FF00FF00"); // GREEN
        Settings.Secure.putStringForUser(mContentResolver,
                Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES, obj.toString(), mUserId);

        // Read back: List should have GREEN as first element
        ThemeSettings repaired = mManager.getSettings(mUserId, mContentResolver);
        assertThat(repaired.seedColors().get(0)).isEqualTo(Color.valueOf(Color.GREEN));
        assertThat(repaired.seedColors().get(1)).isEqualTo(Color.valueOf(Color.BLUE));
    }

    @Test
    public void getSettings_repairsLegacyUpdate_accentColorChanged() throws Exception {
        // Initial state: list of 2 colors
        ThemeSettings initial = new ThemeSettings.Builder()
                .setThemeStyle(ThemeStyle.TONAL_SPOT)
                .setColorSource(FieldColorSource.VALUE_PRESET)
                .setSeedColors(Color.valueOf(Color.RED), Color.valueOf(Color.BLUE))
                .build();
        mManager.setSettings(mUserId, mContentResolver, initial);
        mManager.invalidateCache(mUserId);

        // Update accent_color externally (e.g. by legacy app)
        String json = Settings.Secure.getStringForUser(mContentResolver,
                Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES, mUserId);
        JSONObject obj = new JSONObject(json);
        obj.put(ThemeSettingsManager.OVERLAY_CATEGORY_ACCENT_COLOR, "FFFFFF00"); // YELLOW
        Settings.Secure.putStringForUser(mContentResolver,
                Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES, obj.toString(), mUserId);

        // Read back: List should have YELLOW as second element
        ThemeSettings repaired = mManager.getSettings(mUserId, mContentResolver);
        assertThat(repaired.seedColors().get(0)).isEqualTo(Color.valueOf(Color.RED));
        assertThat(repaired.seedColors().get(1)).isEqualTo(Color.valueOf(Color.YELLOW));
    }
}
