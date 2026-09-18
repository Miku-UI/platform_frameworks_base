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
package com.android.wm.shell.common.pip

import android.app.ActivityManager
import android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM
import android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN
import android.app.WindowConfiguration.WINDOWING_MODE_UNDEFINED
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.testing.AndroidTestingRunner
import android.testing.TestableLooper.RunWithLooper
import android.view.Display.DEFAULT_DISPLAY
import android.window.DisplayAreaInfo
import android.window.WindowContainerToken
import androidx.test.filters.SmallTest
import com.android.window.flags.Flags.FLAG_ENABLE_DESKTOP_WINDOWING_FREE_FLOATING_PIP
import com.android.wm.shell.RootTaskDisplayAreaOrganizer
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.desktopmode.DesktopUserRepositories
import com.android.wm.shell.desktopmode.DragToDesktopTransitionHandler
import com.android.wm.shell.desktopmode.data.DesktopRepository
import com.android.wm.shell.desktopmode.desktopfirst.DESKTOP_FIRST_DISPLAY_WINDOWING_MODE
import com.android.wm.shell.desktopmode.desktopfirst.TOUCH_FIRST_DISPLAY_WINDOWING_MODE
import com.android.wm.shell.recents.RecentsTransitionHandler
import com.android.wm.shell.recents.RecentsTransitionStateListener
import com.android.wm.shell.recents.RecentsTransitionStateListener.TRANSITION_STATE_ANIMATING
import com.android.wm.shell.recents.RecentsTransitionStateListener.TRANSITION_STATE_NOT_RUNNING
import com.google.common.truth.Truth.assertThat
import java.util.Optional
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/** Unit test against [PipDesktopState]. */
@SmallTest
@RunWithLooper
@RunWith(AndroidTestingRunner::class)
class PipDesktopStateTest : ShellTestCase() {
    private val mockPipDisplayLayoutState = mock<PipDisplayLayoutState>()
    private val mockRecentsTransitionHandler = mock<RecentsTransitionHandler>()
    private val mockDesktopUserRepositories = mock<DesktopUserRepositories>()
    private val mockDesktopRepository = mock<DesktopRepository>()
    private val mockDragToDesktopTransitionHandler = mock<DragToDesktopTransitionHandler>()
    private val mockRootTaskDisplayAreaOrganizer = mock<RootTaskDisplayAreaOrganizer>()
    private val mockTaskInfo = mock<ActivityManager.RunningTaskInfo>()
    private lateinit var defaultTda: DisplayAreaInfo
    private lateinit var recentsTransitionStateListener: RecentsTransitionStateListener
    private lateinit var pipDesktopState: PipDesktopState

    @Before
    fun setUp() {
        whenever(mockDesktopUserRepositories.current).thenReturn(mockDesktopRepository)
        whenever(mockTaskInfo.getDisplayId()).thenReturn(DISPLAY_ID)
        whenever(mockPipDisplayLayoutState.displayId).thenReturn(DISPLAY_ID)

        defaultTda = DisplayAreaInfo(mock<WindowContainerToken>(), DISPLAY_ID, /* featureId= */ 0)
        defaultTda.configuration.windowConfiguration.windowingMode =
            TOUCH_FIRST_DISPLAY_WINDOWING_MODE
        whenever(mockRootTaskDisplayAreaOrganizer.getDisplayAreaInfo(DISPLAY_ID))
            .thenReturn(defaultTda)

        pipDesktopState =
            PipDesktopState(
                mockPipDisplayLayoutState,
                mockRecentsTransitionHandler,
                Optional.of(mockDesktopUserRepositories),
                Optional.of(mockDragToDesktopTransitionHandler),
                mockRootTaskDisplayAreaOrganizer,
            )

        val captor = argumentCaptor<RecentsTransitionStateListener>()
        verify(mockRecentsTransitionHandler).addTransitionStateListener(captor.capture())
        recentsTransitionStateListener = captor.firstValue
        recentsTransitionStateListener.onTransitionStateChanged(
            TRANSITION_STATE_NOT_RUNNING,
            DEFAULT_DISPLAY,
        )

        whenever(mockDesktopRepository.isAnyDeskActive(DISPLAY_ID)).thenReturn(true)
    }

    @Test
    fun isDesktopWindowingPipEnabled_returnsTrue() {
        assertThat(pipDesktopState.isDesktopWindowingPipEnabled()).isTrue()
    }

    @Test
    fun desktopWindowingPipDisabled_missingDesktopUserRepositories_returnsFalse() {
        // Create a new instance with the dependency missing.
        val pipDesktopStateNoRepos =
            PipDesktopState(
                mockPipDisplayLayoutState,
                mockRecentsTransitionHandler,
                Optional.empty(), // Missing dependency
                Optional.of(mockDragToDesktopTransitionHandler),
                mockRootTaskDisplayAreaOrganizer,
            )

        // Verify that the feature and dependent features are disabled.
        assertThat(pipDesktopStateNoRepos.isDesktopWindowingPipEnabled()).isFalse()
        assertThat(pipDesktopStateNoRepos.isPipInDesktopMode()).isFalse()
        assertThat(pipDesktopStateNoRepos.isDragToDesktopInProgress()).isFalse()
    }

