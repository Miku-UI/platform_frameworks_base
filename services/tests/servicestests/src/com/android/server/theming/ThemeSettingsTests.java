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

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.annotation.SuppressLint;
import android.content.theming.FieldColorSource;
import android.content.theming.ThemeSettings;
import android.content.theming.ThemeStyle;
import android.graphics.Color;
import android.os.Parcel;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Instant;

@RunWith(AndroidJUnit4.class)
public class ThemeSettingsTests {
    @Test
    public void testBuilder_wallpaper() {
        Instant testStart = Instant.now();
        ThemeSettings settings = new ThemeSettings.Builder()
                .setThemeStyle(ThemeStyle.TONAL_SPOT)
                .setColorSource(FieldColorSource.VALUE_HOME_WALLPAPER)
                .setSeedColors(Color.valueOf(Color.BLUE))
                .build();

        assertThat(settings.themeStyle()).isEqualTo(ThemeStyle.TONAL_SPOT);
        assertThat(settings.colorSource()).isEqualTo(FieldColorSource.VALUE_HOME_WALLPAPER);
        assertThat(settings.seedColors().getFirst()).isNotNull();
        assertThat(settings.timeStamp()).isAtLeast(testStart);
    }

    @Test
    public void testBuilder_preset() {
        Color testColor = Color.valueOf(0xFF1A73E8);
        Instant testStart = Instant.now();

        ThemeSettings settings = new ThemeSettings.Builder()
                .setThemeStyle(ThemeStyle.TONAL_SPOT)
                .setColorSource(FieldColorSource.VALUE_PRESET)
                .setSeedColors(testColor)
                .build();

        assertThat(settings.themeStyle()).isEqualTo(ThemeStyle.TONAL_SPOT);
        assertThat(settings.colorSource()).isEqualTo(FieldColorSource.VALUE_PRESET);
        assertThat(settings.seedColors().getFirst()).isEqualTo(testColor);
        assertThat(settings.timeStamp()).isAtLeast(testStart);
    }

    @Test
    public void testBuilder_multiSeed() {
        Color color1 = Color.valueOf(Color.RED);
        Color color2 = Color.valueOf(Color.BLUE);
        ThemeSettings settings = new ThemeSettings.Builder()
                .setThemeStyle(ThemeStyle.VIBRANT)
                .setColorSource(FieldColorSource.VALUE_PRESET)
                .setSeedColors(color1, color2)
                .build();

        assertThat(settings.seedColors()).containsExactly(color1, color2).inOrder();
        assertThat(settings.seedColors().getFirst()).isEqualTo(color1);
    }

    @Test
    public void testParcel_roundTrip() {
        // Test wallpaper
        ThemeSettings originalWallpaper = new ThemeSettings.Builder()
                .setThemeStyle(ThemeStyle.VIBRANT)
                .setColorSource(FieldColorSource.VALUE_HOME_WALLPAPER)
                .setSeedColors(Color.valueOf(Color.RED))
                .build();
        Parcel wallpaperParcel = Parcel.obtain();
        originalWallpaper.writeToParcel(wallpaperParcel, 0);
        wallpaperParcel.setDataPosition(0);
        ThemeSettings unparceledWallpaper = ThemeSettings.CREATOR.createFromParcel(
                wallpaperParcel);
        wallpaperParcel.recycle();
        assertThat(unparceledWallpaper.themeStyle()).isEqualTo(originalWallpaper.themeStyle());
        assertThat(unparceledWallpaper.colorSource()).isEqualTo(originalWallpaper.colorSource());
        assertThat(unparceledWallpaper.seedColors())
                .isEqualTo(originalWallpaper.seedColors());
        assertThat(unparceledWallpaper.timeStamp().toEpochMilli()).isAtLeast(
                originalWallpaper.timeStamp().toEpochMilli());

        // Test multi-seed
        ThemeSettings originalMulti = new ThemeSettings.Builder()
                .setThemeStyle(ThemeStyle.TONAL_SPOT)
                .setColorSource(FieldColorSource.VALUE_PRESET)
                .setSeedColors(Color.valueOf(Color.GREEN), Color.valueOf(Color.YELLOW))
                .build();
        Parcel multiParcel = Parcel.obtain();
        originalMulti.writeToParcel(multiParcel, 0);
        multiParcel.setDataPosition(0);
        ThemeSettings unparceledMulti = ThemeSettings.CREATOR.createFromParcel(multiParcel);
        multiParcel.recycle();
        assertThat(unparceledMulti.seedColors()).isEqualTo(originalMulti.seedColors());
    }

    @SuppressLint("WrongConstant")
    @Test
    public void testBuilder_preset_withInvalidStyle_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> new ThemeSettings.Builder()
                        .setThemeStyle(-1)
                        .setColorSource(FieldColorSource.VALUE_PRESET)
                        .setSeedColors(Color.valueOf(Color.BLUE))
                        .build());
    }

    @Test
    public void testBuilder_preset_withInvalidColor_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> new ThemeSettings.Builder()
                        .setThemeStyle(ThemeStyle.TONAL_SPOT)
                        .setColorSource(FieldColorSource.VALUE_PRESET)
                        .setSeedColors(Color.valueOf(Color.TRANSPARENT))
                        .build());
    }

    @SuppressLint("WrongConstant")
    @Test
    public void testBuilder_wallpaper_withInvalidStyle_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> new ThemeSettings.Builder()
                        .setThemeStyle(-1)
                        .setColorSource(FieldColorSource.VALUE_HOME_WALLPAPER)
                        .setSeedColors(Color.valueOf(Color.RED))
                        .build());
    }
}
