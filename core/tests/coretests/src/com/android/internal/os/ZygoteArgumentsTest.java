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

package com.android.internal.os;

import static org.junit.Assert.assertArrayEquals;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class ZygoteArgumentsTest {

    @Test
    public void testParseAndMergeCompatChanges() {
        long[] disabled = null;
        disabled = ZygoteArguments.parseAndMergeCompatChanges(
                "--disabled-compat-changes=1,2", disabled);
        disabled = ZygoteArguments.parseAndMergeCompatChanges(
                "--disabled-compat-changes=3", disabled);

        long[] expected = {1, 2, 3};
        assertArrayEquals(expected, disabled);
    }

    @Test
    public void testParseAndMergeCompatChanges_sorted() {
        long[] enabled = null;
        enabled = ZygoteArguments.parseAndMergeCompatChanges(
                "--enabled-compat-changes=6,4", enabled);
        enabled = ZygoteArguments.parseAndMergeCompatChanges(
                "--enabled-compat-changes=5", enabled);

        long[] expected = {4, 5, 6};
        assertArrayEquals(expected, enabled);
    }

    @Test
    public void testParseAndMergeCompatChanges_malformed() {
        long[] changes = null;
        // Test with empty commas
        changes = ZygoteArguments.parseAndMergeCompatChanges(
                "--disabled-compat-changes=1,,2", changes);
        long[] expected = {1, 2};
        assertArrayEquals(expected, changes);
    }

    @Test
    public void testParseAndMergeCompatChanges_dedup() {
        long[] changes = {1, 2};
        // Merge with overlapping IDs and duplicate IDs in argument
        changes = ZygoteArguments.parseAndMergeCompatChanges(
                "--enabled-compat-changes=2,3,3,4", changes);

        long[] expected = {1, 2, 3, 4};
        assertArrayEquals(expected, changes);
    }
}
