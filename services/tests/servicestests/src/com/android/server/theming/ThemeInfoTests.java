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

import android.content.theming.ThemeInfo;
import android.content.theming.ThemeStyle;
import android.graphics.Color;
import android.os.Parcel;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;


@RunWith(AndroidJUnit4.class)
public class ThemeInfoTests {

    private static final float CONTRAST_MEDIUM = 0.5f;
    private static final float CONTRAST_HIGH = 1.0f;

    private static final List<Color> SEED_COLORS_VALID = List.of(Color.valueOf(Color.BLUE),
            Color.valueOf(Color.RED));
    private static final int STYLE_VALID = ThemeStyle.TONAL_SPOT;
    private static final String SPEC_VERSION = "1.0";
    private static final String PLATFORM = "android";

    @Test
    public void testBuildWithAllParameters() {
        ThemeInfo themeInfo = new ThemeInfo.Builder()
                .setSeedColors(SEED_COLORS_VALID)
                .setStyle(STYLE_VALID)
                .setContrast(CONTRAST_MEDIUM)
                .build();
        assertThat(themeInfo.seedColors).isEqualTo(SEED_COLORS_VALID);
        assertThat(themeInfo.style).isEqualTo(STYLE_VALID);
        assertThat(themeInfo.contrast).isEqualTo(CONTRAST_MEDIUM);
        assertThat(themeInfo.specVersion).isNull();
        assertThat(themeInfo.platform).isNull();
    }

    @Test
    public void testBuildWithNullParameters() {
        ThemeInfo themeInfo = new ThemeInfo.Builder().build();
        assertThat(themeInfo.seedColors).isNull();
        assertThat(themeInfo.style).isNull();
        assertThat(themeInfo.contrast).isNull();
        assertThat(themeInfo.specVersion).isNull();
        assertThat(themeInfo.platform).isNull();
    }

    @Test
    public void testParcelability_allValues() {
        ThemeInfo themeInfo = new ThemeInfo(
                SEED_COLORS_VALID,
                STYLE_VALID,
                CONTRAST_HIGH,
                SPEC_VERSION,
                PLATFORM);

        Parcel parcel = Parcel.obtain();
        themeInfo.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        ThemeInfo unparceledThemeInfo = ThemeInfo.CREATOR.createFromParcel(parcel);
        parcel.recycle();

        assertThat(unparceledThemeInfo.seedColors).isEqualTo(themeInfo.seedColors);
        assertThat(unparceledThemeInfo.style).isEqualTo(themeInfo.style);
        assertThat(unparceledThemeInfo.contrast).isEqualTo(themeInfo.contrast);
        assertThat(unparceledThemeInfo.specVersion).isEqualTo(themeInfo.specVersion);
        assertThat(unparceledThemeInfo.platform).isEqualTo(themeInfo.platform);
    }

    @Test
    public void testParcelability_nullValues() {
        ThemeInfo themeInfo = new ThemeInfo.Builder().build();

        Parcel parcel = Parcel.obtain();
        themeInfo.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        ThemeInfo unparceledThemeInfo = ThemeInfo.CREATOR.createFromParcel(parcel);
        parcel.recycle();

        assertThat(unparceledThemeInfo.seedColors).isNull();
        assertThat(unparceledThemeInfo.style).isNull();
        assertThat(unparceledThemeInfo.contrast).isNull();
        assertThat(unparceledThemeInfo.specVersion).isNull();
        assertThat(unparceledThemeInfo.platform).isNull();
    }

    @Test
    public void testParcelability_mixedValues() {
        ThemeInfo themeInfo = new ThemeInfo.Builder()
                .setSeedColors(SEED_COLORS_VALID)
                .setContrast(CONTRAST_HIGH)
                .build();


        Parcel parcel = Parcel.obtain();
        themeInfo.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        ThemeInfo unparceledThemeInfo = ThemeInfo.CREATOR.createFromParcel(parcel);
        parcel.recycle();

        assertThat(unparceledThemeInfo.seedColors).isEqualTo(themeInfo.seedColors);
        assertThat(unparceledThemeInfo.style).isNull();
        assertThat(unparceledThemeInfo.contrast).isEqualTo(themeInfo.contrast);
        assertThat(unparceledThemeInfo.specVersion).isNull();
        assertThat(unparceledThemeInfo.platform).isNull();
    }

    @Test
    public void testDescribeContents() {
        ThemeInfo themeInfo = new ThemeInfo.Builder().build();
        assertThat(themeInfo.describeContents()).isEqualTo(0);
    }
}
