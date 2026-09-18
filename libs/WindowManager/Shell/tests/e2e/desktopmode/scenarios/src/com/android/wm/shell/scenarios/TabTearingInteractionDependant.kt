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

package com.android.wm.shell.scenarios

import android.app.Instrumentation
import android.graphics.Rect
import android.tools.Rotation
import android.tools.device.apphelpers.BrowserAppHelper
import android.tools.traces.parsers.WindowManagerStateHelper
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.android.server.wm.flicker.helpers.DesktopModeAppHelper
import com.google.common.truth.Truth
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Test

/**
 * This class tests the interaction-dependant tab tearing functionality in desktop mode.
 *
 * It launches a browser, enters desktop mode, creates a new tab, resizes the window, and then tears
 * the tab out for a new window. It then verifies that the new window has the correct size and
 * position.
 */
@Ignore("Test Base Class")
abstract class TabTearingInteractionDependant(val rotation: Rotation = Rotation.ROTATION_0) :
    TestScenarioBase(rotation) {

    private val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
    private val wmHelper = WindowManagerStateHelper(instrumentation)
    private val device = UiDevice.getInstance(instrumentation)
    private val browserAppHelper = BrowserAppHelper(instrumentation)
    private val browserDesktopAppHelper = DesktopModeAppHelper(browserAppHelper)

    @Before
    fun setup() {
        browserAppHelper.launchViaIntent(wmHelper)
        browserAppHelper.closePopupsIfNeeded(device)
        browserDesktopAppHelper.enterDesktopMode(wmHelper, device)
        browserAppHelper.openThreeDotsMenu()
        browserAppHelper.clickNewTabInMenu()

        // Resize the window to a unique size before tearing
        browserDesktopAppHelper.cornerResize(
            wmHelper,
            device,
            DesktopModeAppHelper.Corners.RIGHT_BOTTOM,
            horizontalChange = 100,
            verticalChange = 100,
        )
    }

    @Test
    open fun tearTab() {
        browserAppHelper.performTabTearing(
            wmHelper,
            BrowserAppHelper.Companion.TabDraggingDirection.TOP_LEFT,
        )
        wmHelper.StateSyncBuilder().withAppTransitionIdle().waitForAndVerify()

        val wmStateAfterTear = wmHelper.currentState.wmState
        val visibleBrowserWindows =
            wmStateAfterTear.visibleAppWindows.filter {
                browserAppHelper.componentMatcher.windowMatchesAnyOf(it)
            }

        // The new window should be on top.
        val tornWindow = visibleBrowserWindows[0]
        val parentWindow = visibleBrowserWindows[1]

        assertTornWindow(parentWindow.frame, tornWindow.frame)
    }

    private fun assertTornWindow(parentWindow: Rect, tornWindow: Rect) {
        // Assert that the new window has the same size as the parent window
        Truth.assertThat(tornWindow.width()).isEqualTo(parentWindow.width())
        Truth.assertThat(tornWindow.height()).isEqualTo(parentWindow.height())

        // From BrowserAppHelper.performTabTearing, the drop point for TOP_LEFT is
        // offset by (-100, -100) from the parent window's top-left corner.
        val dropY = parentWindow.top - 100

        // TODO(b/452911424): Add check for the torn window's left and right position as well.
        // Assert that the new window is exactly at the drop point
        Truth.assertThat(tornWindow.top).isEqualTo(dropY)
    }

    @After
    fun teardown() {
        browserAppHelper.clearStorage()
        browserDesktopAppHelper.exit(wmHelper)
    }
}
