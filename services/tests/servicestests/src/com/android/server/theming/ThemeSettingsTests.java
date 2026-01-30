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

import static android.content.theming.FieldColorSource.VALUE_PRESET;
import static android.content.theming.ThemeSettings.OVERLAY_CATEGORY_ACCENT_COLOR;
import static android.content.theming.ThemeSettings.OVERLAY_CATEGORY_SYSTEM_PALETTE;
import static android.content.theming.ThemeSettings.OVERLAY_CATEGORY_THEME_STYLE;
import static android.content.theming.ThemeSettings.OVERLAY_COLOR_BOTH;
import static android.content.theming.ThemeSettings.OVERLAY_COLOR_INDEX;
import static android.content.theming.ThemeSettings.OVERLAY_COLOR_SOURCE;
import static android.content.theming.ThemeSettings.TIMESTAMP;

import static com.google.common.truth.Truth.assertThat;

import android.content.theming.FieldColorSource;
import android.content.theming.ThemeSettings;
import android.content.theming.ThemeSettingsPreset;
import android.content.theming.ThemeSettingsWallpaper;
import android.content.theming.ThemeStyle;
import android.graphics.Color;
import android.os.Parcel;

import androidx.test.runner.AndroidJUnit4;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Instant;

@RunWith(AndroidJUnit4.class)
public class ThemeSettingsTests {
    private static final String WALLPAPER_JSON = """
                    {
                      "_applied_timestamp": 1747862004148,
                      "android.theme.customization.color_index": "1",
                      "android.theme.customization.color_source": "home_wallpaper",
                      "android.theme.customization.theme_style": "VIBRANT",
                      "android.theme.customization.color_both": "1"
                    }
            """;

    private static final String PRESET_JSON = """
                    {
                      "_applied_timestamp": 1749626671504,
                      "android.theme.customization.color_index": "7",
                      "android.theme.customization.color_source": "preset",
                      "android.theme.customization.theme_style": "TONAL_SPOT",
                      "android.theme.customization.system_palette": "1A73E8",
                      "android.theme.customization.accent_color": "1A73E8"
                    }
            """;

    @Test
    public void testBuilder_allFieldsSet_wallpaper() {
        Instant testStart = Instant.now();

        ThemeSettingsWallpaper settings = ThemeSettings.builder(7,
                ThemeStyle.TONAL_SPOT).buildFromWallpaper(false);

        assertThat(settings.colorIndex()).isEqualTo(7);
        assertThat(settings.themeStyle()).isEqualTo(ThemeStyle.TONAL_SPOT);
        assertThat(settings.colorSource()).isEqualTo(FieldColorSource.VALUE_HOME_WALLPAPER);
        assertThat(settings.colorBoth()).isFalse();
        // Timestamp is set internally
        assertThat(settings.timeStamp()).isAtLeast(testStart);
    }

    @Test
    public void testBuilder_allFieldsSet_preset() {
        Color testColor = Color.valueOf(0xFF1A73E8);
        Instant testStart = Instant.now();

        ThemeSettingsPreset settings = ThemeSettings.builder(7,
                ThemeStyle.TONAL_SPOT).buildFromPreset(testColor, testColor);

        assertThat(settings.colorIndex()).isEqualTo(7);
        assertThat(settings.themeStyle()).isEqualTo(ThemeStyle.TONAL_SPOT);
        assertThat(settings.colorSource()).isEqualTo(VALUE_PRESET);
        assertThat(settings.systemPalette()).isEqualTo(testColor);
        assertThat(settings.accentColor()).isEqualTo(testColor);
        // Timestamp is set internally
        assertThat(settings.timeStamp()).isAtLeast(testStart);
    }

    @Test
    public void testParcel_roundTrip_wallpaper() throws JSONException {
        ThemeSettings originalSettings = ThemeSettings.fromJson(WALLPAPER_JSON);

        Parcel parcel = Parcel.obtain();
        originalSettings.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        ThemeSettings settingsFromParcel = ThemeSettings.CREATOR.createFromParcel(parcel);
        parcel.recycle();

        assertThat(settingsFromParcel).isInstanceOf(ThemeSettingsWallpaper.class);
        assertThat(settingsFromParcel).isEqualTo(originalSettings);
        assertThat(settingsFromParcel.hashCode()).isEqualTo(originalSettings.hashCode());
    }

    @Test
    public void testParcel_roundTrip_preset() throws JSONException {
        ThemeSettings originalSettings = ThemeSettings.fromJson(PRESET_JSON);

        Parcel parcel = Parcel.obtain();
        originalSettings.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        ThemeSettings settingsFromParcel = ThemeSettings.CREATOR.createFromParcel(parcel);
        parcel.recycle();

        assertThat(settingsFromParcel).isInstanceOf(ThemeSettingsPreset.class);
        assertThat(settingsFromParcel).isEqualTo(originalSettings);
        assertThat(settingsFromParcel.hashCode()).isEqualTo(originalSettings.hashCode());
    }


