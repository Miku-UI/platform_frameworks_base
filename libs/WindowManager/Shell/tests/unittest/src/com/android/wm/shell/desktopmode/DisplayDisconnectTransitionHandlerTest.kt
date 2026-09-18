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
package com.android.wm.shell.desktopmode

import android.graphics.Rect
import android.os.Binder
import android.platform.test.annotations.EnableFlags
import android.view.Display.DEFAULT_DISPLAY
import android.view.Display.INVALID_DISPLAY
import android.view.SurfaceControl
import android.view.WindowManager.TRANSIT_CHANGE
import android.window.TransitionInfo
import android.window.TransitionRequestInfo
import android.window.WindowContainerToken
import android.window.WindowContainerTransaction
import androidx.test.filters.SmallTest
import com.android.window.flags.Flags
import com.android.window.flags.Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE
import com.android.window.flags.Flags.FLAG_ENABLE_DISPLAY_DISCONNECT_INTERACTION
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.fullscreen.FullscreenDisconnectHandler
import com.android.wm.shell.pinnedlayer.phone.PinnedLayerController
import com.android.wm.shell.splitscreen.SplitMultiDisplayProvider
import com.android.wm.shell.splitscreen.SplitScreenController
import com.android.wm.shell.sysui.ShellInit
import com.android.wm.shell.transition.Transitions
import java.util.Optional
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.Before
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.never
import org.mockito.kotlin.spy
import org.mockito.kotlin.stub
import org.mockito.kotlin.whenever

/**
 * Test class for {@link DesktopTasksController}
 *
 * Usage: atest WMShellUnitTests:DisplayDisconnectTransitionHandlerTest
 */
@SmallTest
@EnableFlags(FLAG_ENABLE_DESKTOP_WINDOWING_MODE, FLAG_ENABLE_DISPLAY_DISCONNECT_INTERACTION)
class DisplayDisconnectTransitionHandlerTest() : ShellTestCase() {
    private val transitions = mock(Transitions::class.java)
    private val shellInit = mock(ShellInit::class.java)
    private val splitScreenController = mock(SplitScreenController::class.java)
    private val desktopTasksController = mock(DesktopTasksController::class.java)
    private val fullscreenDisconnectHandler = mock(FullscreenDisconnectHandler::class.java)
    private val pinnedLayerController = mock(PinnedLayerController::class.java)
    private val pipDisconnectHandler = mock(PipDisplayDisconnectHandler::class.java)

    private lateinit var disconnectTransitionHandler: DisplayDisconnectTransitionHandler
    private lateinit var splitScreenControllerOptional: Optional<SplitScreenController>
    private lateinit var desktopTasksControllerOptional: Optional<DesktopTasksController>
    private lateinit var fullscreenDisconnectHandlerOptional: Optional<FullscreenDisconnectHandler>
    private lateinit var pinnedLayerControllerOptional: Optional<PinnedLayerController>
    private lateinit var pipDisconnectHandlerOptional: Optional<PipDisplayDisconnectHandler>
    private val transition = Binder()

    @Before
    fun setUp() {
        splitScreenControllerOptional = spy(Optional.of(splitScreenController))
        desktopTasksControllerOptional = spy(Optional.of(desktopTasksController))
        fullscreenDisconnectHandlerOptional = spy(Optional.of(fullscreenDisconnectHandler))
        pinnedLayerControllerOptional = spy(Optional.of(pinnedLayerController))
        pipDisconnectHandlerOptional = spy(Optional.of(pipDisconnectHandler))
        disconnectTransitionHandler =
            DisplayDisconnectTransitionHandler(
                transitions,
                shellInit,
                splitScreenControllerOptional,
                desktopTasksControllerOptional,
                fullscreenDisconnectHandlerOptional,
                pinnedLayerControllerOptional,
                pipDisconnectHandlerOptional,
            )
        whenever(splitScreenController.multiDisplayProvider)
            .thenReturn(FakeSplitMultiDisplayProvider())
    }

