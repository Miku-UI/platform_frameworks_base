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

package com.android.wm.shell.desktopmode

import android.testing.AndroidTestingRunner
import android.view.WindowManager.TRANSIT_OPEN
import android.window.TransitionInfo
import androidx.test.filters.SmallTest
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.desktopmode.DesktopTestHelpers.createFreeformTask
import com.android.wm.shell.desktopmode.DesktopTestHelpers.createFullscreenTask
import com.android.wm.shell.desktopmode.data.DesktopRepository
import com.android.wm.shell.desktopmode.multidesks.DesksController
import com.android.wm.shell.desktopmode.multidesks.DesksOrganizer
import com.android.wm.shell.pinnedlayer.phone.PinnedLayerController
import com.android.wm.shell.shared.desktopmode.DesktopConfig
import com.android.wm.shell.transition.Transitions
import java.util.Optional
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Tests for {@link FreeformFallbackTransitionObserver}
 *
 * Build/Install/Run: atest WMShellUnitTests:FreeformFallbackTransitionObserverTest
 */
@OptIn(ExperimentalCoroutinesApi::class)
@SmallTest
@RunWith(AndroidTestingRunner::class)
class FreeformFallbackTransitionObserverTest : ShellTestCase() {

    private val transitions = mock<Transitions>()
    private val testScope = TestScope()
    private val pinnedLayerController: PinnedLayerController = mock()
    private val desktopTasksController: DesktopTasksController = mock()
    private val desktopTasksControllerLazy: () -> Optional<DesktopTasksController> = {
        Optional.of(desktopTasksController)
    }
    private val desktopUserRepositories = mock<DesktopUserRepositories>()
    private val desktopRepository = mock<DesktopRepository>()
    private val desksOrganizer = mock<DesksOrganizer>()
    private val desksController = mock<DesksController>()
    private val desktopConfig = mock<DesktopConfig>()
    private val freeformFallbackTransitionHandler = mock<FreeformFallbackTransitionHandler>()

    private lateinit var transitionObserver: FreeformFallbackTransitionObserver

    @Before
    fun setup() {
        whenever(desktopUserRepositories.getProfile(any())).thenReturn(desktopRepository)
        whenever(desktopRepository.getDefaultDeskId(any())).thenReturn(MOCK_DESK_ID)
        whenever(desktopRepository.isDeskActive(any())).thenReturn(true)
        doAnswer { invocation ->
                val runnable = invocation.getArgument<Runnable>(0)
                runnable.run()
                testScope.advanceUntilIdle()
            }
            .whenever(transitions)
            .runOnIdle(any())

        transitionObserver =
            FreeformFallbackTransitionObserver(
                transitions,
                testScope,
                Optional.of(pinnedLayerController),
                desktopTasksControllerLazy,
                desktopUserRepositories,
                desksOrganizer,
                desksController,
                desktopConfig,
                Optional.of(freeformFallbackTransitionHandler),
            )
    }

    @After
    fun tearDown() {
        testScope.cancel()
    }

    @Test
    fun onTransitionReady_validOrphanFreeformTasks_movedToDesk() {
        testScope.runTest {
            // Setup transition info changes with freeform task.
            val info = mock<TransitionInfo>()
            val mockChange = mock<TransitionInfo.Change>()
            val mockTaskInfo = createFreeformTask()
            whenever(mockChange.taskInfo).thenReturn(mockTaskInfo)
            whenever(mockChange.mode).thenReturn(TRANSIT_OPEN)
            whenever(info.changes).thenReturn(listOf(mockChange))

            // Set task as not pinned.
            whenever(pinnedLayerController.isPinned(any())).thenReturn(false)

            transitionObserver.onTransitionReady(info)

            verify(desksOrganizer).moveTaskToDesk(any(), eq(MOCK_DESK_ID), eq(mockTaskInfo), any())
            verify(transitions).startTransition(any(), any(), any())
        }
    }

    @Test
    fun onTransitionReady_pinnedOrphanFreeformTasks_notMovedToDesk() {
        testScope.runTest {
            // Setup transition info changes with freeform task.
            val info = mock<TransitionInfo>()
            val mockChange = mock<TransitionInfo.Change>()
            val mockTaskInfo = createFreeformTask()
            whenever(mockChange.taskInfo).thenReturn(mockTaskInfo)
            whenever(mockChange.mode).thenReturn(TRANSIT_OPEN)
            whenever(info.changes).thenReturn(listOf(mockChange))

            // Set task as pinned.
            whenever(pinnedLayerController.isPinned(any())).thenReturn(true)

            transitionObserver.onTransitionReady(info)

            verify(desksOrganizer, never())
                .moveTaskToDesk(any(), eq(MOCK_DESK_ID), eq(mockTaskInfo), any())
            verify(transitions, never()).startTransition(any(), any(), any())
        }
    }

    @Test
    fun onTransitionReady_noOrphanFreeformTasks_doesNothing() {
        testScope.runTest {
            // Setup transition info changes with freeform task.
            val info = mock<TransitionInfo>()
            val mockChange = mock<TransitionInfo.Change>()
            val mockTaskInfo = createFreeformTask()
            whenever(mockChange.taskInfo).thenReturn(mockTaskInfo)
            whenever(mockChange.mode).thenReturn(TRANSIT_OPEN)
            whenever(info.changes).thenReturn(listOf(mockChange))

            // Mark task as active so it is not an orphan
            whenever(desktopRepository.isActiveTask(any())).thenReturn(true)

            transitionObserver.onTransitionReady(info)

            verify(desksOrganizer, never())
                .moveTaskToDesk(any(), eq(MOCK_DESK_ID), eq(mockTaskInfo), any())
            verify(transitions, never()).startTransition(any(), any(), any())
        }
    }

    @Test
    fun onTransitionReady_noFreeformTasks_doesNothing() {
        testScope.runTest {
            // Setup transition info changes with fullscreen task.
            val info = mock<TransitionInfo>()
            val mockChange = mock<TransitionInfo.Change>()
            val mockTaskInfo = createFullscreenTask()
            whenever(mockChange.taskInfo).thenReturn(mockTaskInfo)
            whenever(mockChange.mode).thenReturn(TRANSIT_OPEN)
            whenever(info.changes).thenReturn(listOf(mockChange))

            transitionObserver.onTransitionReady(info)

            verify(desksOrganizer, never())
                .moveTaskToDesk(any(), eq(MOCK_DESK_ID), eq(mockTaskInfo), any())
            verify(transitions, never()).startTransition(any(), any(), any())
        }
    }

    companion object {
        private const val MOCK_DESK_ID = 0
    }
}
