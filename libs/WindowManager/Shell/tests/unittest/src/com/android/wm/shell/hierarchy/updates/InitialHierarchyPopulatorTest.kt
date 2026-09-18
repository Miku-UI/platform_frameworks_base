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

import android.app.ActivityManager
import android.app.ActivityTaskManager
import android.platform.test.annotations.EnableFlags
import android.util.Pair
import android.view.SurfaceControl
import android.window.DisplayAreaInfo
import android.window.WindowContainerToken
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.wm.shell.Flags.FLAG_ENABLE_SHELL_MODES
import com.android.wm.shell.RootDisplayAreaOrganizer
import com.android.wm.shell.RootTaskDisplayAreaOrganizer
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.hierarchy.ContainerHierarchy
import com.android.wm.shell.sysui.ShellInit
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub

/**
 * Tests for [InitialHierarchyPopulator].
 *
 * Build/Install/Run:
 *  atest WMShellUnitTests:InitialContainerHierarchyPopulatorTest
 */
@EnableFlags(FLAG_ENABLE_SHELL_MODES)
@SmallTest
@RunWith(AndroidJUnit4::class)
class InitialHierarchyPopulatorTest : ShellTestCase() {

    private val hierarchy = ContainerHierarchy().apply {
        root.leash = mock<SurfaceControl>()
    }
    private val shellTaskOrganizer = mock<ShellTaskOrganizer>()
    private val rootDisplayAreaOrganizer = mock<RootDisplayAreaOrganizer>()
    private val taskDisplayAreaOrganizer = mock<RootTaskDisplayAreaOrganizer>()
    private val updater = mock<HierarchyUpdater>()
    private val shellInit = mock<ShellInit>()

    private val populator = InitialHierarchyPopulator(
        shellTaskOrganizer,
        rootDisplayAreaOrganizer,
        taskDisplayAreaOrganizer,
        hierarchy,
        updater,
        shellInit,
    )

    @Test
    fun onInit_createsHierarchy() {
        // Create a display
        val displayInfo = DisplayAreaInfo(WindowContainerToken.createProxy("test"), 0, 0)
        // Create a task display area for that display
        val tdaInfo = DisplayAreaInfo(WindowContainerToken.createProxy("test"), 0, 1)
        // Create some tasks
        val taskInfo = ActivityManager.RunningTaskInfo()
        taskInfo.taskId = 1
        taskInfo.displayId = 0
        taskInfo.parentTaskId = ActivityTaskManager.INVALID_TASK_ID
        taskInfo.token = WindowContainerToken.createProxy("test")
        val childTaskInfo = ActivityManager.RunningTaskInfo()
        childTaskInfo.taskId = 2
        childTaskInfo.displayId = 0
        childTaskInfo.parentTaskId = 1
        childTaskInfo.token = WindowContainerToken.createProxy("test")

        // Stub out the methods to return the right infos
        rootDisplayAreaOrganizer.stub {
            on { getInitialRootDisplayAreas() } doAnswer {
                listOf(Pair(displayInfo, mock<SurfaceControl>()))
            }
        }

        taskDisplayAreaOrganizer.stub {
            on { getInitialTaskDisplayAreas() } doAnswer {
                listOf(Pair(tdaInfo, mock<SurfaceControl>()))
            }
        }

        shellTaskOrganizer.stub {
            on { getInitialTasks() } doAnswer {
                listOf(
                    Pair(taskInfo, mock<SurfaceControl>()),
                    Pair(childTaskInfo, mock<SurfaceControl>())
                )
            }
        }

        // Trigger the population of the hierarchy
        populator.onInit()

        // Verify the hierarchy
        val display = hierarchy.getDisplay(0)
        assertThat(display).isNotNull()
        assertThat(display?.parent).isEqualTo(hierarchy.root)

        val tda = hierarchy.getTaskDisplayArea(0)
        assertThat(tda).isNotNull()
        assertThat(tda?.parent).isEqualTo(display)

        val task = hierarchy.getTask(1)
        assertThat(task).isNotNull()
        assertThat(task?.parent).isEqualTo(tda)

        val childTask = hierarchy.getTask(2)
        assertThat(childTask).isNotNull()
        assertThat(childTask?.parent).isEqualTo(task)
    }
}