/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.wm.shell.windowdecor.caption

import android.content.Context
import android.testing.AndroidTestingRunner
import android.view.Display
import android.view.SurfaceControl
import android.view.View
import android.view.ViewGroup
import android.window.WindowContainerTransaction
import com.android.testing.wm.util.StubTransaction
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.desktopmode.DesktopTestHelpers.createFreeformTask
import com.android.wm.shell.windowdecor.TaskFocusStateConsumer
import com.android.wm.shell.windowdecor.WindowDecoration2
import com.android.wm.shell.windowdecor.common.viewhost.WindowDecorViewHost
import com.android.wm.shell.windowdecor.common.viewhost.WindowDecorViewHostSupplier
import com.android.wm.shell.windowdecor.viewholder.WindowDecorationViewHolder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.never
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Tests for [CaptionController].
 *
 * Build/Install/Run: atest WMShellUnitTests:CaptionControllerTest
 */
@RunWith(AndroidTestingRunner::class)
@ExperimentalCoroutinesApi
class CaptionControllerTest : ShellTestCase() {
    private val mockSurfaceControl = mock<SurfaceControl>()
    private val mockViewHost = mock<WindowDecorViewHost>()
    private val mockViewHostSupplier = mock<WindowDecorViewHostSupplier<WindowDecorViewHost>>()

    private val mockTaskOrganizer = mock<ShellTaskOrganizer>()

    private val mockRootView = mock<TestCaptionView>()
    private val mockParentView = mock<ViewGroup>()
    private val mockWindowDecorViewHolder =
        mock<WindowDecorationViewHolder<WindowDecorationViewHolder.Data>>()

    private val stubStartTransaction = StubTransaction()
    private val stubFinishTransaction = StubTransaction()

    private val testDispatcher = StandardTestDispatcher(TestCoroutineScheduler())
    private val testScope = TestScope(testDispatcher)

    @Before
    fun setUp() {
        doReturn(true).whenever(mockSurfaceControl).isValid
        doReturn(mockSurfaceControl).whenever(mockViewHost).surfaceControl
        doReturn(mockViewHost).whenever(mockViewHostSupplier).acquire(any(), any())

        doReturn(mockRootView).whenever(mockWindowDecorViewHolder).rootView
        doReturn(mockParentView).whenever(mockRootView).parent
    }

    @Test
    fun relayout_runBackgroundTaskBeforeSurfaceReleased() =
        testScope.runTest {
            val taskInfo = createFreeformTask()

            val target =
                TestCaptionController(
                    captionType = CaptionController.CaptionType.APP_HEADER,
                    taskInfo = taskInfo,
                    windowDecorViewHostSupplier = mockViewHostSupplier,
                    taskOrganizer = mockTaskOrganizer,
                    testScope = testScope,
                )

            val params =
                WindowDecoration2.RelayoutParams(
                    runningTaskInfo = taskInfo,
                    captionType = CaptionController.CaptionType.APP_HEADER,
                )
            target.relayout(
                params = params,
                parentContainer = mock<SurfaceControl>(),
                display = mock<Display>(),
                decorWindowContext = context,
                startT = stubStartTransaction,
                finishT = stubFinishTransaction,
                wct = mock<WindowContainerTransaction>(),
            )
            runCurrent()

            verify(mockTaskOrganizer).setExcludeLayersFromTaskSnapshot(any(), any())
        }

    @Test
    fun relayout_runBackgroundTaskAfterSurfaceReleased() =
        testScope.runTest {
            val taskInfo = createFreeformTask()

            val target =
                TestCaptionController(
                    captionType = CaptionController.CaptionType.APP_HEADER,
                    taskInfo = taskInfo,
                    windowDecorViewHostSupplier = mockViewHostSupplier,
                    taskOrganizer = mockTaskOrganizer,
                    testScope = testScope,
                )

            val params =
                WindowDecoration2.RelayoutParams(
                    runningTaskInfo = taskInfo,
                    captionType = CaptionController.CaptionType.APP_HEADER,
                )
            target.relayout(
                params = params,
                parentContainer = mock<SurfaceControl>(),
                display = mock<Display>(),
                decorWindowContext = context,
                startT = stubStartTransaction,
                finishT = stubFinishTransaction,
                wct = mock<WindowContainerTransaction>(),
            )
            doReturn(false).whenever(mockSurfaceControl).isValid
            runCurrent()

            verify(mockTaskOrganizer, never()).setExcludeLayersFromTaskSnapshot(any(), any())
        }

    private class TestCaptionView(context: Context) : View(context), TaskFocusStateConsumer {
        override fun setTaskFocusState(focused: Boolean) {}
    }
}
