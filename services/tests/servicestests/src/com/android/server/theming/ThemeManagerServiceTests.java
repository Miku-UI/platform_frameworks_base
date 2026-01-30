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

import android.content.theming.FieldColorSource;
import android.content.theming.ThemeSettings;
import android.content.theming.ThemeSettingsPreset;
import android.content.theming.ThemeSettingsWallpaper;
import android.content.theming.ThemeStyle;
import android.graphics.Color;
import android.testing.TestableContext;
import android.testing.TestableResources;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.runner.AndroidJUnit4;

import com.android.internal.R;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ThemeManagerServiceTests {

    @Rule
    public final HardwareColorRule mHardwareColorRule = new HardwareColorRule();

    private final SystemPropertiesReader mSystemPropertiesReader = new SystemPropertiesReader() {
        @NonNull
        @Override
        public String get(@NonNull String key, @Nullable String def) {
            return mHardwareColorRule.color;
        }
    };

    @Rule
    public final TestableContext mUserContext = new TestableContext(
            getInstrumentation().getContext(), null);

    @Before
    public void setup() {
        TestableResources userResources = mUserContext.getOrCreateTestableResources();
        userResources.addOverride(R.array.theming_defaults, mHardwareColorRule.options);
    }

    @Test
    @HardwareColors(color = "RED_DEV", options = {
            "RED_DEV|VIBRANT|#FF0000",
            "*|TONAL_SPOT|#00FF00"
    })
    public void createDefaultThemeSettings_matchesHardwareColor() {
        ThemeManagerService themeManagerService = testableServiceStart();
        ThemeSettings defaultSettings = themeManagerService.createDefaultThemeSettings();

        assertThat(defaultSettings).isInstanceOf(ThemeSettingsPreset.class);
        ThemeSettingsPreset preset = (ThemeSettingsPreset) defaultSettings;
        assertThat(preset.themeStyle()).isEqualTo(ThemeStyle.VIBRANT);
        assertThat(preset.systemPalette().toArgb()).isEqualTo(Color.parseColor("#FFFF0000"));
    }

    @Test
    @HardwareColors(color = "BLUE_DEV", options = {
            "RED_DEV|VIBRANT|#FF0000",
            "*|TONAL_SPOT|#00FF00"
    })
    public void createDefaultThemeSettings_usesWildcardFallback_preset() {
        ThemeManagerService themeManagerService = testableServiceStart();
        ThemeSettings defaultSettings = themeManagerService.createDefaultThemeSettings();

        assertThat(defaultSettings).isInstanceOf(ThemeSettingsPreset.class);
        ThemeSettingsPreset preset = (ThemeSettingsPreset) defaultSettings;
        assertThat(preset.themeStyle()).isEqualTo(ThemeStyle.TONAL_SPOT);
        assertThat(preset.systemPalette().toArgb()).isEqualTo(Color.parseColor("#FF00FF00"));
    }

    @Test
    @HardwareColors(color = "BLUE_DEV", options = {
            "RED_DEV|VIBRANT|#FF0000",
            "*|EXPRESSIVE|home_wallpaper"
    })
    public void createDefaultThemeSettings_usesWildcardFallback_wallpaper() {
        ThemeManagerService themeManagerService = testableServiceStart();
        ThemeSettings defaultSettings = themeManagerService.createDefaultThemeSettings();

        assertThat(defaultSettings).isInstanceOf(ThemeSettingsWallpaper.class);
        ThemeSettingsWallpaper wallpaper = (ThemeSettingsWallpaper) defaultSettings;
        assertThat(wallpaper.themeStyle()).isEqualTo(ThemeStyle.EXPRESSIVE);
        assertThat(wallpaper.colorSource()).isEqualTo(FieldColorSource.VALUE_HOME_WALLPAPER);
    }

    @Test
    @HardwareColors(color = "ANY", options = {
            "RED_DEV|VIBRANT|#FF0000"
            // No wildcard
    })
    public void createDefaultThemeSettings_noWildcard_throwsException() {
        assertThrows(IllegalStateException.class, this::testableServiceStart);
    }

    @Test
    @HardwareColors(color = "ANY", options = {
            "*|TONAL_SPOT|invalid-color"
    })
    public void createDefaultThemeSettings_malformedColor_throwsException() {
        assertThrows(IllegalArgumentException.class, this::testableServiceStart);
    }

    private ThemeManagerService testableServiceStart() {
        return new ThemeManagerService(mUserContext, mSystemPropertiesReader);
    }
}
