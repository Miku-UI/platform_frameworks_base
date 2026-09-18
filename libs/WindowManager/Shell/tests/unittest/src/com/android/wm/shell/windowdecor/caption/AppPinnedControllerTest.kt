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

import android.app.ActivityManager
import android.graphics.Rect
import android.testing.AndroidTestingRunner
import android.view.View
import androidx.test.filters.SmallTest
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.common.DisplayLayout
import com.android.wm.shell.windowdecor.WindowDecorationActions
import com.android.wm.shell.windowdecor.common.WindowDecorTaskResourceLoader
import com.android.wm.shell.windowdecor.common.viewhost.WindowDecorViewHost
import com.android.wm.shell.windowdecor.common.viewhost.WindowDecorViewHostSupplier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.CoroutineScope
import org.junit.Before
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Tests for [AppPinnedController]
 *
 * atest WMShellUnitTests:AppPinnedControllerTest
 */
@SmallTest
@RunWith(AndroidTestingRunner::class)
class AppPinnedControllerTest : ShellTestCase() {

    private val taskInfo = ActivityManager.RunningTaskInfo()
    private val mockDecorViewHostSupplier = mock<WindowDecorViewHostSupplier<WindowDecorViewHost>>()
    private val mockDisplayController = mock<DisplayController>()
    private val mockDisplayLayout = mock<DisplayLayout>()
    private val mockTouchListener = mock<View.OnTouchListener>()
    private val mockMotionListener = mock<View.OnGenericMotionListener>()
    private val mockDecorationActions = mock<WindowDecorationActions>()
    private val mockTaskResourceLoader = mock<WindowDecorTaskResourceLoader>()
    private val mockTaskOrganizer = mock<ShellTaskOrganizer>()
    private val mockBgScope = mock<CoroutineScope>()

    private fun createController() =
        AppPinnedController(
            taskInfo,
            mockDecorViewHostSupplier,
            mContext,
            mockDisplayController,
            mockTouchListener,
            mockMotionListener,
            mockDecorationActions,
            mockTaskResourceLoader,
            mockTaskOrganizer,
            mockBgScope,
        )

    @Before
    fun setup() {
        taskInfo.configuration.windowConfiguration.setBounds(Rect(0, 0, 100, 100))

        whenever(mockDisplayController.getDisplayLayout(any())).thenReturn(mockDisplayLayout)

        whenever(mockDisplayLayout.width()).thenReturn(2000)
        whenever(mockDisplayLayout.getStableBounds(any())).thenAnswer {
            val rect = it.getArgument<Rect>(0)
            rect.set(0, 0, 2000, 1000)
        }
    }

    @Test
    fun controllerBindsData() {
        val controller = createController()
        val validDragArea = controller.calculateValidDragArea()
        assertEquals(Rect(0, 0, 1900, 900), validDragArea)
    }
}
