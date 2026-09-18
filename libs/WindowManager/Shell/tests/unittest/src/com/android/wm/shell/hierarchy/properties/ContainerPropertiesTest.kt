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

import android.app.WindowConfiguration
import android.graphics.Rect
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
 * Tests for [ContainerProperties].
 *
 * Build/Install/Run:
 *  atest WMShellUnitTests:WindowContainerPropertiesTest
 */
@EnableFlags(FLAG_ENABLE_SHELL_MODES)
@SmallTest
@RunWith(AndroidJUnit4::class)
class ContainerPropertiesTest : ShellTestCase() {

    @Test
    fun testCopy() {
        val properties = ContainerProperties(userId = 1)
        properties.visibleRequested = true
        properties.config.windowConfiguration.setBounds(Rect(1, 2, 3, 4))
        properties.config.windowConfiguration.setRotation(1)
        properties.config.windowConfiguration.setWindowingMode(
            WindowConfiguration.WINDOWING_MODE_FULLSCREEN
        )

        val copy = properties.copy()

        assertThat(copy.userId).isEqualTo(properties.userId)
        assertThat(copy.visibleRequested).isEqualTo(properties.visibleRequested)
        assertThat(copy.bounds).isEqualTo(properties.bounds)
        assertThat(copy.rotation).isEqualTo(properties.rotation)
        assertThat(copy.windowingMode).isEqualTo(properties.windowingMode)

        // Ensure it's a deep copy
        assertThat(copy.config).isNotSameInstanceAs(properties.config)
    }

    @Test
    fun testDiff_noDifference() {
        val properties1 = ContainerProperties()
        val properties2 = ContainerProperties()

        val chgs = HierarchyChangeFlags()
        properties1.diff(properties2, chgs)

        assertThat(chgs.isEmpty).isTrue()
    }

    @Test
    fun testDiff_boundsChanged() {
        val properties1 = ContainerProperties()
        properties1.config.windowConfiguration.setBounds(Rect(0, 0, 100, 100))
        val properties2 = ContainerProperties()
        properties2.config.windowConfiguration.setBounds(Rect(0, 0, 200, 200))

        val chgs = HierarchyChangeFlags()
        properties1.diff(properties2, chgs)

        assertThat(chgs.get(HierarchySnapshot.CHANGED_BOUNDS)).isTrue()
    }

    @Test
    fun testDiff_rotationChanged() {
        val properties1 = ContainerProperties()
        properties1.config.windowConfiguration.setRotation(1)
        val properties2 = ContainerProperties()
        properties2.config.windowConfiguration.setRotation(2)

        val chgs = HierarchyChangeFlags()
        properties1.diff(properties2, chgs)

        assertThat(chgs.get(HierarchySnapshot.CHANGED_ROTATION)).isTrue()
    }

    @Test
    fun testDiff_windowingModeChanged() {
        val properties1 = ContainerProperties()
        properties1.config.windowConfiguration.setWindowingMode(
            WindowConfiguration.WINDOWING_MODE_FULLSCREEN
        )
        val properties2 = ContainerProperties()
        properties2.config.windowConfiguration.setWindowingMode(
            WindowConfiguration.WINDOWING_MODE_PINNED
        )

        val chgs = HierarchyChangeFlags()
        properties1.diff(properties2, chgs)

        assertThat(chgs.get(HierarchySnapshot.CHANGED_WINDOWING_MODE)).isTrue()
    }

    @Test
    fun testDiff_multipleChanges() {
        val properties1 = ContainerProperties()
        properties1.config.windowConfiguration.setBounds(Rect(0, 0, 100, 100))
        properties1.config.windowConfiguration.setRotation(1)
        val properties2 = ContainerProperties()
        properties2.config.windowConfiguration.setBounds(Rect(0, 0, 200, 200))
        properties2.config.windowConfiguration.setRotation(2)
        properties2.config.windowConfiguration.setWindowingMode(
            WindowConfiguration.WINDOWING_MODE_PINNED
        )

        val chgs = HierarchyChangeFlags()
        properties1.diff(properties2, chgs)

        assertThat(chgs.get(HierarchySnapshot.CHANGED_BOUNDS)).isTrue()
        assertThat(chgs.get(HierarchySnapshot.CHANGED_ROTATION)).isTrue()
        assertThat(chgs.get(HierarchySnapshot.CHANGED_WINDOWING_MODE)).isTrue()
    }
}