    @Test
    @EnableFlags(
        Flags.FLAG_ENABLE_DISPLAY_DISCONNECT_INTERACTION,
    )
    fun handleRequest_noReparentDisplay_doesNotPerformDisconnect() {
        val displayChange = TransitionRequestInfo.DisplayChange(SECOND_DISPLAY)
        displayChange.disconnectReparentDisplay = INVALID_DISPLAY
        val transitionRequestInfo =
            TransitionRequestInfo(
                    TRANSIT_CHANGE,
                    /* triggerTask = */ null,
                    /* remoteTransition= */ null,
                )
                .apply { setDisplayChanges(listOf(displayChange)) }

        val transition = Binder()
        whenever(splitScreenControllerOptional.isPresent).thenReturn(false)
        whenever(desktopTasksControllerOptional.isPresent).thenReturn(true)
        whenever(fullscreenDisconnectHandlerOptional.isPresent).thenReturn(true)
        val finalWct =
            WindowContainerTransaction().apply {
                setBounds(mock(WindowContainerToken::class.java), Rect())
            }
        whenever(desktopTasksController.onDisplayDisconnect(anyInt(), anyInt(), any()))
            .thenReturn(finalWct)
        whenever(fullscreenDisconnectHandler.onDisplayDisconnect(anyInt(), anyInt()))
            .thenReturn(WindowContainerTransaction())

        disconnectTransitionHandler.handleRequest(transition, transitionRequestInfo)

        verify(desktopTasksController, never())
            .onDisplayDisconnect(SECOND_DISPLAY, DEFAULT_DISPLAY, transition)
    }

    @Test
    @EnableFlags(
        Flags.FLAG_ENABLE_DISPLAY_DISCONNECT_INTERACTION,
    )
    fun handleRequest_validReparentDisplay_performsDisconnect() {
        val displayChange = TransitionRequestInfo.DisplayChange(SECOND_DISPLAY)
        displayChange.disconnectReparentDisplay = DEFAULT_DISPLAY
        val transitionRequestInfo =
            TransitionRequestInfo(
                    TRANSIT_CHANGE,
                    /* triggerTask = */ null,
                    /* remoteTransition= */ null,
                )
                .apply { setDisplayChanges(listOf(displayChange)) }
        val transition = Binder()
        whenever(splitScreenControllerOptional.isPresent).thenReturn(false)
        whenever(desktopTasksControllerOptional.isPresent).thenReturn(true)
        whenever(fullscreenDisconnectHandlerOptional.isPresent).thenReturn(true)
        whenever(pipDisconnectHandlerOptional.isPresent).thenReturn(true)
        val finalWct =
            WindowContainerTransaction().apply {
                setBounds(mock(WindowContainerToken::class.java), Rect())
            }
        whenever(desktopTasksController.onDisplayDisconnect(anyInt(), anyInt(), any()))
            .thenReturn(finalWct)
        whenever(fullscreenDisconnectHandler.onDisplayDisconnect(anyInt(), anyInt()))
            .thenReturn(WindowContainerTransaction())
        whenever(pipDisconnectHandler.onDisplayDisconnect(anyInt(), anyInt()))
            .thenReturn(WindowContainerTransaction())

        disconnectTransitionHandler.handleRequest(transition, transitionRequestInfo)

        // Verify that the disconnect is forwarded to the controller.
        verify(desktopTasksController)
            .onDisplayDisconnect(SECOND_DISPLAY, DEFAULT_DISPLAY, transition)

        // Verify that the transition is added to the pending list, so it can be animated.
        val startT = mock(SurfaceControl.Transaction::class.java)
        val finishT = mock(SurfaceControl.Transaction::class.java)
        val finishCallback = mock(Transitions.TransitionFinishCallback::class.java)
        val info = mock(TransitionInfo::class.java)
        assertTrue(
            disconnectTransitionHandler.startAnimation(
                transition,
                info,
                startT,
                finishT,
                finishCallback,
            ),
            "Disconnect transition should be handled",
        )
    }

