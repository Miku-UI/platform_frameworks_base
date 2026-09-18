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

import android.app.ActivityManager.RunningTaskInfo
import android.graphics.Rect
import android.view.Display.DEFAULT_DISPLAY
import android.window.DisplayAreaInfo
import android.window.WindowContainerToken
import android.window.WindowContainerTransaction
import android.window.WindowContainerTransaction.HierarchyOp
import com.android.testing.wm.util.MockToken
import com.android.wm.shell.RootTaskDisplayAreaOrganizer
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.common.pip.PipBoundsState
import com.android.wm.shell.common.pip.PipDisplayLayoutState
import com.android.wm.shell.pip2.phone.PipDisplayTransferHandler
import com.android.wm.shell.pip2.phone.PipScheduler
import com.android.wm.shell.pip2.phone.PipTransitionState
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever

/**
 * Test class for {@link PipDisplayDisconnectHandlerTest}
 *
 * Usage: atest WMShellUnitTests:PipDisplayDisconnectHandlerTest
 *
 * TODO: b/488139835 Consider moving this out of desktop package as it becomes less
 *   desktop-specific.
 */
class PipDisplayDisconnectHandlerTest : ShellTestCase() {

    @Mock private lateinit var pipScheduler: PipScheduler
    @Mock private lateinit var pipTransitionState: PipTransitionState
    @Mock private lateinit var pipBoundsState: PipBoundsState
    @Mock private lateinit var desktopState: ShellDesktopState
    @Mock private lateinit var rootTaskDisplayAreaOrganizer: RootTaskDisplayAreaOrganizer
    @Mock private lateinit var pipDisplayTransferHandler: PipDisplayTransferHandler
    @Mock private lateinit var mockMotionBoundsState: PipBoundsState.MotionBoundsState
    @Mock private lateinit var pipDisplayLayoutState: PipDisplayLayoutState
    @Mock private lateinit var pipDisplayReconnectHandler: PipDisplayReconnectHandler

    private val reparentDisplayInfo =
        DisplayAreaInfo(MockToken().token(), REPARENT_DISPLAY_ID, /* featureId= */ 0)

    private lateinit var handler: PipDisplayDisconnectHandler

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        handler =
            PipDisplayDisconnectHandler(
                pipScheduler,
                pipTransitionState,
                pipBoundsState,
                pipDisplayLayoutState,
                desktopState,
                rootTaskDisplayAreaOrganizer,
                pipDisplayTransferHandler,
                pipDisplayReconnectHandler,
            )
        whenever(rootTaskDisplayAreaOrganizer.getDisplayAreaInfo(REPARENT_DISPLAY_ID))
            .thenReturn(reparentDisplayInfo)
    }

    @Test
    fun onDisplayDisconnect_pipTaskIsNull_returnsEmptyWct() {
        whenever(pipTransitionState.pipTaskInfo).thenReturn(null)

        val wct = handler.onDisplayDisconnect(DISCONNECTED_DISPLAY_ID, REPARENT_DISPLAY_ID)

        assertTrue("WCT should be empty when pipTask is null", wct.isEmpty)
    }

    @Test
    fun onDisplayDisconnect_pipTaskOnDifferentDisplay_returnsEmptyWct() {
        createPipTaskInfo(displayId = 99) // Different from DISCONNECTED_DISPLAY_ID
        whenever(pipDisplayLayoutState.displayId).thenReturn(99)

        val wct = handler.onDisplayDisconnect(DISCONNECTED_DISPLAY_ID, REPARENT_DISPLAY_ID)

        assertTrue("WCT should be empty when pipTask is on a different display", wct.isEmpty)
    }

    @Test
    fun onDisplayDisconnect_desktopModeSupported_reparentsAndMovesPip() {
        val pipTask = createPipTaskInfo(displayId = DISCONNECTED_DISPLAY_ID)
        whenever(pipDisplayLayoutState.displayId).thenReturn(DISCONNECTED_DISPLAY_ID)
        val currentBounds = Rect(0, 0, 100, 100)

        whenever(desktopState.isDesktopModeSupportedOnDisplay(DEFAULT_DISPLAY)).thenReturn(true)
        whenever(pipBoundsState.bounds).thenReturn(currentBounds)
        whenever(pipBoundsState.motionBoundsState).thenReturn(mockMotionBoundsState)
        whenever(mockMotionBoundsState.isInMotion).thenReturn(false)

        val wct = handler.onDisplayDisconnect(DISCONNECTED_DISPLAY_ID, REPARENT_DISPLAY_ID)

        // Verify pipTask was reparented to top.
        assertWctHasReparent(wct, pipTask.token, reparentDisplayInfo.token, toTop = true)
        // Verify scheduleMovePipToDisplay was called
        verify(pipDisplayTransferHandler)
            .scheduleMovePipToDisplay(DISCONNECTED_DISPLAY_ID, REPARENT_DISPLAY_ID, currentBounds)
        // Ensure no removal was scheduled
        verify(pipScheduler, never()).scheduleRemovePip(anyBoolean())
    }

    @Test
    fun onDisplayDisconnect_noDesktopModeSupport_reparentsAndRemovesPip() {
        val pipTask = createPipTaskInfo(displayId = DISCONNECTED_DISPLAY_ID)
        whenever(pipDisplayLayoutState.displayId).thenReturn(DISCONNECTED_DISPLAY_ID)
        whenever(desktopState.isDesktopModeSupportedOnDisplay(DEFAULT_DISPLAY)).thenReturn(false)

        val wct = handler.onDisplayDisconnect(DISCONNECTED_DISPLAY_ID, REPARENT_DISPLAY_ID)

        // Verify the pip task was reparented to the bottom
        assertWctHasReparent(wct, pipTask.token, reparentDisplayInfo.token, toTop = false)
        // Verify scheduleRemovePip was called
        verify(pipScheduler).scheduleRemovePip(false)
        // Ensure no move was scheduled
        verify(pipDisplayTransferHandler, never())
            .scheduleMovePipToDisplay(anyInt(), anyInt(), any())
    }

    private fun createPipTaskInfo(displayId: Int): RunningTaskInfo {
        val pipTask =
            RunningTaskInfo().apply {
                this.displayId = displayId
                this.token = mock(WindowContainerToken::class.java)
            }
        whenever(pipTransitionState.pipTaskInfo).thenReturn(pipTask)
        return pipTask
    }

    private fun assertWctHasReparent(
        wct: WindowContainerTransaction,
        taskToken: WindowContainerToken,
        parentToken: WindowContainerToken,
        toTop: Boolean,
    ) {
        val hasReparent =
            wct.hierarchyOps.any { op ->
                op.type == HierarchyOp.HIERARCHY_OP_TYPE_REPARENT &&
                    op.container == taskToken.asBinder() &&
                    op.newParent == parentToken.asBinder() &&
                    op.toTop == toTop
            }
        assertTrue("WCT missing expected reparent operation", hasReparent)
    }

    companion object {
        private val DISCONNECTED_DISPLAY_ID = 10
        private val REPARENT_DISPLAY_ID = DEFAULT_DISPLAY
    }
}
