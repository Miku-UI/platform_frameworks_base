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
package com.android.wm.shell.hierarchy.updates

import android.app.WindowConfiguration.WINDOWING_MODE_MULTI_WINDOW
import android.graphics.Rect
import android.platform.test.annotations.EnableFlags
import android.view.Surface.ROTATION_90
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.wm.shell.Flags.FLAG_ENABLE_SHELL_MODES
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.hierarchy.ContainerHierarchy
import com.android.wm.shell.hierarchy.containers.StubContainer
import com.android.wm.shell.hierarchy.modes.StubMode
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for [HierarchySnapshot].
 *
 * Build/Install/Run:
 *  atest WMShellUnitTests:HierarchySnapshotTest
 */
@EnableFlags(FLAG_ENABLE_SHELL_MODES)
@SmallTest
@RunWith(AndroidJUnit4::class)
class HierarchySnapshotTest : ShellTestCase() {

    private val hierarchy = ContainerHierarchy()

    @Test
    fun testSaveAndGetContainerState() {
        val container = StubContainer()
        container.parent = hierarchy.root

        val snapshot = HierarchySnapshot(hierarchy.toContainerList())

        // No changes should be reported for the same container
        val chgs = snapshot.getChanges(container)
        assertThat(chgs.isEmpty).isTrue()

        // The saved state should be retrievable and match the original container
        val savedState = snapshot.snapshots.getValue(container)
        assertThat(savedState.parentToken).isEqualTo(container.parent?.token)
        assertThat(savedState.children).isEqualTo(container.children)
        assertThat(savedState.props.bounds).isEqualTo(container.props.bounds)
        assertThat(savedState.props.rotation).isEqualTo(container.props.rotation)
        assertThat(savedState.props.windowingMode).isEqualTo(container.props.windowingMode)
    }

    @Test
    fun testSaveAndGetContainerMode() {
        val root = hierarchy.root
        val parentA = StubContainer()
        val parentB = StubContainer()
        val container = StubContainer()
        parentA.parent = root
        parentA.mode = StubMode()
        parentB.parent = parentA
        parentB.mode = StubMode()
        container.parent = parentB

        val snapshot = HierarchySnapshot(hierarchy.toContainerList())

        // The saved mode should be retrievable and match the original mode
        assertThat(snapshot.snapshots.getValue(container).mode).isEqualTo(parentB.mode!!)
    }

    @Test
    fun testDiffChanges() {
        val root = hierarchy.root
        val parentA = StubContainer()
        val parentB = StubContainer()
        val container = StubContainer()
        parentA.parent = root
        parentA.mode = StubMode()
        parentB.parent = parentA
        parentB.mode = StubMode()
        container.parent = parentB

        val snapshot = HierarchySnapshot(hierarchy.toContainerList())

        // Modify the container's properties
        val newParent = StubContainer()
        newParent.parent = root
        container.parent = newParent
        container.children.add(StubContainer())
        container.props.visibleRequested = true
        container.props.config.windowConfiguration.setBounds(Rect(0, 0, 100, 100))
        container.props.config.windowConfiguration.setRotation(ROTATION_90)
        container.props.config.windowConfiguration.setWindowingMode(WINDOWING_MODE_MULTI_WINDOW)
        parentB.mode = StubMode()

        val expectedChanges = HierarchyChangeFlags(
            HierarchySnapshot.CHANGED_PARENT,
            HierarchySnapshot.CHANGED_MODE,
            HierarchySnapshot.CHANGED_VISIBILITY,
            HierarchySnapshot.CHANGED_CHILDREN,
            HierarchySnapshot.CHANGED_BOUNDS,
            HierarchySnapshot.CHANGED_ROTATION,
            HierarchySnapshot.CHANGED_WINDOWING_MODE,
        )

        assertThat(snapshot.getChanges(container)).isEqualTo(expectedChanges)
    }
}