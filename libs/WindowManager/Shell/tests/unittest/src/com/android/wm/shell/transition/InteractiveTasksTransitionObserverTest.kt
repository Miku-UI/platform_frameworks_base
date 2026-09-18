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

package com.android.wm.shell.transition

import android.app.ActivityManager.RunningTaskInfo
import android.content.Intent
import android.os.IBinder
import android.view.Display.DEFAULT_DISPLAY
import android.view.SurfaceControl
import android.view.WindowManager.TRANSIT_CHANGE
import android.window.TransitionInfo
import androidx.test.filters.SmallTest
import com.android.testing.wm.util.MockToken
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.sysui.ShellInit
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

/**
 * Tests for [InteractiveTasksTransitionObserver].
 *
 * Build/Install/Run: atest WMShellUnitTests:InteractiveTasksTransitionObserverTest
 */
@SmallTest
class InteractiveTasksTransitionObserverTest : ShellTestCase() {

    private val shellInit = mock<ShellInit>()
    private val transitions = mock<Transitions>()
    private val surfaceControl = mock<SurfaceControl>()
    private val startTransaction = mock<SurfaceControl.Transaction>()
    private val finishTransaction = mock<SurfaceControl.Transaction>()
    private val transition = mock<IBinder>()
    private val shellTaskOrganizer = mock<ShellTaskOrganizer>()

    private lateinit var observer: InteractiveTasksTransitionObserver

    @Before
    fun setUp() {
        observer = InteractiveTasksTransitionObserver(shellInit, transitions, shellTaskOrganizer)
    }

    @Test
    fun testOnTransitionReady_updatesTaskOrganizer() {
        val taskInfo =
            createTask(taskId = TASK_ID_0, displayId = DEFAULT_DISPLAY, isInteractive = true)
        val token = MockToken.token()
        val change = TransitionInfo.Change(token, surfaceControl)
        change.taskInfo = taskInfo
        val info = TransitionInfo(TRANSIT_CHANGE, 0)
        info.addChange(change)

        observer.onTransitionReady(transition, info, startTransaction, finishTransaction)

        verify(shellTaskOrganizer).onTaskInfoUpdated(taskInfo)
    }

    private fun createTask(
        taskId: Int,
        displayId: Int,
        isInteractive: Boolean,
        baseIntent: Intent = Intent(),
    ): RunningTaskInfo {
        val info = RunningTaskInfo()
        info.taskId = taskId
        info.displayId = displayId
        info.isInteractive = isInteractive
        info.baseIntent = baseIntent
        return info
    }

    private companion object {
        const val TASK_ID_0 = 0
    }
}
