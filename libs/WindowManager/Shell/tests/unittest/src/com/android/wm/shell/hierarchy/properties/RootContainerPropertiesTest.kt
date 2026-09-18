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
package com.android.wm.shell.hierarchy.properties

import android.platform.test.annotations.EnableFlags
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.wm.shell.Flags.FLAG_ENABLE_SHELL_MODES
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.hierarchy.updates.HierarchyChangeFlags
import com.android.wm.shell.hierarchy.updates.HierarchySnapshot
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for [RootContainerProperties].
 *
 * Build/Install/Run:
 *  atest WMShellUnitTests:RootContainerPropertiesTest
 */
@EnableFlags(FLAG_ENABLE_SHELL_MODES)
@SmallTest
@RunWith(AndroidJUnit4::class)
class RootContainerPropertiesTest : ShellTestCase() {

    @Test
    fun testCopy() {
        val properties = RootContainerProperties()
        properties.focusState.globallyFocusedDisplayId = 1234
        properties.focusState.globallyFocusedTaskId = 1
        properties.focusState.perDisplayFocusedTaskId = mutableMapOf(
            0 to 1,
            1 to 2,
        )

        val copy = properties.copy()

        assertThat(copy.focusState.globallyFocusedDisplayId)
            .isEqualTo(properties.focusState.globallyFocusedDisplayId)
        assertThat(copy.focusState.globallyFocusedTaskId)
            .isEqualTo(properties.focusState.globallyFocusedTaskId)
        assertThat(copy.focusState.perDisplayFocusedTaskId)
            .isEqualTo(properties.focusState.perDisplayFocusedTaskId)

        // Ensure it's a deep copy
        assertThat(copy.config).isNotSameInstanceAs(properties.config)
    }

    @Test
    fun testDiff_globallyFocusedDisplayIdChanged() {
        val properties1 = RootContainerProperties()
        properties1.focusState.globallyFocusedDisplayId = 1234
        val properties2 = RootContainerProperties()
        properties2.focusState.globallyFocusedDisplayId = 5678

        val chgs = HierarchyChangeFlags()
        properties1.diff(properties2, chgs)

        assertThat(chgs.get(HierarchySnapshot.CHANGED_FOCUS)).isTrue()
    }

    @Test
    fun testDiff_globallyFocusedTaskIdChanged() {
        val properties1 = RootContainerProperties()
        properties1.focusState.globallyFocusedTaskId = 1
        val properties2 = RootContainerProperties()
        properties2.focusState.globallyFocusedTaskId = 2

        val chgs = HierarchyChangeFlags()
        properties1.diff(properties2, chgs)

        assertThat(chgs.get(HierarchySnapshot.CHANGED_FOCUS)).isTrue()
    }

    @Test
    fun testDiff_perDisplayFocusedTaskChanged() {
        val properties1 = RootContainerProperties()
        properties1.focusState.perDisplayFocusedTaskId = mutableMapOf(
            0 to 1,
            1 to 2,
        )
        val properties2 = RootContainerProperties()
        properties2.focusState.perDisplayFocusedTaskId = mutableMapOf(
            0 to 2,
            1 to 3,
        )

        val chgs = HierarchyChangeFlags()
        properties1.diff(properties2, chgs)

        assertThat(chgs.get(HierarchySnapshot.CHANGED_FOCUS)).isTrue()
    }
}