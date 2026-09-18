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

package android.app;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.compat.Compatibility;
import android.util.LongSparseArray;

import com.android.internal.compat.CompatibilityChangeInfo;
import com.android.internal.compat.CompatibilityRules;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class AppCompatCallbacksTest {

    @Before
    public void setUp() {
        CompatibilityRules.reset();
    }

    @Test
    public void testIsChangeEnabled_PreloadedRules() {
        LongSparseArray<CompatibilityChangeInfo> rules = new LongSparseArray<>();
        // Change 1: Enabled in rules
        rules.put(
                1L,
                new CompatibilityChangeInfo(
                        1L, "CHANGE_1", -1, -1, false, false, false, "", false));
        // Change 2: Disabled in rules
        rules.put(
                2L,
                new CompatibilityChangeInfo(2L, "CHANGE_2", -1, -1, true, false, false, "", false));
        // Change 3: Enable since SDK 31
        rules.put(
                3L,
                new CompatibilityChangeInfo(
                        3L, "CHANGE_3", -1, 31, false, false, false, "", false));

        CompatibilityRules.init(rules);

        // Install callbacks with target SDK 30.
        // CHANGE_1: Enabled (rule)
        // CHANGE_2: Disabled (rule)
        // CHANGE_3: Disabled (target SDK 30 < 31)
        AppCompatCallbacks.install(new long[0], new long[0], new long[0], false, 30);

        assertTrue(Compatibility.isChangeEnabled(1L));
        assertFalse(Compatibility.isChangeEnabled(2L));
        assertFalse(Compatibility.isChangeEnabled(3L));

        // Re-install with target SDK 31
        AppCompatCallbacks.install(new long[0], new long[0], new long[0], false, 31);
        assertTrue(Compatibility.isChangeEnabled(3L));
    }

    @Test
    public void testIsChangeEnabled_Overrides() {
        LongSparseArray<CompatibilityChangeInfo> rules = new LongSparseArray<>();
        // Change 1: Enabled in rules, but process-disabled
        rules.put(
                1L,
                new CompatibilityChangeInfo(
                        1L, "CHANGE_1", -1, -1, false, false, false, "", false));
        // Change 2: Disabled in rules, but process-enabled
        rules.put(
                2L,
                new CompatibilityChangeInfo(2L, "CHANGE_2", -1, -1, true, false, false, "", false));

        CompatibilityRules.init(rules);

        // process-disabled: 1
        // process-enabled: 2
        AppCompatCallbacks.install(new long[] {1L}, new long[] {2L}, new long[0], false, 30);

        assertFalse(Compatibility.isChangeEnabled(1L));
        assertTrue(Compatibility.isChangeEnabled(2L));
        // Unknown change should be enabled by default
        assertTrue(Compatibility.isChangeEnabled(99L));
    }
}
