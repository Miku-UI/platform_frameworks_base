/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.keyguard.mediator

import android.os.Looper
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.internal.policy.IKeyguardService.SCREEN_TURNING_ON_REASON_UNKNOWN
import com.android.systemui.Flags
import com.android.window.flags.Flags as WindowFlags
import com.android.systemui.SysuiTestCase
import com.android.systemui.shade.display.PendingDisplayChangeController
import com.android.systemui.unfold.FoldAodAnimationController
import com.android.systemui.unfold.FullscreenLightRevealAnimation
import com.android.systemui.unfold.SysUIUnfoldComponent
import com.android.systemui.util.mockito.capture
import com.android.systemui.utils.os.FakeHandler
import com.android.systemui.utils.os.FakeHandler.Mode.QUEUEING
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.Mock
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import java.util.Optional

@SmallTest
@RunWith(AndroidJUnit4::class)
class ScreenOnCoordinatorTest : SysuiTestCase() {

    @Mock
    private lateinit var runnable: Runnable

    @Mock
    private lateinit var unfoldComponent: SysUIUnfoldComponent

    @Mock
    private lateinit var foldAodAnimationController: FoldAodAnimationController

    @Mock
    private lateinit var fullscreenLightRevealAnimation: FullscreenLightRevealAnimation

    @Mock
    private lateinit var fullScreenLightRevealAnimations: Set<FullscreenLightRevealAnimation>

    @Mock
    private lateinit var pendingDisplayChangeController: PendingDisplayChangeController

    @Captor
    private lateinit var readyCaptor: ArgumentCaptor<Runnable>

    private val testHandler = FakeHandler(Looper.getMainLooper()).apply { setMode(QUEUEING) }

    private lateinit var screenOnCoordinator: ScreenOnCoordinator

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        fullScreenLightRevealAnimations = setOf(fullscreenLightRevealAnimation)
        `when`(unfoldComponent.getFullScreenLightRevealAnimations())
            .thenReturn(fullScreenLightRevealAnimations)
        `when`(unfoldComponent.getFoldAodAnimationController())
            .thenReturn(foldAodAnimationController)

