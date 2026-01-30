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

import android.content.theming.FieldColor;
import android.graphics.Color;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class FieldColorTests {

    private FieldColor mFieldColor;

    @Before
    public void setup() {
        mFieldColor = new FieldColor();
    }

    @Test
    public void parse_validColorWithAlpha_returnsCorrectColor() {
        Color parsedValue = mFieldColor.parse("FF0000FF");
        assertThat(parsedValue).isEqualTo(Color.valueOf(0xFF0000FF));
    }

    @Test
    public void parse_validColorLowercaseWithAlpha_returnsCorrectColor() {
        Color parsedValue = mFieldColor.parse("ff0000ff");
        assertThat(parsedValue).isEqualTo(Color.valueOf(0xFF0000FF));
    }

    @Test
    public void parse_validColorNoAlpha_returnsCorrectColor() {
        Color parsedValue = mFieldColor.parse("0000FF");
        assertThat(parsedValue).isEqualTo(Color.valueOf(Color.parseColor("#0000FF")));
    }

    @Test
    public void parse_validColorNoAlphaLowercase_returnsCorrectColor() {
        Color parsedValue = mFieldColor.parse("0000ff");
        assertThat(parsedValue).isEqualTo(Color.valueOf(Color.parseColor("#0000ff")));
    }

    @Test
    public void parse_invalidColor_short_returnsNull() {
        Color parsedValue = mFieldColor.parse("12345");
        assertThat(parsedValue).isNull();
    }

    @Test
    public void parse_invalidColor_long_returnsNull() {
        Color parsedValue = mFieldColor.parse("123456789");
        assertThat(parsedValue).isNull();
    }

    @Test
    public void parse_invalidColor_characters_returnsNull() {
        Color parsedValue = mFieldColor.parse("GGHHII");
        assertThat(parsedValue).isNull();
    }

    @Test
    public void parse_nullColor_returnsNull() {
        Color parsedValue = mFieldColor.parse(null);
        assertThat(parsedValue).isNull();
    }

    @Test
    public void serialize_validColorWithAlpha_returnsCorrectString() {
        String serializedValue = mFieldColor.serialize(Color.valueOf(0xFFFF0000));
        assertThat(serializedValue).isEqualTo("FFFF0000");
    }

    @Test
    public void serialize_validColorBlack_returnsCorrectString() {
        String serializedValue = mFieldColor.serialize(Color.valueOf(Color.BLACK));
        assertThat(serializedValue).isEqualTo("FF000000");
    }

    @Test
    public void serialize_transparentColor_returnsZeroString() {
        String serializedValue = mFieldColor.serialize(Color.valueOf(Color.TRANSPARENT));
        assertThat(serializedValue).isEqualTo("0");
    }

    @Test
    public void validate_validOpaqueColor_returnsTrue() {
        assertThat(mFieldColor.validate(Color.valueOf(0xFF00FF00))).isTrue();
    }

    @Test
    public void validate_validNonOpaqueColor_returnsTrue() {
        assertThat(mFieldColor.validate(Color.valueOf(0x8000FF00))).isTrue();
    }

    @Test
    public void validate_transparentColor_returnsFalse() {
        assertThat(mFieldColor.validate(Color.valueOf(Color.TRANSPARENT))).isFalse();
    }

    @Test
    public void getFieldType_returnsColorClass() {
        assertThat(mFieldColor.getFieldType()).isEqualTo(Color.class);
    }

    @Test
    public void getJsonType_returnsStringClass() {
        assertThat(mFieldColor.getJsonType()).isEqualTo(String.class);
    }
}