    @Test
    fun handleRequest_disconnectWithoutDesktopController_addsPendingTransition() {
        // Re-initialize handler without the optional controller for this test.
        disconnectTransitionHandler =
            DisplayDisconnectTransitionHandler(
                transitions,
                shellInit,
                Optional.empty(), // No SplitScreenController
                Optional.empty(), // No DesktopTasksController
                Optional.empty(), // No FullscreenDisconnectHandler
                Optional.empty(), // No PinnedLayerController
                Optional.empty(), // No PipDisplayDisconnectHandler
            )

        val displayChange = TransitionRequestInfo.DisplayChange(SECOND_DISPLAY)
        displayChange.disconnectReparentDisplay = DEFAULT_DISPLAY
        val transitionRequestInfo =
            TransitionRequestInfo(
                    TRANSIT_CHANGE,
                    /* triggerTask = */ null,
                    /* remoteTransition= */ null,
                )
                .apply { setDisplayChanges(listOf(displayChange)) }
        val transition = Binder()

        // Handle the request.
        val wct = disconnectTransitionHandler.handleRequest(transition, transitionRequestInfo)

        // Verify that no WCT is returned as there is no controller to generate one.
        assertTrue(wct == null)

        // Verify that the transition is added to the pending list, so it can be animated.
        val startT = mock(SurfaceControl.Transaction::class.java)
        val finishT = mock(SurfaceControl.Transaction::class.java)
        val finishCallback = mock(Transitions.TransitionFinishCallback::class.java)
        val info = mock(TransitionInfo::class.java)
        assertTrue(
            disconnectTransitionHandler.startAnimation(
                transition,
                info,
                startT,
                finishT,
                finishCallback,
            ),
            "Disconnect transition should be handled even without DesktopTasksController",
        )
    }

    @Test
    fun handleRequest_notADisconnect_isNotHandled() {
        val transitionRequestInfo =
            TransitionRequestInfo(
                TRANSIT_CHANGE,
                /* triggerTask = */ null,
                /* remoteTransition= */ null,
            )
        // No DisplayChange is set, so this is not a disconnect transition.
        val transition = Binder()

        // Handle the request.
        disconnectTransitionHandler.handleRequest(transition, transitionRequestInfo)

        // Verify that the transition is NOT added to the pending list.
        val startT = mock(SurfaceControl.Transaction::class.java)
        val finishT = mock(SurfaceControl.Transaction::class.java)
        val finishCallback = mock(Transitions.TransitionFinishCallback::class.java)
        val info = mock(TransitionInfo::class.java)
        assertFalse(
            disconnectTransitionHandler.startAnimation(
                transition,
                info,
                startT,
                finishT,
                finishCallback,
            ),
            "Non-disconnect transition should not be handled",
        )
    }

    @Test
    @EnableFlags(FLAG_ENABLE_DISPLAY_DISCONNECT_INTERACTION)
    fun transitionHandler_NotHandle_doesFallback() {
        val displayChange = TransitionRequestInfo.DisplayChange(SECOND_DISPLAY)
        displayChange.disconnectReparentDisplay = DEFAULT_DISPLAY
        val transitionRequestInfo =
            TransitionRequestInfo(
                    TRANSIT_CHANGE,
                    /* triggerTask = */ null,
                    /* remoteTransition= */ null,
                )
                .apply { setDisplayChanges(listOf(displayChange)) }

        val spyHandler = spy(disconnectTransitionHandler)
        whenever(splitScreenControllerOptional.isPresent).thenReturn(false)
        whenever(desktopTasksControllerOptional.isPresent).thenReturn(false)
        whenever(fullscreenDisconnectHandlerOptional.isPresent).thenReturn(false)
        whenever(pipDisconnectHandlerOptional.isPresent).thenReturn(false)
        spyHandler.handleRequest(transition = transition, request = transitionRequestInfo)
        verify(spyHandler).addPendingTransition((transition))
    }

    @Test
    @EnableFlags(FLAG_ENABLE_DISPLAY_DISCONNECT_INTERACTION)
    fun transitionHandler_handledbyDesktopTaskController_returnValidWCT() {
        val displayChange = TransitionRequestInfo.DisplayChange(SECOND_DISPLAY)
        displayChange.disconnectReparentDisplay = DEFAULT_DISPLAY
        val transitionRequestInfo =
            TransitionRequestInfo(
                    TRANSIT_CHANGE,
                    /* triggerTask = */ null,
                    /* remoteTransition= */ null,
                )
                .apply { setDisplayChanges(listOf(displayChange)) }
        val spyHandler = spy(disconnectTransitionHandler)
        whenever(splitScreenControllerOptional.isPresent).thenReturn(false)
        whenever(desktopTasksControllerOptional.isPresent).thenReturn(true)
        whenever(fullscreenDisconnectHandlerOptional.isPresent).thenReturn(false)
        whenever(pipDisconnectHandlerOptional.isPresent).thenReturn(false)
        val desktopWct = WindowContainerTransaction()
        desktopWct.reparent(
            mock(WindowContainerToken::class.java),
            mock(WindowContainerToken::class.java),
            true,
        )
        whenever(desktopTasksController.onDisplayDisconnect(anyInt(), anyInt(), any()))
            .thenReturn(desktopWct)
        val wct = spyHandler.handleRequest(transition = transition, request = transitionRequestInfo)
        assertNotNull(wct, "wct should not be null if handled by desktop")
    }