    @Test
    fun desktopWindowingPipDisabled_missingDragToDesktopHandler_returnsFalse() {
        // Create a new instance with the dependency missing.
        val pipDesktopStateNoHandler =
            PipDesktopState(
                mockPipDisplayLayoutState,
                mockRecentsTransitionHandler,
                Optional.of(mockDesktopUserRepositories),
                Optional.empty(), // Missing dependency
                mockRootTaskDisplayAreaOrganizer,
            )

        // Verify that the feature and dependent features are disabled.
        assertThat(pipDesktopStateNoHandler.isDesktopWindowingPipEnabled()).isFalse()
        assertThat(pipDesktopStateNoHandler.isPipInDesktopMode()).isFalse()
        assertThat(pipDesktopStateNoHandler.isDragToDesktopInProgress()).isFalse()
    }

    @Test
    fun isDraggingPipAcrossDisplaysEnabled_returnsTrue() {
        assertThat(pipDesktopState.isDraggingPipAcrossDisplaysEnabled()).isTrue()
    }

    @Test
    fun isPipInDesktopMode_anyDeskActive_returnsTrue() {
        assertThat(pipDesktopState.isPipInDesktopMode()).isTrue()

        defaultTda.configuration.windowConfiguration.windowingMode =
            DESKTOP_FIRST_DISPLAY_WINDOWING_MODE
        assertThat(pipDesktopState.isPipInDesktopMode()).isTrue()
    }

    @Test
    fun isPipInDesktopMode_noDeskActive_touchFirstDisplay_returnsFalse() {
        whenever(mockDesktopRepository.isAnyDeskActive(DISPLAY_ID)).thenReturn(false)

        assertThat(pipDesktopState.isPipInDesktopMode()).isFalse()
    }

    @EnableFlags(FLAG_ENABLE_DESKTOP_WINDOWING_FREE_FLOATING_PIP)
    @Test
    fun isFreeFloatingPipEnabled_touchFirstDisplay_returnsFalse() {
        assertThat(pipDesktopState.isFreeFloatingPipEnabled()).isFalse()
    }

    @EnableFlags(FLAG_ENABLE_DESKTOP_WINDOWING_FREE_FLOATING_PIP)
    @Test
    fun isFreeFloatingPipEnabled_desktopFirstDisplay_returnsTrue() {
        defaultTda.configuration.windowConfiguration.windowingMode =
            DESKTOP_FIRST_DISPLAY_WINDOWING_MODE

        assertThat(pipDesktopState.isFreeFloatingPipEnabled()).isTrue()
    }

    @Test
    fun outPipWindowingMode_exitToDesktop_displayFreeform_returnsUndefined() {
        setDisplayWindowingMode(WINDOWING_MODE_FREEFORM)

        assertThat(pipDesktopState.getOutPipWindowingMode()).isEqualTo(WINDOWING_MODE_UNDEFINED)
    }

    @Test
    fun outPipWindowingMode_exitToDesktop_multiDesktopsEnabled_returnsUndefined() {
        whenever(mockDesktopRepository.isAnyDeskActive(DISPLAY_ID)).thenReturn(true)

        assertThat(pipDesktopState.getOutPipWindowingMode()).isEqualTo(WINDOWING_MODE_UNDEFINED)
    }

    @Test
    fun outPipWindowingMode_midRecents_inDesktop_returnsFullscreen() {
        recentsTransitionStateListener.onTransitionStateChanged(
            TRANSITION_STATE_ANIMATING,
            DEFAULT_DISPLAY,
        )

        assertThat(pipDesktopState.getOutPipWindowingMode()).isEqualTo(WINDOWING_MODE_FULLSCREEN)
    }

    @Test
    fun outPipWindowingMode_multiActivityChild_inDesktop_returnsUndefined() {
        // When in desktop mode, a multi-activity child PiP should expand to an undefined
        // windowing mode, so it can be resolved by the system (to freeform).
        whenever(mockDesktopRepository.isAnyDeskActive(DISPLAY_ID)).thenReturn(true)

        val windowingMode = pipDesktopState.getOutPipWindowingMode(isMultiActivityChild = true)

        assertThat(windowingMode).isEqualTo(WINDOWING_MODE_UNDEFINED)
    }

    @Test
    fun outPipWindowingMode_multiActivityChild_notInDesktop_returnsFullscreen() {
        // When not in desktop mode, a multi-activity child PiP should expand to fullscreen.
        whenever(mockDesktopRepository.isAnyDeskActive(DISPLAY_ID)).thenReturn(false)

        val windowingMode = pipDesktopState.getOutPipWindowingMode(isMultiActivityChild = true)

        assertThat(windowingMode).isEqualTo(WINDOWING_MODE_FULLSCREEN)
    }

    @Test
    fun isDragToDesktopInProgress_inProgress_returnsTrue() {
        whenever(mockDragToDesktopTransitionHandler.inProgress).thenReturn(true)

        assertThat(pipDesktopState.isDragToDesktopInProgress()).isTrue()
    }

    @Test
    fun isDragToDesktopInProgress_notInProgress_returnsFalse() {
        whenever(mockDragToDesktopTransitionHandler.inProgress).thenReturn(false)

        assertThat(pipDesktopState.isDragToDesktopInProgress()).isFalse()
    }

    private fun setDisplayWindowingMode(windowingMode: Int) {
        defaultTda.configuration.windowConfiguration.windowingMode = windowingMode
    }

    companion object {
        private const val DISPLAY_ID = 1
    }
}
