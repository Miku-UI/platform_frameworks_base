/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.wm.shell.bubbles

import android.app.ActivityManager
import android.content.Context
import android.view.SurfaceControl
import com.android.testing.wm.util.MockToken
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.common.ShellExecutor
import com.android.wm.shell.taskview.TaskView
import com.android.wm.shell.taskview.TaskViewController
import com.android.wm.shell.taskview.TaskViewTaskController
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.whenever

/** Implementation of [BubbleTaskViewFactory] for testing. */
class FakeBubbleTaskViewFactory(
    private val context: Context,
    private val mainExecutor: ShellExecutor,
) : BubbleTaskViewFactory {
    override fun create(): BubbleTaskView {
        val taskViewTaskController = mock<TaskViewTaskController>()
        val taskLeash = SurfaceControl.Builder().setName("taskViewLeash").build()
        val taskView =
            spy(TaskView(context, mock<TaskViewController>(), taskViewTaskController)) {
                on { surfaceControl } doReturn taskLeash
            }
        val taskInfo = mock<ActivityManager.RunningTaskInfo>().apply { token = MockToken().token() }
        val bubbleHelper = mock<BubbleHelper>()
        val bubbleController =
            mock<BubbleController> { on { getBubbleHelper() } doReturn bubbleHelper }
        whenever(taskViewTaskController.taskInfo).thenReturn(taskInfo)
        whenever(taskViewTaskController.taskOrganizer).thenReturn(mock<ShellTaskOrganizer>())
        return BubbleTaskView(taskView, mainExecutor, bubbleController)
    }
}
