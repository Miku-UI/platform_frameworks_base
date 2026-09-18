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

package com.android.wm.shell.scenarios

import android.app.Instrumentation
import android.platform.test.annotations.RequiresFlagsEnabled
import android.tools.Rotation
import android.tools.device.apphelpers.BrowserAppHelper
import android.tools.traces.parsers.WindowManagerStateHelper
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.android.launcher3.tapl.LauncherInstrumentation
import com.android.server.wm.flicker.helpers.DesktopModeAppHelper
import com.android.server.wm.flicker.helpers.SimpleAppHelper
import com.android.window.flags.Flags
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Ignore
import org.junit.Test

@Ignore("Test Base Class")
@RequiresFlagsEnabled(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE, Flags.FLAG_ENABLE_REMEMBERED_BOUNDS)
abstract class RememberedBounds(val rotation: Rotation = Rotation.ROTATION_0) :
    TestScenarioBase(rotation) {

    private val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
    private val tapl = LauncherInstrumentation()
    private val wmHelper = WindowManagerStateHelper(instrumentation)
    private val device = UiDevice.getInstance(instrumentation)

    private val targetApp = DesktopModeAppHelper(BrowserAppHelper(instrumentation))
    private val otherApp = DesktopModeAppHelper(SimpleAppHelper(instrumentation))

    @Before
    fun setup() {
        // Ensure we are in desktop mode by launching another app
        otherApp.enterDesktopMode(wmHelper, device)
    }

    @After
    fun teardown() {
        otherApp.exit(wmHelper)
        targetApp.exit(wmHelper)
    }

    /**
     * Launch a freeform browser app via startActivity. Drag move it. Close it. Open it again.
     * Verify the new instance opens in previously resized bounds.
     */
    @Test
    open fun dragMove_launchViaStartActivity() {
        verifyDragMoveRememberedBounds { targetApp.launchViaIntent(wmHelper) }
    }

    /**
     * Launch a browser app via taskbar. Drag move it. Close it. Open it again. Verify the new
     * instance opens in previously resized bounds.
     */
    @Test
    open fun dragMove_launchViaTaskbar() {
        verifyDragMoveRememberedBounds { launchAppViaTaskbar() }
    }

    /**
     * Launch a freeform browser app via startActivity. Resize it from corner. Close it. Open it
     * again. Verify the new instance opens in previously resized bounds.
     */
    @Test
    open fun cornerResize_launchViaStartActivity() {
        verifyCornerResizeRememberedBounds { targetApp.launchViaIntent(wmHelper) }
    }

    /**
     * Launch a browser app via taskbar. Resize it from corner. Close it. Open it again. Verify the
     * new instance opens in previously resized bounds.
     */
    @Test
    open fun cornerResize_launchViaTaskbar() {
        verifyCornerResizeRememberedBounds { launchAppViaTaskbar() }
    }

    /**
     * Launch a freeform browser app via startActivity. Snap it to right. Close it. Open it again.
     * Verify the new instance opens in snapped bounds.
     */
    @Test
    open fun snapRight_launchViaStartActivity() {
        verifySnapRightRememberedBounds { targetApp.launchViaIntent(wmHelper) }
    }

    /**
     * Launch a browser app via taskbar. Snap it to right. Close it. Open it again. Verify the new
     * instance opens in snapped bounds.
     */
    @Test
    open fun snapRight_launchViaTaskbar() {
        verifySnapRightRememberedBounds { launchAppViaTaskbar() }
    }

    /**
     * Launch a freeform browser app via startActivity. Maximize it. Close it. Open it again. Verify
     * the new instance opens in maximized bounds.
     */
    @Test
    open fun maximize_launchViaStartActivity() {
        verifyMaximizeRememberedBounds { targetApp.launchViaIntent(wmHelper) }
    }

    /**
     * Launch a browser app via taskbar. Maximize it. Close it. Open it again. Verify the new
     * instance opens in maximized bounds.
     */
    @Test
    open fun maximize_launchViaTaskbar() {
        verifyMaximizeRememberedBounds { launchAppViaTaskbar() }
    }

    private fun launchAppViaTaskbar() {
        tapl.showTaskbarIfHidden()
        tapl.launchedAppState.taskbar.getAppIcon(targetApp.appName).launch(targetApp.packageName)
    }

    private fun verifyDragMoveRememberedBounds(launchApp: () -> Unit) {
        // 1. Launch
        launchApp()
        targetApp.waitForTransitionToFreeform(wmHelper)

        // 2. Drag move it
        val initialBounds = wmHelper.getWindowRegion(targetApp).bounds
        targetApp.dragRight(wmHelper, device, distance = DRAG_DISTANCE)
        val draggedBounds = wmHelper.getWindowRegion(targetApp).bounds
        assertNotEquals("Window should have moved", initialBounds, draggedBounds)

        // 3. Close it
        targetApp.closeDesktopApp(wmHelper, device)

        // 4. Open it again
        launchApp()
        targetApp.waitForTransitionToFreeform(wmHelper)

        // 5. Verify the new instance opens in previously moved bounds
        val finalBounds = wmHelper.getWindowRegion(targetApp).bounds
        assertEquals("Moved bounds should be remembered", draggedBounds, finalBounds)
    }

    private fun verifyCornerResizeRememberedBounds(launchApp: () -> Unit) {
        // 1. Launch
        launchApp()
        targetApp.waitForTransitionToFreeform(wmHelper)

        // 2. Resize it from corner
        val initialBounds = wmHelper.getWindowRegion(targetApp).bounds
        targetApp.cornerResize(
            wmHelper,
            device,
            DesktopModeAppHelper.Corners.RIGHT_BOTTOM,
            horizontalChange = DRAG_DISTANCE,
            verticalChange = DRAG_DISTANCE,
        )
        val resizedBounds = wmHelper.getWindowRegion(targetApp).bounds
        assertNotEquals("Window should have been resized", initialBounds, resizedBounds)

        // 3. Close it
        targetApp.closeDesktopApp(wmHelper, device)

        // 4. Open it again
        launchApp()
        targetApp.waitForTransitionToFreeform(wmHelper)

        // 5. Verify the new instance opens in previously resized bounds
        val finalBounds = wmHelper.getWindowRegion(targetApp).bounds
        assertEquals("Resized bounds should be remembered", resizedBounds, finalBounds)
    }

    private fun verifySnapRightRememberedBounds(launchApp: () -> Unit) {
        // Place another app in snapped right below the browser app to verify no cascading
        otherApp.dragToSnapResizeRegion(wmHelper, device, instrumentation.context, isLeft = false)

        // 1. Launch
        launchApp()
        targetApp.waitForTransitionToFreeform(wmHelper)

        // 2. Snap it to right
        targetApp.dragToSnapResizeRegion(wmHelper, device, instrumentation.context, isLeft = false)
        val snappedBounds = wmHelper.getWindowRegion(targetApp).bounds

        // 3. Close it
        targetApp.closeDesktopApp(wmHelper, device)

        // 4. Open it again
        launchApp()
        targetApp.waitForTransitionToFreeform(wmHelper)

        // 5. Verify the new instance opens in snapped bounds
        val finalBounds = wmHelper.getWindowRegion(targetApp).bounds
        assertEquals("Snapped bounds should be remembered", snappedBounds, finalBounds)
    }

    private fun verifyMaximizeRememberedBounds(launchApp: () -> Unit) {
        // Place another app in maximized below the browser app to verify no cascading
        otherApp.maximiseDesktopApp(wmHelper, device)

        // 1. Launch
        launchApp()
        targetApp.waitForTransitionToFreeform(wmHelper)

        // 2. Maximize it
        targetApp.maximiseDesktopApp(wmHelper, device)
        val maximizedBounds = wmHelper.getWindowRegion(targetApp).bounds

        // 3. Close it
        targetApp.closeDesktopApp(wmHelper, device)

        // 4. Open it again
        launchApp()
        targetApp.waitForTransitionToFreeform(wmHelper)

        // 5. Verify the new instance opens in maximized bounds
        val finalBounds = wmHelper.getWindowRegion(targetApp).bounds
        assertEquals("Maximized bounds should be remembered", maximizedBounds, finalBounds)
    }

    private companion object {
        const val DRAG_DISTANCE = -100
    }
}
