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

import android.app.ActivityManager
import android.app.KeyguardManager
import android.platform.test.annotations.EnableFlags
import android.testing.AndroidTestingRunner
import android.view.Display.DEFAULT_DISPLAY
import androidx.test.filters.SmallTest
import com.android.wm.shell.RootTaskDisplayAreaOrganizer
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.TestShellExecutor
import com.android.wm.shell.desktopmode.DesktopModeEventLogger.Companion.ExitReason
import com.android.wm.shell.shared.desktopmode.DesktopScrimListener
import com.android.wm.shell.sysui.ShellController
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.clearInvocations
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Test class for {@link DesktopScrimController}
 *
 * Usage: atest WMShellUnitTests:DesktopScrimControllerTest
 */
@SmallTest
@RunWith(AndroidTestingRunner::class)
class DesktopScrimControllerTest : ShellTestCase() {
    private val DEFAULT_USER_ID = ActivityManager.getCurrentUser()
    private val desktopRemoteListener = mock<DesktopRemoteListener>()
    private val desktopScrimListener = mock<DesktopScrimListener>()
    private val desktopTasksController = mock<DesktopTasksController>()
    private val shellController = mock<ShellController>()
    private val rootTaskDisplayAreaOrganizer = mock<RootTaskDisplayAreaOrganizer>()
    private val keyguardManager = mock<KeyguardManager>()
    private val displayIds = intArrayOf(DEFAULT_DISPLAY)

    private val shellExecutor = TestShellExecutor()
    private lateinit var controller: DesktopScrimController

    @Before
    fun setUp() {
        whenever(shellController.currentUserId).thenReturn(DEFAULT_USER_ID)
        whenever(rootTaskDisplayAreaOrganizer.displayIds).thenReturn(displayIds)
        controller =
            DesktopScrimController(
                desktopRemoteListener,
                desktopTasksController,
                shellController,
                rootTaskDisplayAreaOrganizer,
                keyguardManager,
                shellExecutor,
            )
    }

    @Test
    @EnableFlags(
        com.android.window.flags.Flags.FLAG_UPDATE_DESKTOP_SCRIM_ON_FULLSCREEN_LAUNCH,
        com.android.systemui.Flags.FLAG_OPAQUE_STATUS_BAR,
    )
    fun disableScrimEffectWhenTopFullscreenLaunched() {
        controller.addDesktopScrimListener(desktopScrimListener, shellExecutor)
        controller.handleExitCleanUp(
            DEFAULT_DISPLAY,
            shouldEndUpAtHome = false,
            ExitReason.FULLSCREEN_LAUNCH,
        )
        shellExecutor.flushAll()
        // Callback is called to handle exiting desk to fullscreen launch
        verify(desktopScrimListener).onDesktopScrimEffectChanged(DEFAULT_DISPLAY, false)
    }

    @Test
    @EnableFlags(
        com.android.window.flags.Flags.FLAG_UPDATE_DESKTOP_SCRIM_ON_FULLSCREEN_LAUNCH,
        com.android.systemui.Flags.FLAG_OPAQUE_STATUS_BAR,
    )
    fun doNotDisableScrimEffectWhenNotBackToHomeAndNotLaunchingFullscreen() {
        controller.addDesktopScrimListener(desktopScrimListener, shellExecutor)
        controller.handleExitCleanUp(
            DEFAULT_DISPLAY,
            shouldEndUpAtHome = false,
            ExitReason.SCREEN_OFF,
        )
        shellExecutor.flushAll()
        // No callback call when the desk is not going back home and the reason is not
        // FULLSCREEN_LAUNCH
        verify(desktopScrimListener, never()).onDesktopScrimEffectChanged(any(), any())
    }

    @Test
    @EnableFlags(com.android.systemui.Flags.FLAG_OPAQUE_STATUS_BAR)
    fun updateImmersiveAppearance_notifyDesktopScrimListener() {
        controller.addDesktopScrimListener(desktopScrimListener, shellExecutor)
        controller.updateDesktopScrim(DEFAULT_DISPLAY, true /* applyLightOutEffect */)
        shellExecutor.flushAll()
        verify(desktopScrimListener).onDesktopScrimEffectChanged(DEFAULT_DISPLAY, true)

        clearInvocations(desktopScrimListener)
        controller.removeDesktopScrimListener(desktopScrimListener)
        controller.updateDesktopScrim(DEFAULT_DISPLAY, false /* applyLightOutEffect */)
        shellExecutor.flushAll()
        verify(desktopScrimListener, never()).onDesktopScrimEffectChanged(any(), any())
    }

    @Test
    @EnableFlags(com.android.systemui.Flags.FLAG_OPAQUE_STATUS_BAR)
    fun updateDesktopScrim_cacheRedundantUpdates() {
        controller.addDesktopScrimListener(desktopScrimListener, shellExecutor)
        controller.updateDesktopScrim(DEFAULT_DISPLAY, true /* applyLightOutEffect */)
        shellExecutor.flushAll()
        verify(desktopScrimListener).onDesktopScrimEffectChanged(DEFAULT_DISPLAY, true)

        clearInvocations(desktopScrimListener)
        // Redundant update, should be cached
        controller.updateDesktopScrim(DEFAULT_DISPLAY, true /* applyLightOutEffect */)
        shellExecutor.flushAll()
        verify(desktopScrimListener, never()).onDesktopScrimEffectChanged(any(), any())

        // Different display ID, should not be cached
        val secondDisplayId = DEFAULT_DISPLAY + 1
        controller.updateDesktopScrim(secondDisplayId, true /* applyLightOutEffect */)
        shellExecutor.flushAll()
        verify(desktopScrimListener).onDesktopScrimEffectChanged(secondDisplayId, true)

        // Different value, should not be cached
        controller.updateDesktopScrim(DEFAULT_DISPLAY, false /* applyLightOutEffect */)
        shellExecutor.flushAll()
        verify(desktopScrimListener).onDesktopScrimEffectChanged(DEFAULT_DISPLAY, false)
    }

    @Test
    @EnableFlags(
        com.android.window.flags.Flags.FLAG_UPDATE_DESKTOP_SCRIM_WHEN_LOCKED_BUGFIX,
        com.android.systemui.Flags.FLAG_OPAQUE_STATUS_BAR,
    )
    fun disableScrimEffectWhenKeyguardStateChanged_bugFixEnabled() {
        controller.onInit()
        controller.addDesktopScrimListener(desktopScrimListener, shellExecutor)
        // Set the keyguard to be locked
        whenever(keyguardManager.isKeyguardLocked).thenReturn(true)
        controller.onKeyguardLockedStateChanged(true /* isKeyguardLocked */)
        shellExecutor.flushAll()
        // The effect should be gone
        verify(desktopScrimListener).onDesktopScrimEffectChanged(DEFAULT_DISPLAY, false)
    }

    @Test
    @EnableFlags(
        com.android.window.flags.Flags.FLAG_HANDLE_OVERVIEW_DESKTOP_SCRIM_BUGFIX,
        com.android.systemui.Flags.FLAG_OPAQUE_STATUS_BAR,
    )
    fun disableScrimEffectWhenOverviewShown_bugFixEnabled() {
        controller.onInit()
        controller.addDesktopScrimListener(desktopScrimListener, shellExecutor)
        // Send the overview shown callback
        controller.onOverviewShown(DEFAULT_DISPLAY)
        shellExecutor.flushAll()
        // The effect should be gone
        verify(desktopScrimListener).onDesktopScrimEffectChanged(DEFAULT_DISPLAY, false)
    }
    // TODO(b/489916353): Add tests to cover all other methods in DesktopScrimController
}
