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


@RunWith(AndroidJUnit4.class)
public class ThemeInfoTests {

    private static final float CONTRAST_MEDIUM = 0.5f;
    private static final float CONTRAST_HIGH = 1.0f;

    private static final Color SEED_COLOR_VALID = Color.valueOf(Color.BLUE);
    private static final int STYLE_VALID = ThemeStyle.TONAL_SPOT;

    @Test
    public void testBuildWithAllParameters() {
        ThemeInfo themeInfo = ThemeInfo.build(SEED_COLOR_VALID, STYLE_VALID, CONTRAST_MEDIUM);
        assertThat(themeInfo.seedColor).isEqualTo(SEED_COLOR_VALID.toArgb());
        assertThat(themeInfo.style).isEqualTo(STYLE_VALID);
        assertThat(themeInfo.contrast).isEqualTo(CONTRAST_MEDIUM);
    }

    @Test
    public void testBuildWithNullParameters() {
        ThemeInfo themeInfo = ThemeInfo.build(null, null, null);
        assertThat(themeInfo.seedColor).isNull();
        assertThat(themeInfo.style).isNull();
        assertThat(themeInfo.contrast).isNull();
    }

    @Test
    public void testParcelability_allValues() {
        ThemeInfo themeInfo = ThemeInfo.build(SEED_COLOR_VALID, STYLE_VALID, CONTRAST_HIGH);

        Parcel parcel = Parcel.obtain();
        themeInfo.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        ThemeInfo unparceledThemeInfo = ThemeInfo.CREATOR.createFromParcel(parcel);
        parcel.recycle();

        assertThat(unparceledThemeInfo.seedColor).isEqualTo(themeInfo.seedColor);
        assertThat(unparceledThemeInfo.style).isEqualTo(themeInfo.style);
        assertThat(unparceledThemeInfo.contrast).isEqualTo(themeInfo.contrast);
    }

    @Test
    public void testParcelability_nullValues() {
        ThemeInfo themeInfo = ThemeInfo.build(null, null, null);

        Parcel parcel = Parcel.obtain();
        themeInfo.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        ThemeInfo unparceledThemeInfo = ThemeInfo.CREATOR.createFromParcel(parcel);
        parcel.recycle();

        assertThat(unparceledThemeInfo.seedColor).isNull();
        assertThat(unparceledThemeInfo.style).isNull();
        assertThat(unparceledThemeInfo.contrast).isNull();
    }

    @Test
    public void testParcelability_mixedValues() {
        ThemeInfo themeInfo = ThemeInfo.build(SEED_COLOR_VALID, null, CONTRAST_HIGH);

        Parcel parcel = Parcel.obtain();
        themeInfo.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        ThemeInfo unparceledThemeInfo = ThemeInfo.CREATOR.createFromParcel(parcel);
        parcel.recycle();

        assertThat(unparceledThemeInfo.seedColor).isEqualTo(themeInfo.seedColor);
        assertThat(unparceledThemeInfo.style).isNull();
        assertThat(unparceledThemeInfo.contrast).isEqualTo(themeInfo.contrast);
    }

    @Test
    public void testDescribeContents() {
        ThemeInfo themeInfo = ThemeInfo.build(SEED_COLOR_VALID, STYLE_VALID, CONTRAST_MEDIUM);
        assertThat(themeInfo.describeContents()).isEqualTo(0);
    }
}
