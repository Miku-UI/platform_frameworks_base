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

import android.content.theming.FieldColorSource;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;


@RunWith(JUnit4.class)
public class FieldColorSourceTests {
    private FieldColorSource mFieldColorSource;

    @Before
    public void setup() {
        mFieldColorSource = new FieldColorSource();
    }

    @Test
    public void parse_validColorSource_returnsSameString() {
        String validColorSource = FieldColorSource.VALUE_HOME_WALLPAPER;
        String parsedValue = mFieldColorSource.parse(validColorSource);
        assertThat(parsedValue).isEqualTo(validColorSource);
    }

    @Test
    public void parse_anotherValidColorSource_returnsSameString() {
        String validColorSource = FieldColorSource.VALUE_PRESET;
        String parsedValue = mFieldColorSource.parse(validColorSource);
        assertThat(parsedValue).isEqualTo(validColorSource);
    }

    @Test
    public void parse_anyString_returnsSameString() {
        String anyString = "any_custom_string";
        String parsedValue = mFieldColorSource.parse(anyString);
        assertThat(parsedValue).isEqualTo(anyString);
    }

    @Test
    public void parse_nullString_returnsNull() {
        String parsedValue = mFieldColorSource.parse(null);
        assertThat(parsedValue).isNull();
    }

    @Test
    public void serialize_validColorSource_returnsSameString() {
        String validColorSource =
                FieldColorSource.VALUE_HOME_WALLPAPER; // Changed from VALUE_LOCK_WALLPAPER as
        // it's not in FieldColorSource.java
        String serializedValue = mFieldColorSource.serialize(validColorSource);
        assertThat(serializedValue).isEqualTo(validColorSource);
    }

    @Test
    public void serialize_anotherValidColorSource_returnsSameString() {
        String validColorSource = FieldColorSource.VALUE_PRESET;
        String serializedValue = mFieldColorSource.serialize(validColorSource);
        assertThat(serializedValue).isEqualTo(validColorSource);
    }

    @Test
    public void validate_preset_returnsTrue() {
        assertThat(mFieldColorSource.validate(FieldColorSource.VALUE_PRESET)).isTrue();
    }

    @Test
    public void validate_homeWallpaper_returnsTrue() {
        assertThat(mFieldColorSource.validate(FieldColorSource.VALUE_HOME_WALLPAPER)).isTrue();
    }

    @Test
    public void validate_lockWallpaper_returnsFalse() {
        assertThat(mFieldColorSource.validate("lock_wallpaper")).isFalse();
    }

    @Test
    public void validate_invalidColorSource_returnsFalse() {
        assertThat(mFieldColorSource.validate("invalid")).isFalse();
    }

    @Test
    public void getFieldType_returnsStringClass() {
        assertThat(mFieldColorSource.getFieldType()).isEqualTo(String.class);
    }

    @Test
    public void getJsonType_returnsStringClass() {
        assertThat(mFieldColorSource.getJsonType()).isEqualTo(String.class);
    }
}
