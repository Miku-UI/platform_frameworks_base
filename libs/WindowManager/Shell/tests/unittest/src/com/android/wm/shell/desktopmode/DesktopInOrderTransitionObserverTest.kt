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

import android.os.IBinder
import android.testing.AndroidTestingRunner
import android.view.SurfaceControl
import android.view.WindowManager.TRANSIT_CHANGE
import android.view.WindowManager.TRANSIT_CLOSE
import androidx.test.filters.SmallTest
import com.android.testing.wm.util.StubTransaction
import com.android.testing.wm.util.TransitionInfoBuilder
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.desktopmode.multidesks.DesksTransitionObserver
import com.android.wm.shell.transition.FocusTransitionObserver
import java.util.Optional
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

/**
 * Tests for {@link DesktopInOrderTransitionObserver}
 *
 * Build/Install/Run: atest WMShellUnitTests:DesktopInOrderTransitionObserverTest
 */
@SmallTest
@RunWith(AndroidTestingRunner::class)
class DesktopInOrderTransitionObserverTest : ShellTestCase() {
    private val desktopImmersiveController = mock<DesktopImmersiveController>()
    private val focusTransitionObserver = mock<FocusTransitionObserver>()
    private val desksTransitionObserver = mock<DesksTransitionObserver>()
    private val desktopImeHandler = mock<DesktopImeHandler>()
    private val desktopBackNavTransitionObserver = mock<DesktopBackNavTransitionObserver>()
    private val desktopModeLoggerTransitionObserver = mock<DesktopModeLoggerTransitionObserver>()
    private lateinit var transitionObserver: DesktopInOrderTransitionObserver

    @Before
    fun setUp() {
        transitionObserver =
            DesktopInOrderTransitionObserver(
                Optional.of(desktopImmersiveController),
                focusTransitionObserver,
                Optional.of(desksTransitionObserver),
                Optional.of(desktopImeHandler),
                Optional.of(desktopBackNavTransitionObserver),
                desktopModeLoggerTransitionObserver,
            )
    }

    @Test
    fun onTransitionReady_forwardsToDesktopImmersiveController() {
        val transition = Mockito.mock(IBinder::class.java)
        val info = TransitionInfoBuilder(TRANSIT_CHANGE, 0).build()
        val startT = mock<SurfaceControl.Transaction>()
        val finishT = mock<SurfaceControl.Transaction>()

        transitionObserver.onTransitionReady(transition, info, startT, finishT)

        verify(desktopImmersiveController).onTransitionReady(transition, info, startT, finishT)
    }

    @Test
    fun onTransitionMerged_forwardsToDesktopImmersiveController() {
        val merged = Mockito.mock(IBinder::class.java)
        val playing = Mockito.mock(IBinder::class.java)

        transitionObserver.onTransitionMerged(merged, playing)

        verify(desktopImmersiveController).onTransitionMerged(merged, playing)
    }

    @Test
    fun onTransitionStarting_forwardsToDesktopImmersiveController() {
        val transition = Mockito.mock(IBinder::class.java)

        transitionObserver.onTransitionStarting(transition)

        verify(desktopImmersiveController).onTransitionStarting(transition)
    }

    @Test
    fun onTransitionFinished_forwardsToDesktopImmersiveController() {
        val transition = Mockito.mock(IBinder::class.java)

        transitionObserver.onTransitionFinished(transition, /* aborted= */ false)

        verify(desktopImmersiveController).onTransitionFinished(transition, /* aborted= */ false)
    }

    @Test
    fun onTransitionReady_forwardsToDesksTransitionObserver() {
        val transition = Mockito.mock(IBinder::class.java)
        val info = TransitionInfoBuilder(TRANSIT_CLOSE, /* flags= */ 0).build()

        transitionObserver.onTransitionReady(transition, info, StubTransaction(), StubTransaction())

        verify(desksTransitionObserver).onTransitionReady(transition, info)
    }

    @Test
    fun onTransitionMerged_forwardsToDesksTransitionObserver() {
        val merged = Mockito.mock(IBinder::class.java)
        val playing = Mockito.mock(IBinder::class.java)

        transitionObserver.onTransitionMerged(merged, playing)

        verify(desksTransitionObserver).onTransitionMerged(merged, playing)
    }

    @Test
    fun onTransitionFinished_forwardsToDesksTransitionObserver() {
        val transition = Mockito.mock(IBinder::class.java)

        transitionObserver.onTransitionFinished(transition, /* aborted= */ false)

        verify(desksTransitionObserver).onTransitionFinished(transition)
    }

    @Test
    fun onTransitionReady_forwardsToDesktopModeLoggerTransitionObserver() {
        val transition = Mockito.mock(IBinder::class.java)
        val info = TransitionInfoBuilder(TRANSIT_CHANGE, /* flags= */ 0).build()
        val startT = mock<SurfaceControl.Transaction>()
        val finishT = mock<SurfaceControl.Transaction>()

        val inorder = inOrder(desktopModeLoggerTransitionObserver, desksTransitionObserver)

        transitionObserver.onTransitionReady(transition, info, startT, finishT)

        inorder.verify(desksTransitionObserver).onTransitionReady(transition, info)
        inorder
            .verify(desktopModeLoggerTransitionObserver)
            .onTransitionReady(transition, info, startT, finishT)
    }

    @Test
    fun onTransitionFinished_forwardsToDesktopModeLoggerTransitionObserver() {
        val transition = Mockito.mock(IBinder::class.java)
        val aborted = false

        val inorder = inOrder(desktopModeLoggerTransitionObserver, desksTransitionObserver)

        transitionObserver.onTransitionFinished(transition, aborted)

        inorder.verify(desksTransitionObserver).onTransitionFinished(transition)
        inorder
            .verify(desktopModeLoggerTransitionObserver)
            .onTransitionFinished(transition, aborted)
    }
}
