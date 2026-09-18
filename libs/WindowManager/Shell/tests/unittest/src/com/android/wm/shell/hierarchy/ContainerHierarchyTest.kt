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
package com.android.wm.shell.hierarchy

import android.app.ActivityManager
import android.platform.test.annotations.EnableFlags
import android.window.WindowContainerToken
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.wm.shell.Flags.FLAG_ENABLE_SHELL_MODES
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.hierarchy.containers.Container
import com.android.wm.shell.hierarchy.containers.StubContainer
import com.android.wm.shell.hierarchy.properties.DisplayAreaContainerProperties
import com.android.wm.shell.hierarchy.properties.DisplayContainerProperties
import com.android.wm.shell.hierarchy.properties.TaskContainerProperties
import com.android.wm.shell.hierarchy.utils.HierarchyUtils
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock

/**
 * Tests for [ContainerHierarchy].
 *
 * Build/Install/Run:
 *  atest WMShellUnitTests:ContainerHierarchyTest
 */
@EnableFlags(FLAG_ENABLE_SHELL_MODES)
@SmallTest
@RunWith(AndroidJUnit4::class)
class ContainerHierarchyTest : ShellTestCase() {

    private val hierarchy = ContainerHierarchy()

    @Test
    fun testGetContainer() {
        val display = createDisplayContainer(1)
        val tda = createDisplayAreaContainer(display, 0)
        val task = createTaskContainer(tda, 1)
        tda.parent = display
        task.parent = tda

        assertThat(hierarchy.getContainer(display.token)).isEqualTo(display)
        assertThat(hierarchy.getContainer(tda.token)).isEqualTo(tda)
        assertThat(hierarchy.getContainer(task.token)).isEqualTo(task)
    }

    @Test
    fun testGetContainerByWindowToken() {
        val windowToken = mock(WindowContainerToken::class.java)
        val display = createDisplayContainer(1)
        val tda = createDisplayAreaContainer(display, 0)
        val task = createTaskContainer(tda, 1, windowToken)
        tda.parent = display
        task.parent = tda

        assertThat(hierarchy.getContainer(windowToken)).isEqualTo(task)
        assertThat(hierarchy.getContainer(mock(WindowContainerToken::class.java))).isNull()
    }

    @Test
    fun testGetDisplay() {
        val display = createDisplayContainer(1)

        assertThat(hierarchy.getDisplay(1)).isEqualTo(display)
        assertThat(hierarchy.getDisplay(2)).isNull()
    }

    @Test
    fun testGetTaskDisplayArea() {
        val display = createDisplayContainer(1)
        val tda = createDisplayAreaContainer(display, 0)
        tda.parent = display

        assertThat(hierarchy.getTaskDisplayArea(1)).isEqualTo(tda)
        assertThat(hierarchy.getTaskDisplayArea(2)).isNull()
    }

    @Test
    fun testGetTask() {
        val display = createDisplayContainer(1)
        val tda = createDisplayAreaContainer(display, 0)
        val task = createTaskContainer(tda, 1)
        tda.parent = display
        task.parent = tda

        assertThat(hierarchy.getTask(1)).isEqualTo(task)
        assertThat(hierarchy.getTask(2)).isNull()
    }

    @Test
    fun testGetAncestorDisplay() {
        val display = createDisplayContainer(1)
        val tda = createDisplayAreaContainer(display, 0)
        val task = createTaskContainer(tda, 1)
        val orphan = StubContainer()
        tda.parent = display
        task.parent = tda

        assertThat(HierarchyUtils.getAncestorDisplay(task)).isEqualTo(display)
        assertThat(HierarchyUtils.getAncestorDisplay(display)).isEqualTo(display)
        assertThat(HierarchyUtils.getAncestorDisplay(orphan)).isNull()
    }

    private fun createDisplayContainer(displayId: Int) =
        Container(
            WindowContainerToken.createProxy("test"),
            DisplayContainerProperties(displayId),
        ).apply {
            parent = hierarchy.root
        }

    private fun createDisplayAreaContainer(
        parentDisplay: Container,
        featureId: Int
    ) = Container(
            WindowContainerToken.createProxy("test"),
            DisplayAreaContainerProperties(featureId),
        ).apply { parent = parentDisplay }

    private fun createTaskContainer(
        parentTda: Container,
        taskId: Int,
        windowToken: WindowContainerToken = mock(WindowContainerToken::class.java)
    ) : Container {
        val taskInfo = ActivityManager.RunningTaskInfo()
        taskInfo.taskId = taskId
        return Container(
            windowToken,
            TaskContainerProperties(taskInfo),
        ).apply { parent = parentTda }
    }
}