        screenOnCoordinator =
            createScreenOnCoordinator(unfoldComponent = Optional.of(unfoldComponent))
    }

    @EnableFlags(
        WindowFlags.FLAG_ENSURE_WALLPAPER_DRAWN_ON_DISPLAY_SWITCH,
        Flags.FLAG_ENABLE_BACKGROUND_KEYGUARD_ONDRAWN_CALLBACK
    )
    @Test
    fun testUnfoldTransitionEnabledDrawnTasksReady_onScreenTurningOn_callsDrawnCallback() {
        screenOnCoordinator.onScreenTurningOn(reason = SCREEN_TURNING_ON_REASON_UNKNOWN, runnable)

        onUnfoldOverlayReady()
        onFoldAodReady()
        onPendingDisplayChangeReady()
        waitHandlerIdle()

        // Should be called when both unfold overlay and keyguard drawn ready
        verify(runnable).run()
    }

    @EnableFlags(
        WindowFlags.FLAG_ENSURE_WALLPAPER_DRAWN_ON_DISPLAY_SWITCH,
        Flags.FLAG_ENABLE_BACKGROUND_KEYGUARD_ONDRAWN_CALLBACK
    )
    @Test
    fun testPendingDisplayChangeNotReady_onScreenTurningOn_doesNotCallDrawnCallback() {
        screenOnCoordinator.onScreenTurningOn(reason = SCREEN_TURNING_ON_REASON_UNKNOWN, runnable)

        onUnfoldOverlayReady()
        onFoldAodReady()
        waitHandlerIdle()

        // We didn't call onPendingDisplayChangeReady(), so the ready runnable should not be called
        // as not all tasks are ready
        verify(runnable, never()).run()
    }

    @DisableFlags(WindowFlags.FLAG_ENSURE_WALLPAPER_DRAWN_ON_DISPLAY_SWITCH)
    @EnableFlags(Flags.FLAG_ENABLE_BACKGROUND_KEYGUARD_ONDRAWN_CALLBACK)
    @Test
    fun testEnsureWallpaperDrawnFlagDisabled_pendingDisplayChangeNotReady_onScreenTurningOn_callsDrawnCallback() {
        screenOnCoordinator.onScreenTurningOn(reason = SCREEN_TURNING_ON_REASON_UNKNOWN, runnable)

        onUnfoldOverlayReady()
        onFoldAodReady()
        // we don't call onPendingDisplayChangeReady() but as the flag is disabled, the
        // ready runnable should be called anyway
        waitHandlerIdle()

        // Should be called when both unfold overlay and keyguard drawn ready
        verify(runnable).run()
    }

    @EnableFlags(
        WindowFlags.FLAG_ENSURE_WALLPAPER_DRAWN_ON_DISPLAY_SWITCH,
        Flags.FLAG_ENABLE_BACKGROUND_KEYGUARD_ONDRAWN_CALLBACK
    )
    @Test
    fun testTasksReady_onScreenTurningOnAndTurnedOnEventsCalledTogether_callsDrawnCallback() {
        screenOnCoordinator.onScreenTurningOn(reason = SCREEN_TURNING_ON_REASON_UNKNOWN, runnable)
        screenOnCoordinator.onScreenTurnedOn()

        onUnfoldOverlayReady()
        onFoldAodReady()
        onPendingDisplayChangeReady()
        waitHandlerIdle()

        // Should be called when both unfold overlay and keyguard drawn ready
        verify(runnable).run()
    }

    @EnableFlags(
        WindowFlags.FLAG_ENSURE_WALLPAPER_DRAWN_ON_DISPLAY_SWITCH,
        Flags.FLAG_ENABLE_BACKGROUND_KEYGUARD_ONDRAWN_CALLBACK
    )
    @Test
    fun testTasksReady_onScreenTurnedOnAndTurnedOffBeforeCompletion_doesNotCallDrawnCallback() {
        screenOnCoordinator.onScreenTurningOn(reason = SCREEN_TURNING_ON_REASON_UNKNOWN, runnable)
        screenOnCoordinator.onScreenTurnedOn()
        screenOnCoordinator.onScreenTurnedOff()

        onUnfoldOverlayReady()
        onFoldAodReady()
        onPendingDisplayChangeReady()
        waitHandlerIdle()


        // Should not be called because this screen turning on call is not valid anymore
        verify(runnable, never()).run()
    }

    @EnableFlags(WindowFlags.FLAG_ENSURE_WALLPAPER_DRAWN_ON_DISPLAY_SWITCH)
    @DisableFlags(Flags.FLAG_ENABLE_BACKGROUND_KEYGUARD_ONDRAWN_CALLBACK)
    @Test
    fun testUnfoldTransitionDisabledDrawnTasksReady_onScreenTurningOn_callsDrawnCallback() {
        // Recreate with empty unfoldComponent
        screenOnCoordinator = createScreenOnCoordinator(unfoldComponent = Optional.empty())
        screenOnCoordinator.onScreenTurningOn(reason = SCREEN_TURNING_ON_REASON_UNKNOWN, runnable)
        onPendingDisplayChangeReady()
        waitHandlerIdle()

        // Should be called when only keyguard drawn
        verify(runnable).run()
    }

    @EnableFlags(WindowFlags.FLAG_ENSURE_WALLPAPER_DRAWN_ON_DISPLAY_SWITCH)
    @DisableFlags(Flags.FLAG_ENABLE_BACKGROUND_KEYGUARD_ONDRAWN_CALLBACK)
    @Test
    fun testUnfoldTransitionDisabledDrawnTasksReady_onScreenTurningOn_usesMainHandler() {
        // Recreate with empty unfoldComponent
        screenOnCoordinator = createScreenOnCoordinator(unfoldComponent = Optional.empty())
        screenOnCoordinator.onScreenTurningOn(reason = SCREEN_TURNING_ON_REASON_UNKNOWN, runnable)
        onPendingDisplayChangeReady()

        // Never called as the main handler didn't schedule it yet.
        verify(runnable, never()).run()
    }

    @EnableFlags(
        Flags.FLAG_ENABLE_BACKGROUND_KEYGUARD_ONDRAWN_CALLBACK,
        WindowFlags.FLAG_ENSURE_WALLPAPER_DRAWN_ON_DISPLAY_SWITCH
    )
    @Test
    fun unfoldTransitionDisabledDrawnTasksReady_onScreenTurningOn_bgCallback_callsDrawnCallback() {
        // Recreate with empty unfoldComponent
        screenOnCoordinator = createScreenOnCoordinator(unfoldComponent = Optional.empty())
        screenOnCoordinator.onScreenTurningOn(reason = SCREEN_TURNING_ON_REASON_UNKNOWN, runnable)
        onPendingDisplayChangeReady()
        // No need to wait for the handler to be idle, as it shouldn't be used
        // waitHandlerIdle()

        // Should be called when only keyguard drawn
        verify(runnable).run()
    }

    @EnableFlags(
        Flags.FLAG_ENABLE_BACKGROUND_KEYGUARD_ONDRAWN_CALLBACK,
        WindowFlags.FLAG_ENSURE_WALLPAPER_DRAWN_ON_DISPLAY_SWITCH
    )
    @Test
    fun unfoldTransitionDisabledDrawnTasksNotReady_onScreenTurningOn_bgCallback_doesNotCallDrawnCallback() {
        // Recreate with empty unfoldComponent
        screenOnCoordinator = createScreenOnCoordinator(unfoldComponent = Optional.empty())
        screenOnCoordinator.onScreenTurningOn(reason = SCREEN_TURNING_ON_REASON_UNKNOWN, runnable)
        onPendingDisplayChangeReady()
        // No need to wait for the handler to be idle, as it shouldn't be used
        // waitHandlerIdle()

        // Should be called when only keyguard drawn
        verify(runnable).run()
    }

    private fun createScreenOnCoordinator(unfoldComponent: Optional<SysUIUnfoldComponent>): ScreenOnCoordinator =
        ScreenOnCoordinator(
            unfoldComponent,
            testHandler,
            pendingDisplayChangeController,
        )

    private fun onUnfoldOverlayReady() {
        verify(fullscreenLightRevealAnimation).onScreenTurningOn(capture(readyCaptor))
        readyCaptor.value.run()
    }

    private fun onFoldAodReady() {
        verify(foldAodAnimationController).onScreenTurningOn(capture(readyCaptor))
        readyCaptor.value.run()
    }

    private fun onPendingDisplayChangeReady() {
        verify(pendingDisplayChangeController).onScreenTurningOn(anyInt(), capture(readyCaptor))
        readyCaptor.value.run()
    }

    private fun waitHandlerIdle() {
        testHandler.dispatchQueuedMessages()
    }
}
