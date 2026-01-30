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
import android.content.theming.FieldThemeStyle;
import android.content.theming.ThemeStyle;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class FieldThemeStyleTests {
    private FieldThemeStyle mFieldThemeStyle;

    @Before
    public void setup() {
        mFieldThemeStyle = new FieldThemeStyle();
    }

    @Test
    public void parse_validThemeStyle_returnsCorrectStyle() {
        Integer parsedValue = mFieldThemeStyle.parse("EXPRESSIVE");
        assertThat(parsedValue).isEqualTo(ThemeStyle.EXPRESSIVE);
    }

    @Test
    public void parse_validThemeStyle_wrongCase_returnsNull() {
        Integer parsedValue = mFieldThemeStyle.parse("expressive");
        assertThat(parsedValue).isNull();
    }

    @Test
    public void parse_invalidThemeStyleName_returnsNull() {
        Integer parsedValue = mFieldThemeStyle.parse("INVALID");
        assertThat(parsedValue).isNull();
    }

    @Test
    public void parse_nullString_returnsNull() {
        Integer parsedValue = mFieldThemeStyle.parse(null);
        assertThat(parsedValue).isNull();
    }

    @Test
    public void serialize_validThemeStyle_returnsCorrectString() {
        String serializedValue = mFieldThemeStyle.serialize(ThemeStyle.SPRITZ);
        assertThat(serializedValue).isEqualTo("SPRITZ");
    }

    @Test
    public void serialize_nullThemeStyle_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () -> {
            mFieldThemeStyle.serialize(null);
        });
    }

    @SuppressLint("WrongConstant")
    @Test
    public void serialize_invalidThemeStyleInteger_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> {
            mFieldThemeStyle.serialize(-1);
        });
    }

    @Test
    public void validate_validThemeStyle_tonalSpot_returnsTrue() {
        assertThat(mFieldThemeStyle.validate(ThemeStyle.TONAL_SPOT)).isTrue();
    }

    @Test
    public void validate_validThemeStyle_vibrant_returnsTrue() {
        assertThat(mFieldThemeStyle.validate(ThemeStyle.VIBRANT)).isTrue();
    }

    @Test
    public void validate_invalidThemeStyle_content_returnsFalse() {
        assertThat(mFieldThemeStyle.validate(ThemeStyle.CONTENT)).isFalse();
    }

    @Test
    public void validate_invalidThemeStyle_clock_returnsFalse() {
        assertThat(mFieldThemeStyle.validate(ThemeStyle.CLOCK)).isFalse();
    }

    @Test
    public void validate_invalidThemeStyle_integer_returnsFalse() {
        assertThat(mFieldThemeStyle.validate(-1)).isFalse();
    }

    @Test
    public void validate_invalidThemeStyle_largePositiveInteger_returnsFalse() {
        assertThat(mFieldThemeStyle.validate(100)).isFalse();
    }

    @Test
    public void getFieldType_returnsIntegerClass() {
        assertThat(mFieldThemeStyle.getFieldType()).isEqualTo(Integer.class);
    }

    @Test
    public void getJsonType_returnsStringClass() {
        assertThat(mFieldThemeStyle.getJsonType()).isEqualTo(String.class);
    }
}
