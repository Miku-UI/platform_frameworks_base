/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.server.dreams;

import static com.google.common.truth.Truth.assertThat;

import android.content.ComponentName;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class DreamComponentNameUtilsTest {

    private static final ComponentName DREAM_1 =
            ComponentName.unflattenFromString("com.test/.Dream1");
    private static final ComponentName DREAM_2 =
            ComponentName.unflattenFromString("com.test/.Dream2");

    @Test
    public void testToCommaSeparatedString_Null() {
        assertThat(DreamComponentNameUtils.toCommaSeparatedString(null)).isEmpty();
    }

    @Test
    public void testToCommaSeparatedString_EmptyArray() {
        assertThat(DreamComponentNameUtils.toCommaSeparatedString(new ComponentName[0])).isEmpty();
    }

    @Test
    public void testToCommaSeparatedString_SingleElement() {
        ComponentName[] components = new ComponentName[] {DREAM_1};
        assertThat(DreamComponentNameUtils.toCommaSeparatedString(components))
                .isEqualTo(DREAM_1.flattenToString());
    }

    @Test
    public void testToCommaSeparatedString_MultipleElements() {
        ComponentName[] components = new ComponentName[] {DREAM_1, DREAM_2};
        assertThat(DreamComponentNameUtils.toCommaSeparatedString(components))
                .isEqualTo(DREAM_1.flattenToString() + "," + DREAM_2.flattenToString());
    }

    @Test
    public void testToCommaSeparatedString_WithNullElements() {
        ComponentName[] components = new ComponentName[] {DREAM_1, null, DREAM_2};
        assertThat(DreamComponentNameUtils.toCommaSeparatedString(components))
                .isEqualTo(DREAM_1.flattenToString() + "," + DREAM_2.flattenToString());
    }

    @Test
    public void testFromCommaSeparatedString_Null() {
        assertThat(DreamComponentNameUtils.fromCommaSeparatedString(null)).isEmpty();
    }

    @Test
    public void testFromCommaSeparatedString_EmptyString() {
        assertThat(DreamComponentNameUtils.fromCommaSeparatedString("")).isEmpty();
    }

    @Test
    public void testFromCommaSeparatedString_SingleElement() {
        String input = DREAM_1.flattenToString();
        ComponentName[] result = DreamComponentNameUtils.fromCommaSeparatedString(input);
        assertThat(result).hasLength(1);
        assertThat(result[0]).isEqualTo(DREAM_1);
    }

    @Test
    public void testFromCommaSeparatedString_MultipleElements() {
        String input = DREAM_1.flattenToString() + "," + DREAM_2.flattenToString();
        ComponentName[] result = DreamComponentNameUtils.fromCommaSeparatedString(input);
        assertThat(result).hasLength(2);
        assertThat(result[0]).isEqualTo(DREAM_1);
        assertThat(result[1]).isEqualTo(DREAM_2);
    }

    @Test
    public void testFromCommaSeparatedString_MalformedString() {
        String input = DREAM_1.flattenToString() + ",malformed,," + DREAM_2.flattenToString();
        ComponentName[] result = DreamComponentNameUtils.fromCommaSeparatedString(input);
        assertThat(result).hasLength(2);
        assertThat(result[0]).isEqualTo(DREAM_1);
        assertThat(result[1]).isEqualTo(DREAM_2);
    }

    @Test
    public void testFromCommaSeparatedString_OnlyCommas() {
        assertThat(DreamComponentNameUtils.fromCommaSeparatedString(",,,")).isEmpty();
    }
}
