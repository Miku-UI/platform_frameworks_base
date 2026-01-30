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

import android.content.theming.FieldColorIndex;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class FieldColorIndexTests {
    private FieldColorIndex mFieldColorIndex;

    @Before
    public void setup() {
        mFieldColorIndex = new FieldColorIndex();
    }

    @Test
    public void parse_validColorIndex_returnsCorrectInteger() {
        Integer parsedValue = mFieldColorIndex.parse("10");
        assertThat(parsedValue).isEqualTo(10);
    }

    @Test
    public void parse_negativeColorIndex_returnsCorrectInteger() {
        Integer parsedValue = mFieldColorIndex.parse("-1");
        assertThat(parsedValue).isEqualTo(-1);
    }

    @Test
    public void parse_zeroColorIndex_returnsCorrectInteger() {
        Integer parsedValue = mFieldColorIndex.parse("0");
        assertThat(parsedValue).isEqualTo(0);
    }

    @Test
    public void parse_invalidColorIndex_nonNumeric_returnsNull() {
        Integer parsedValue = mFieldColorIndex.parse("invalid");
        assertThat(parsedValue).isNull();
    }

    @Test
    public void parse_invalidColorIndex_emptyString_returnsNull() {
        Integer parsedValue = mFieldColorIndex.parse("");
        assertThat(parsedValue).isNull();
    }

    @Test
    public void parse_nullString_returnsNull() {
        Integer parsedValue = mFieldColorIndex.parse(null);
        assertThat(parsedValue).isNull();
    }


    @Test
    public void serialize_validColorIndex_returnsCorrectString() {
        String serializedValue = mFieldColorIndex.serialize(15);
        assertThat(serializedValue).isEqualTo("15");
    }

    @Test
    public void serialize_negativeColorIndex_returnsCorrectString() {
        String serializedValue = mFieldColorIndex.serialize(-1);
        assertThat(serializedValue).isEqualTo("-1");
    }

    @Test
    public void serialize_zeroColorIndex_returnsCorrectString() {
        String serializedValue = mFieldColorIndex.serialize(0);
        assertThat(serializedValue).isEqualTo("0");
    }

    @Test
    public void validate_validPositiveColorIndex_returnsTrue() {
        assertThat(mFieldColorIndex.validate(5)).isTrue();
    }

    @Test
    public void validate_validNegativeOneColorIndex_returnsTrue() {
        assertThat(mFieldColorIndex.validate(-1)).isTrue();
    }

    @Test
    public void validate_validZeroColorIndex_returnsTrue() {
        assertThat(mFieldColorIndex.validate(0)).isTrue();
    }

    @Test
    public void validate_invalidColorIndex_lessThanNegativeOne_returnsFalse() {
        assertThat(mFieldColorIndex.validate(-2)).isFalse();
    }

    @Test
    public void getFieldType_returnsIntegerClass() {
        assertThat(mFieldColorIndex.getFieldType()).isEqualTo(Integer.class);
    }

    @Test
    public void getJsonType_returnsStringClass() {
        assertThat(mFieldColorIndex.getJsonType()).isEqualTo(String.class);
    }
}