    @Test
    @EnableFlags(FLAG_ENABLE_DISPLAY_DISCONNECT_INTERACTION)
    fun transitionHandler_handledbySplitScreenController_returnValidWCT() {
        val displayChange = TransitionRequestInfo.DisplayChange(SECOND_DISPLAY)
        displayChange.disconnectReparentDisplay = DEFAULT_DISPLAY
        val transitionRequestInfo =
            TransitionRequestInfo(
                    TRANSIT_CHANGE,
                    /* triggerTask = */ null,
                    /* remoteTransition= */ null,
                )
                .apply { setDisplayChanges(listOf(displayChange)) }
        val spyHandler = spy(disconnectTransitionHandler)
        whenever(splitScreenControllerOptional.isPresent).thenReturn(true)
        whenever(desktopTasksControllerOptional.isPresent).thenReturn(false)
        whenever(fullscreenDisconnectHandlerOptional.isPresent).thenReturn(false)
        whenever(pinnedLayerControllerOptional.isPresent).thenReturn(false)
        whenever(pipDisconnectHandlerOptional.isPresent).thenReturn(false)
        pinnedLayerControllerOptional.stub { on { isPresent } doReturn false }
        val wct = spyHandler.handleRequest(transition = transition, request = transitionRequestInfo)
        assertNotNull(wct, "wct should not be null if handled by splitscreen")
    }

    @Test
    @EnableFlags(FLAG_ENABLE_DISPLAY_DISCONNECT_INTERACTION)
    fun transitionHandler_handledByPinnedLayerController_returnValidWCT() {
        val displayChange = TransitionRequestInfo.DisplayChange(SECOND_DISPLAY)
        displayChange.disconnectReparentDisplay = DEFAULT_DISPLAY
        val transitionRequestInfo =
            TransitionRequestInfo(
                    TRANSIT_CHANGE,
                    /* triggerTask = */ null,
                    /* remoteTransition= */ null,
                )
                .apply { setDisplayChanges(listOf(displayChange)) }
        val spyHandler = spy(disconnectTransitionHandler)
        splitScreenControllerOptional.stub { on { isPresent } doReturn false }
        desktopTasksControllerOptional.stub { on { isPresent } doReturn false }
        fullscreenDisconnectHandlerOptional.stub { on { isPresent } doReturn false }
        pinnedLayerControllerOptional.stub { on { isPresent } doReturn true }
        pinnedLayerController.stub {
            on { getDisplayDisconnectChanges(transition, SECOND_DISPLAY, DEFAULT_DISPLAY) } doReturn
                WindowContainerTransaction()
        }
        pipDisconnectHandlerOptional.stub { on { isPresent } doReturn false }

        val wct = spyHandler.handleRequest(transition = transition, request = transitionRequestInfo)
        assertNotNull(wct, "wct should not be null if handled by pinned layer")
    }

    private class FakeSplitMultiDisplayProvider : SplitMultiDisplayProvider {
        override fun getDisplayRootForDisplayId(displayId: Int): WindowContainerToken? {
            return mock(WindowContainerToken::class.java)
        }

        override fun prepareMovingSplitScreenRoot(
            wct: WindowContainerTransaction?,
            displayId: Int,
            onTop: Boolean,
        ) {}

        override fun addMoveSplitPairToDisplayChanges(
            oldDisplayId: Int,
            destinationDisplayId: Int,
            wct: WindowContainerTransaction,
            toTop: Boolean,
        ) {
            wct.setBounds(mock(WindowContainerToken::class.java), Rect())
        }
    }

    companion object {
        const val SECOND_DISPLAY = 2
    }
}
