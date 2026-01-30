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

import android.content.ContentResolver;
import android.content.theming.FieldColorSource;
import android.content.theming.ThemeSettings;
import android.content.theming.ThemeSettingsPreset;
import android.content.theming.ThemeSettingsWallpaper;
import android.content.theming.ThemeStyle;
import android.graphics.Color;
import android.provider.Settings;
import android.testing.TestableContext;

import androidx.test.runner.AndroidJUnit4;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ThemeSettingsManagerTests {
    private final int mUserId = 0;

    @Rule
    public final TestableContext mContext = new TestableContext(
            getInstrumentation().getTargetContext(), null);

    private ContentResolver mContentResolver;
    private ThemeSettingsManager mManager;

    @Before
    public void setup() {
        mContentResolver = mContext.getContentResolver();
        mManager = new ThemeSettingsManager();

        Settings.Secure.putStringForUser(mContentResolver,
                Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES, null, mUserId);
    }

    @Test
    public void loadSettings_noSettings_returnsNull() {

        ThemeSettings settings = mManager.readSettings(mUserId, mContentResolver);
        assertThat(settings).isNull();
    }

    @Test
    public void loadSettings_emptyJSON_returnsNull() {
        Settings.Secure.putStringForUser(mContentResolver,
                Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES, "{}", mUserId);
        ThemeSettings settings = mManager.readSettings(mUserId, mContentResolver);


        assertThat(settings).isNull();
    }

    @Test
    public void loadSettings_invalidJSON_returnsNull() {
        Settings.Secure.putStringForUser(mContentResolver,
                Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES, "{invalid_json", mUserId);
        ThemeSettings settings = mManager.readSettings(mUserId, mContentResolver);
        assertThat(settings).isNull();
    }

    @Test
    public void writeSettings_writesPresetToProvider() throws Exception {
        long currentTime = System.currentTimeMillis();
        ThemeSettingsPreset presetSettings = ThemeSettings.builder(3,
                ThemeStyle.MONOCHROMATIC).buildFromPreset(Color.valueOf(0xFF112233),
                Color.valueOf(0xFF332211));

        boolean success = mManager.writeSettings(mUserId, mContentResolver, presetSettings);
        assertThat(success).isTrue();

        String settingsString = Settings.Secure.getStringForUser(mContentResolver,
                Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES, mUserId);

        JSONObject settingsJson = new JSONObject(settingsString);

        assertThat(settingsJson.has(ThemeSettings.TIMESTAMP))
                .isTrue();
        assertThat(settingsJson.getLong(ThemeSettings.TIMESTAMP))
                .isAtLeast(currentTime);
        assertThat(settingsJson.getString(ThemeSettings.OVERLAY_COLOR_INDEX))
                .isEqualTo("3");
        assertThat(settingsJson.getString(ThemeSettings.OVERLAY_CATEGORY_SYSTEM_PALETTE))
                .isEqualTo("FF112233");
        assertThat(settingsJson.getString(ThemeSettings.OVERLAY_CATEGORY_ACCENT_COLOR))
                .isEqualTo("FF332211");
        assertThat(settingsJson.getString(ThemeSettings.OVERLAY_COLOR_SOURCE))
                .isEqualTo(FieldColorSource.VALUE_PRESET);
        assertThat(settingsJson.getString(ThemeSettings.OVERLAY_CATEGORY_THEME_STYLE))
                .isEqualTo(ThemeStyle.toString(ThemeStyle.MONOCHROMATIC));

        assertThat(settingsJson.has(ThemeSettings.OVERLAY_COLOR_BOTH)).isFalse();
        assertThat(settingsJson.length()).isEqualTo(6);
    }

    @Test
    public void writeSettings_writesWallpaperToProvider() throws Exception {
        ThemeSettingsWallpaper wallpaperSettings = ThemeSettings.builder(1,
                ThemeStyle.VIBRANT).buildFromWallpaper(true);

        boolean success = mManager.writeSettings(mUserId, mContentResolver, wallpaperSettings);
        assertThat(success).isTrue();

        String settingsString = Settings.Secure.getStringForUser(mContentResolver,
                Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES, mUserId);
        JSONObject settingsJson = new JSONObject(settingsString);

        assertThat(settingsJson.has(ThemeSettings.TIMESTAMP)).isTrue();
        assertThat(settingsJson.getLong(ThemeSettings.TIMESTAMP)).isEqualTo(
                wallpaperSettings.timeStamp().toEpochMilli());
        assertThat(settingsJson.getString(ThemeSettings.OVERLAY_COLOR_INDEX)).isEqualTo("1");
        assertThat(settingsJson.getString(ThemeSettings.OVERLAY_COLOR_SOURCE)).isEqualTo(
                FieldColorSource.VALUE_HOME_WALLPAPER);
        assertThat(settingsJson.getString(ThemeSettings.OVERLAY_CATEGORY_THEME_STYLE)).isEqualTo(
                ThemeStyle.toString(ThemeStyle.VIBRANT));
        assertThat(settingsJson.getString(ThemeSettings.OVERLAY_COLOR_BOTH)).isEqualTo("1");

        assertThat(settingsJson.has(ThemeSettings.OVERLAY_CATEGORY_SYSTEM_PALETTE)).isFalse();
        assertThat(settingsJson.has(ThemeSettings.OVERLAY_CATEGORY_ACCENT_COLOR)).isFalse();
        assertThat(settingsJson.length()).isEqualTo(5);
    }

    @Test
    public void writeAndReadSettings_wallpaper_persistsAndReadsCorrectly() {
        ThemeSettingsWallpaper originalSettings = ThemeSettings.builder(2,
                ThemeStyle.EXPRESSIVE).buildFromWallpaper(false);

        mManager.writeSettings(mUserId, mContentResolver, originalSettings);
        ThemeSettingsWallpaper loadedSettings = (ThemeSettingsWallpaper) mManager.readSettings(
                mUserId, mContentResolver);

        assertThat(loadedSettings.getClass()).isEqualTo(ThemeSettingsWallpaper.class);

        assertThat(loadedSettings).isNotNull();
        assertThat(loadedSettings).isInstanceOf(ThemeSettingsWallpaper.class);

        assertThat(loadedSettings.timeStamp().toEpochMilli()).isEqualTo(
                originalSettings.timeStamp().toEpochMilli());
        assertThat(loadedSettings.colorIndex()).isEqualTo(originalSettings.colorIndex());
        assertThat(loadedSettings.themeStyle()).isEqualTo(originalSettings.themeStyle());
        assertThat(loadedSettings.colorSource()).isEqualTo(originalSettings.colorSource());
        assertThat(loadedSettings.colorBoth()).isEqualTo(originalSettings.colorBoth());
    }
}
