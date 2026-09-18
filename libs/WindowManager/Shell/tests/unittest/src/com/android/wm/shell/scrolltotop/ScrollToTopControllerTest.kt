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

package com.android.wm.shell.scrolltotop

import android.view.IWindowManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.TestShellExecutor
import com.android.wm.shell.splitscreen.SplitScreenController
import java.util.Optional
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

@SmallTest
@RunWith(AndroidJUnit4::class)
class ScrollToTopControllerTest : ShellTestCase() {

    @Mock private lateinit var windowManager: IWindowManager
    @Mock private lateinit var splitScreenController: SplitScreenController

    private lateinit var mainExecutor: TestShellExecutor
    private lateinit var controller: ScrollToTopController

    @Before
    fun setUp() {
        mainExecutor = TestShellExecutor()
    }

    @Test
    fun onScrollToTop_noSplitScreen_dispatchesToWindowManager() {
        controller = ScrollToTopController(mainExecutor, windowManager, Optional.empty())

        controller.asScrollToTop().onScrollToTop(1, 100)
        mainExecutor.flushAll()

        verify(windowManager).dispatchScrollToTop(1, 100, -1)
    }

    @Test
    fun onScrollToTop_splitScreenPresent_noTaskFound_dispatchesToWindowManager() {
        controller =
            ScrollToTopController(mainExecutor, windowManager, Optional.of(splitScreenController))
        `when`(splitScreenController.isSplitScreenFocused).thenReturn(true)
        `when`(splitScreenController.getTaskInSplitAt(100, 1)).thenReturn(-1)

        controller.asScrollToTop().onScrollToTop(1, 100)
        mainExecutor.flushAll()

        verify(windowManager).dispatchScrollToTop(1, 100, -1)
    }

    @Test
    fun onScrollToTop_splitScreenPresent_taskFound_dispatchesToWindowManager() {
        controller =
            ScrollToTopController(mainExecutor, windowManager, Optional.of(splitScreenController))
        `when`(splitScreenController.isSplitScreenFocused).thenReturn(true)
        `when`(splitScreenController.getTaskInSplitAt(100, 1)).thenReturn(123)

        controller.asScrollToTop().onScrollToTop(1, 100)
        mainExecutor.flushAll()

        verify(windowManager).dispatchScrollToTop(1, 100, 123)
    }

    @Test
    fun onScrollToTop_splitScreenPresent_notFocused_dispatchesToWindowManager() {
        controller =
            ScrollToTopController(mainExecutor, windowManager, Optional.of(splitScreenController))
        `when`(splitScreenController.isSplitScreenFocused).thenReturn(false)
        `when`(splitScreenController.getTaskInSplitAt(100, 1)).thenReturn(123)

        controller.asScrollToTop().onScrollToTop(1, 100)
        mainExecutor.flushAll()

        verify(windowManager).dispatchScrollToTop(1, 100, -1)
    }

    @Test
    fun onScrollToTop_splitScreenPresent_wrongDisplay_dispatchesToWindowManager() {
        controller =
            ScrollToTopController(mainExecutor, windowManager, Optional.of(splitScreenController))
        `when`(splitScreenController.isSplitScreenFocused).thenReturn(true)
        `when`(splitScreenController.getTaskInSplitAt(100, 1)).thenReturn(-1)

        controller.asScrollToTop().onScrollToTop(1, 100)
        mainExecutor.flushAll()

        verify(windowManager).dispatchScrollToTop(1, 100, -1)
    }
}
