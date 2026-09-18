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
package com.android.wm.shell.hierarchy.utils

import android.app.ActivityManager
import android.app.ActivityTaskManager
import android.platform.test.annotations.EnableFlags
import android.view.Display.DEFAULT_DISPLAY
import android.window.WindowContainerToken
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.wm.shell.Flags.FLAG_ENABLE_SHELL_MODES
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.hierarchy.ContainerHierarchy
import com.android.wm.shell.hierarchy.containers.Container
import com.android.wm.shell.hierarchy.properties.DisplayAreaContainerProperties
import com.android.wm.shell.hierarchy.properties.DisplayContainerProperties
import com.android.wm.shell.hierarchy.properties.TaskContainerProperties
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for [HierarchyUtils].
 *
 * Build/Install/Run:
 *  atest WMShellUnitTests:HierarchyUtilsTest
 */
@EnableFlags(FLAG_ENABLE_SHELL_MODES)
@SmallTest
@RunWith(AndroidJUnit4::class)
class HierarchyUtilsTest : ShellTestCase() {

    private val hierarchy = ContainerHierarchy()

    @Test
    fun testResolveParent_displayIsRoot() {
        val root = hierarchy.root
        val display = Container(
            WindowContainerToken.createProxy("test"),
            DisplayContainerProperties(DEFAULT_DISPLAY)
        )

        assertThat(
            HierarchyUtils.resolveParentForContainer(
                root,
                display,
                null,
                DEFAULT_DISPLAY
            )
        ).isEqualTo(root)
    }

    @Test
    fun testResolveParent_displayAreaIsDisplay() {
        val root = hierarchy.root
        val display = Container(
            WindowContainerToken.createProxy("test"),
            DisplayContainerProperties(DEFAULT_DISPLAY)
        ).apply {
            parent = root
        }
        val displayArea = Container(
            WindowContainerToken.createProxy("test"),
            DisplayAreaContainerProperties(0)
        )

        assertThat(
            HierarchyUtils.resolveParentForContainer(
                root,
                displayArea,
                null,
                DEFAULT_DISPLAY
            )
        ).isEqualTo(display)
    }

    @Test
    fun testResolveParent_taskIsTaskDisplayArea() {
        val root = hierarchy.root
        val display = Container(
            WindowContainerToken.createProxy("test"),
            DisplayContainerProperties(DEFAULT_DISPLAY)
        ).apply {
            parent = root
        }
        val displayArea = Container(
            WindowContainerToken.createProxy("test"),
            DisplayAreaContainerProperties(0)
        ).apply {
            parent = display
        }
        val taskInfo = ActivityManager.RunningTaskInfo().apply {
            displayId = DEFAULT_DISPLAY
            parentTaskId = ActivityTaskManager.INVALID_TASK_ID
        }
        val task = Container(
            WindowContainerToken.createProxy("test"),
            TaskContainerProperties(taskInfo)
        )

        assertThat(
            HierarchyUtils.resolveParentForContainer(
                root,
                task,
                null,
                DEFAULT_DISPLAY
            )
        ).isEqualTo(displayArea)
    }

    @Test
    fun testResolveParent_taskIsParentTask() {
        val root = hierarchy.root
        val display = Container(
            WindowContainerToken.createProxy("test"),
            DisplayContainerProperties(DEFAULT_DISPLAY)
        ).apply {
            parent = root
        }
        val displayArea = Container(
            WindowContainerToken.createProxy("test"),
            DisplayAreaContainerProperties(0)
        ).apply {
            parent = display
        }
        val parentTaskInfo = ActivityManager.RunningTaskInfo().apply {
            taskId = 10
        }
        val parentTask =
            Container(
                WindowContainerToken.createProxy("test"),
                TaskContainerProperties(parentTaskInfo)
            ).apply {
                parent = displayArea
            }
        val taskInfo = ActivityManager.RunningTaskInfo().apply {
            parentTaskId = 10
        }
        val task = Container(
            WindowContainerToken.createProxy("test"),
            TaskContainerProperties(taskInfo)
        )

        assertThat(
            HierarchyUtils.resolveParentForContainer(
                root,
                task,
                null,
                DEFAULT_DISPLAY
            )
        ).isEqualTo(parentTask)
    }
}