    @Test
    public void testFromJSON_wallpaper() throws JSONException {
        ThemeSettings settings = ThemeSettings.fromJson(WALLPAPER_JSON);

        assertThat(settings).isInstanceOf(ThemeSettingsWallpaper.class);
        ThemeSettingsWallpaper wallpaperSettings = (ThemeSettingsWallpaper) settings;

        assertThat(wallpaperSettings.timeStamp()).isEqualTo(Instant.ofEpochMilli(1747862004148L));
        assertThat(wallpaperSettings.colorIndex()).isEqualTo(1);
        assertThat(wallpaperSettings.colorSource()).isEqualTo(
                FieldColorSource.VALUE_HOME_WALLPAPER);
        assertThat(wallpaperSettings.themeStyle()).isEqualTo(ThemeStyle.VIBRANT);
        assertThat(wallpaperSettings.colorBoth()).isTrue();
    }

    @Test
    public void testFromJson_preset() throws JSONException {
        ThemeSettings settings = ThemeSettings.fromJson(PRESET_JSON);

        assertThat(settings).isInstanceOf(ThemeSettingsPreset.class);
        ThemeSettingsPreset presetSettings = (ThemeSettingsPreset) settings;

        assertThat(presetSettings.timeStamp()).isEqualTo(Instant.ofEpochMilli(1749626671504L));
        assertThat(presetSettings.colorIndex()).isEqualTo(7);
        assertThat(presetSettings.colorSource()).isEqualTo(VALUE_PRESET);
        assertThat(presetSettings.themeStyle()).isEqualTo(ThemeStyle.TONAL_SPOT);
        assertThat(presetSettings.systemPalette().toArgb()).isEqualTo(
                Color.valueOf(0xFF1A73E8).toArgb());
        assertThat(presetSettings.accentColor().toArgb()).isEqualTo(
                Color.valueOf(0xFF1A73E8).toArgb());
    }

    @Test
    public void testToString_wallpaper() throws JSONException {
        ThemeSettingsWallpaper settings = (ThemeSettingsWallpaper) ThemeSettings.fromJson(
                WALLPAPER_JSON);

        assertThat(settings.getClass()).isEqualTo(ThemeSettingsWallpaper.class);

        String jsonString = settings.toString();
        JSONObject jsonOutput = new JSONObject(jsonString);

        assertThat(jsonOutput.has(TIMESTAMP)).isTrue();
        assertThat(jsonOutput.getLong(TIMESTAMP)).isEqualTo(1747862004148L);
        assertThat(jsonOutput.getString(OVERLAY_COLOR_INDEX)).isEqualTo("1");
        assertThat(jsonOutput.getString(OVERLAY_COLOR_SOURCE)).isEqualTo("home_wallpaper");
        assertThat(jsonOutput.getString(OVERLAY_CATEGORY_THEME_STYLE)).isEqualTo("VIBRANT");
        assertThat(jsonOutput.getString(OVERLAY_COLOR_BOTH)).isEqualTo("1");
        assertThat(jsonOutput.length()).isEqualTo(5);
    }

    @Test
    public void testToString_preset() throws JSONException {
        ThemeSettingsPreset settings = (ThemeSettingsPreset) ThemeSettings.fromJson(PRESET_JSON);

        assertThat(settings.getClass()).isEqualTo(ThemeSettingsPreset.class);

        String jsonString = settings.toString();
        JSONObject jsonOutput = new JSONObject(jsonString);

        assertThat(jsonOutput.has(TIMESTAMP)).isTrue();
        assertThat(jsonOutput.getLong(TIMESTAMP)).isEqualTo(1749626671504L);
        assertThat(jsonOutput.getString(OVERLAY_COLOR_INDEX)).isEqualTo("7");
        assertThat(jsonOutput.getString(OVERLAY_COLOR_SOURCE)).isEqualTo("preset");
        assertThat(jsonOutput.getString(OVERLAY_CATEGORY_THEME_STYLE)).isEqualTo("TONAL_SPOT");
        assertThat(jsonOutput.getString(OVERLAY_CATEGORY_SYSTEM_PALETTE)).isEqualTo("FF1A73E8");
        assertThat(jsonOutput.getString(OVERLAY_CATEGORY_ACCENT_COLOR)).isEqualTo("FF1A73E8");
        assertThat(jsonOutput.length()).isEqualTo(6);
    }
}