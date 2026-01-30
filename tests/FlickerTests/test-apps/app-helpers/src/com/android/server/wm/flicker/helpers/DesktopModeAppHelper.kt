/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.server.wm.flicker.helpers

import android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM
import android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN
import android.content.Context
import android.content.Intent.FLAG_ACTIVITY_MULTIPLE_TASK
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.graphics.Insets
import android.graphics.Point
import android.graphics.Rect
import android.graphics.Region
import android.os.SystemClock
import android.platform.uiautomatorhelpers.DeviceHelpers
import android.tools.PlatformConsts
import android.tools.device.apphelpers.IStandardAppHelper
import android.tools.device.apphelpers.StandardAppHelper
import android.tools.helpers.SYSTEMUI_PACKAGE
import android.tools.traces.parsers.WindowManagerStateHelper
import android.tools.traces.wm.WindowingMode
import android.view.Display.DEFAULT_DISPLAY
import android.view.KeyEvent.KEYCODE_D
import android.view.KeyEvent.KEYCODE_DPAD_DOWN
import android.view.KeyEvent.KEYCODE_DPAD_UP
import android.view.KeyEvent.KEYCODE_EQUALS
import android.view.KeyEvent.KEYCODE_LEFT_BRACKET
import android.view.KeyEvent.KEYCODE_MINUS
import android.view.KeyEvent.KEYCODE_RIGHT_BRACKET
import android.view.KeyEvent.META_CTRL_ON
import android.view.KeyEvent.META_META_ON
import android.view.WindowInsets
import android.view.WindowManager
import android.window.DesktopModeFlags
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.android.server.wm.flicker.helpers.MotionEventHelper.InputMethod.TOUCH
import java.time.Duration
import kotlin.math.abs

/**
 * Wrapper class around App helper classes. This class adds functionality to the apps that the
 * desktop apps would have.
 */
open class DesktopModeAppHelper(private val innerHelper: StandardAppHelper) :
    IStandardAppHelper by innerHelper {

    enum class Corners {
        LEFT_TOP,
        RIGHT_TOP,
        LEFT_BOTTOM,
        RIGHT_BOTTOM
    }

    enum class Edges {
        LEFT,
        RIGHT,
        TOP,
        BOTTOM
    }

    enum class AppProperty {
        STANDARD,
        NON_RESIZABLE
    }

    /** Launch an app and ensure it's moved to Desktop if it has not. */
    fun enterDesktopMode(
        wmHelper: WindowManagerStateHelper,
        device: UiDevice,
        motionEventHelper: MotionEventHelper = MotionEventHelper(getInstrumentation(), TOUCH),
        shouldUseDragToDesktop: Boolean = false,
    ) {
        innerHelper.launchViaIntent(wmHelper)
        if (isInDesktopWindowingMode(wmHelper)) return

        if (shouldUseDragToDesktop) {
            enterDesktopModeWithDrag(
                wmHelper = wmHelper,
                device = device,
                motionEventHelper = motionEventHelper
            )
        } else {
            enterDesktopModeFromAppHandleMenu(wmHelper, device)
        }
    }

    /** Move an app to Desktop by dragging the app handle at the top. */
    private fun enterDesktopModeWithDrag(
        wmHelper: WindowManagerStateHelper,
        device: UiDevice,
        motionEventHelper: MotionEventHelper = MotionEventHelper(getInstrumentation(), TOUCH)
    ) {
        if (isAnyDesktopWindowVisible(wmHelper)) error("Already in Desktop Mode")

        dragToDesktop(
            wmHelper = wmHelper,
            device = device,
            motionEventHelper = motionEventHelper
        )
        waitForTransitionToFreeform(wmHelper)
    }

    private fun dragToDesktop(
        wmHelper: WindowManagerStateHelper,
        device: UiDevice,
        motionEventHelper: MotionEventHelper
    ) {
        val (startX, startY) = getAppHandleDragPosition(wmHelper)
        val displayRect = getDisplayRect(wmHelper)

        // The position we want to drag to
        val endY = displayRect.centerY() / 2

        // drag the window to move to desktop
        if (motionEventHelper.inputMethod == TOUCH
            && DesktopModeFlags.ENABLE_HOLD_TO_DRAG_APP_HANDLE.isTrue) {
            // Touch requires hold-to-drag.
            motionEventHelper.holdToDrag(startX, startY, startX, endY, steps = 100)
        } else {
            device.drag(startX, startY, startX, endY, 100)
        }
    }

    /**
     * Drags a window to one side of the screen to enter split-screen mode.
     *
     * This function simulates a user dragging an application window from near the top-center
     * to either the left or right edge of the display, triggering a split-screen view.
     *
     * @param wmHelper Helper class to get information about the window manager state, such as the
     * window's current position and size.
     * @param device The UiDevice instance used to interact with the device, specifically to perform
     * the drag gesture.
     * @param direction The target side of the screen to drag the window to, either
     * {@link SplitDirection#LEFT} or {@link SplitDirection#RIGHT}.
     * @param motionEventHelper A helper for dispatching motion events. It is used for touch-based
     * input.
     */
    fun dragFromFullscreenToSplit(
        wmHelper: WindowManagerStateHelper,
        device: UiDevice,
        direction: SplitDirection,
        motionEventHelper: MotionEventHelper = MotionEventHelper(getInstrumentation(), TOUCH),
    ) {
        if (!isInFullscreenMode(wmHelper)) {
            error("Should be in Fullscreen mode")
        }

        val (startX, startY) = getAppHandleDragPosition(wmHelper)
        val displayRect = getDisplayRect(wmHelper)

        // Determine the end coordinates for the drag.
        val endY = displayRect.centerY()
        val endX = when (direction) {
            SplitDirection.LEFT -> displayRect.left + 20 // Target the left edge with an offset.
            SplitDirection.RIGHT -> displayRect.right - 20 // Target the right edge with an offset.
        }

        val steps = 100 // Number of move steps in the drag gesture.

        // Perform the drag action.
        if (motionEventHelper.inputMethod == TOUCH
            && DesktopModeFlags.ENABLE_HOLD_TO_DRAG_APP_HANDLE.isTrue) {
            // We need more sleep because we are waiting for the split screen indicator.
            motionEventHelper.holdToDrag(startX, startY, endX, endY, steps, sleepTimeBeforeDrop = 1000)
        } else {
            // Use standard UiDevice.drag for other input methods (e.g., mouse).
            device.drag(startX, startY, endX, endY, steps)
        }
    }

    private fun getAppHandleDragPosition(wmHelper: WindowManagerStateHelper): Pair<Int, Int> {
        val windowRect = wmHelper.getWindowRegion(innerHelper).bounds
        val startX = windowRect.centerX()

        // Start dragging a little under the top to prevent dragging the notification shade.
        val startY = 10
        return Pair(startX, startY)
    }

    private fun getMaximizeButtonForTheApp(caption: UiObject2?): UiObject2 {
        return caption
            ?.children
            ?.find { it.resourceName.endsWith(MAXIMIZE_BUTTON_VIEW) }
            ?.children
            ?.get(0)
            ?: error("Unable to find resource $MAXIMIZE_BUTTON_VIEW\n")
    }

    /** Maximize a given app to fill the stable bounds. */
    fun maximiseDesktopApp(
        wmHelper: WindowManagerStateHelper,
        device: UiDevice,
        trigger: MaximizeDesktopAppTrigger = MaximizeDesktopAppTrigger.MAXIMIZE_MENU,
    ) {
        val caption = getCaptionForTheApp(wmHelper, device)!!
        val maximizeButton = getMaximizeButtonForTheApp(caption)

        when (trigger) {
            MaximizeDesktopAppTrigger.MAXIMIZE_MENU -> maximizeButton.click()
            MaximizeDesktopAppTrigger.DOUBLE_TAP_APP_HEADER -> {
                caption.click()
                Thread.sleep(50)
                caption.click()
            }

            MaximizeDesktopAppTrigger.KEYBOARD_SHORTCUT -> {
                val keyEventHelper = KeyEventHelper(getInstrumentation())
                keyEventHelper.press(KEYCODE_EQUALS, META_META_ON)
            }

            MaximizeDesktopAppTrigger.MAXIMIZE_BUTTON_IN_MENU -> {
                maximizeButton.longClick()
                wmHelper.StateSyncBuilder().withAppTransitionIdle().waitForAndVerify()
                val buttonResId = MAXIMIZE_BUTTON_IN_MENU
                val maximizeMenu = getDesktopAppViewByRes(MAXIMIZE_MENU)
                val maximizeButtonInMenu =
                    maximizeMenu
                        ?.wait(
                            Until.findObject(By.res(SYSTEMUI_PACKAGE, buttonResId)),
                            TIMEOUT.toMillis()
                        )
                        ?: error("Unable to find object with resource id $buttonResId")
                maximizeButtonInMenu.click()
            }
        }
        wmHelper.StateSyncBuilder().withAppTransitionIdle().waitForAndVerify()
    }

    private fun getMinimizeButtonForTheApp(caption: UiObject2?): UiObject2 {
        return caption
            ?.children
            ?.find { it.resourceName.endsWith(MINIMIZE_BUTTON_VIEW) }
            ?: error("Unable to find resource $MINIMIZE_BUTTON_VIEW\n")
    }

    fun minimizeDesktopApp(
        wmHelper: WindowManagerStateHelper,
        device: UiDevice,
        isPip: Boolean = false,
        usingKeyboard: Boolean = false,
    ) {
        if (usingKeyboard) {
            val keyEventHelper = KeyEventHelper(getInstrumentation())
            keyEventHelper.press(KEYCODE_MINUS, META_META_ON)
        } else {
            val caption = getCaptionForTheApp(wmHelper, device)
            val minimizeButton = getMinimizeButtonForTheApp(caption)
            minimizeButton.click()
        }

        wmHelper
            .StateSyncBuilder()
            .withAppTransitionIdle()
            .apply {
                if (isPip) withPipShown()
                else
                    withWindowSurfaceDisappeared(innerHelper)
                        .withActivityState(innerHelper, PlatformConsts.STATE_STOPPED)
            }
            .waitForAndVerify()
    }

    private fun getHeaderEmptyView(caption: UiObject2?): UiObject2 {
        return caption
            ?.children
            ?.find { it.resourceName.endsWith(HEADER_EMPTY_VIEW) }
            ?: error("Unable to find resource $HEADER_EMPTY_VIEW\n")
    }

    /** Click on an existing window's header to bring it to the front. */
    fun bringToFront(wmHelper: WindowManagerStateHelper, device: UiDevice) {
        val caption = getCaptionForTheApp(wmHelper, device)
        val openHeaderView = getHeaderEmptyView(caption)
        openHeaderView.click()
        wmHelper
            .StateSyncBuilder()
            .withAppTransitionIdle()
            .withTopVisibleApp(innerHelper)
            .waitForAndVerify()
    }

    /** Open maximize menu and click snap resize button on the app header for the given app. */
    fun snapResizeDesktopApp(
        wmHelper: WindowManagerStateHelper,
        device: UiDevice,
        context: Context,
        toLeft: Boolean
    ) {
        val caption = getCaptionForTheApp(wmHelper, device)
        val maximizeButton = getMaximizeButtonForTheApp(caption)
        maximizeButton.longClick()
        wmHelper.StateSyncBuilder().withAppTransitionIdle().waitForAndVerify()

        val buttonResId = if (toLeft) SNAP_LEFT_BUTTON else SNAP_RIGHT_BUTTON
        val maximizeMenu = getDesktopAppViewByRes(MAXIMIZE_MENU)

        val snapResizeButton =
            maximizeMenu
                ?.wait(Until.findObject(By.res(SYSTEMUI_PACKAGE, buttonResId)), TIMEOUT.toMillis())
                ?: error("Unable to find object with resource id $buttonResId")
        snapResizeButton.click()

        waitAndVerifySnapResize(wmHelper, context, toLeft)
    }

    fun snapResizeWithKeyboard(
        wmHelper: WindowManagerStateHelper,
        context: Context,
        keyEventHelper: KeyEventHelper,
        toLeft: Boolean,
    ) {
        val bracketKey = if (toLeft) KEYCODE_LEFT_BRACKET else KEYCODE_RIGHT_BRACKET
        keyEventHelper.press(bracketKey, META_META_ON)
        waitAndVerifySnapResize(wmHelper, context, toLeft)
    }

    private fun waitAndVerifySnapResize(
        wmHelper: WindowManagerStateHelper,
        context: Context,
        toLeft: Boolean
    ) {
        val displayRect = getDisplayRect(wmHelper)
        val insets = getWindowInsets(
            context, WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars()
        )
        displayRect.inset(insets)

        val expectedWidth = displayRect.width() / 2
        val expectedRect = Rect(displayRect).apply {
            if (toLeft) right -= expectedWidth else left += expectedWidth
        }
        wmHelper.StateSyncBuilder()
            .withAppTransitionIdle()
            .withSurfaceMatchingVisibleRegion(
                this,
                Region(expectedRect),
                { surfaceRegion, expectedRegion ->
                    areSnapWindowRegionsMatchingWithinThreshold(
                        surfaceRegion, expectedRegion, toLeft
                    )
                })
            .waitForAndVerify()
    }

    /** Close a desktop app by clicking the close button on the app header for the given app. */
    fun closeDesktopApp(
        wmHelper: WindowManagerStateHelper,
        device: UiDevice,
        ) {
        val caption = getCaptionForTheApp(wmHelper, device)
        val closeButton = caption?.children?.find { it.resourceName.endsWith(CLOSE_BUTTON) }
        closeButton?.click()

        wmHelper
            .StateSyncBuilder()
            .withAppTransitionIdle()
            .withWindowSurfaceDisappeared(innerHelper)
            .waitForAndVerify()
    }

    fun getCaptionForTheApp(
        wmHelper: WindowManagerStateHelper,
        device: UiDevice
    ): UiObject2? {
        if (
            wmHelper.getWindow(innerHelper)?.windowingMode !=
            WindowingMode.WINDOWING_MODE_FREEFORM.value
        ) error("expected a freeform window with caption but window is not in freeform mode")
        val captions =
            device.wait(Until.findObjects(caption), TIMEOUT.toMillis())
                ?: error("Unable to find view $caption\n")

        return captions.find {
            wmHelper.getWindowRegion(innerHelper).bounds.contains(it.visibleBounds)
        }
    }

    /** Resize a desktop app from its corners. */
    fun cornerResize(
        wmHelper: WindowManagerStateHelper,
        device: UiDevice,
        corner: Corners,
        horizontalChange: Int,
        verticalChange: Int
    ) {
        val windowRect = wmHelper.getWindowRegion(innerHelper).bounds
        val (startX, startY) = getStartCoordinatesForCornerResize(windowRect, corner)

        // The position we want to drag to
        val endY = startY + verticalChange
        val endX = startX + horizontalChange

        // drag the specified corner of the window to the end coordinate.
        dragWindow(startX, startY, endX, endY, wmHelper, device)
    }

    /** Resize a desktop app from its edges. */
    fun edgeResize(
        wmHelper: WindowManagerStateHelper,
        motionEvent: MotionEventHelper,
        edge: Edges
    ) {
        val windowRect = wmHelper.getWindowRegion(innerHelper).bounds
        val (startX, startY) = getStartCoordinatesForEdgeResize(windowRect, edge)
        val verticalChange = when (edge) {
            Edges.LEFT -> 0
            Edges.RIGHT -> 0
            Edges.TOP -> -100
            Edges.BOTTOM -> 100
        }
        val horizontalChange = when (edge) {
            Edges.LEFT -> -100
            Edges.RIGHT -> 100
            Edges.TOP -> 0
            Edges.BOTTOM -> 0
        }

        // The position we want to drag to
        val endY = startY + verticalChange
        val endX = startX + horizontalChange

        val downTime = SystemClock.uptimeMillis()
        motionEvent.actionDown(startX, startY, time = downTime)
        motionEvent.actionMove(startX, startY, endX, endY, /* steps= */100, downTime = downTime)
        motionEvent.actionUp(endX, endY, downTime = downTime)
        wmHelper
            .StateSyncBuilder()
            .withAppTransitionIdle()
            .waitForAndVerify()
    }

    /** Drag a window from a source coordinate to a destination coordinate. */
    fun dragWindow(
        startX: Int, startY: Int,
        endX: Int, endY: Int,
        wmHelper: WindowManagerStateHelper,
        device: UiDevice
    ) {
        device.drag(startX, startY, endX, endY, /* steps= */ 100)
        wmHelper
            .StateSyncBuilder()
            .withAppTransitionIdle()
            .waitForAndVerify()
    }

    /**
     * Simulates dragging the top-left corner of the current window to the center of the device
     * screen.
     *
     * This function calculates a starting point slightly offset from the actual top-left corner (60
     * pixels down and right to prevent app resizing) and drags it to the center of the default
     * display.
     *
     * @param device The UiDevice instance used to perform the drag interaction on the device.
     * @param wmHelper A helper class instance used to retrieve window and display dimension
     *   information.
     * @param direction A direction where the window should be dragged to.
     * @throws IllegalStateException if the default display information cannot be obtained from the
     *   wmHelper.
     */
    fun dragWindowTopLeftCorner(
        device: UiDevice,
        wmHelper: WindowManagerStateHelper,
        direction: WindowDraggingDirection = WindowDraggingDirection.CENTER,
    ) {
        val windowBounds = wmHelper.getWindowRegion(this).bounds
        val displayBounds = getDisplayRect(wmHelper)
        // We take start dragging point with some offset to use app moving instead of resizing.
        val startDraggingPoint =
            Point(/* x= */ windowBounds.left + 60, /* y= */ windowBounds.top + 60)
        val endDraggingPoint =
            when (direction) {
                WindowDraggingDirection.CENTER ->
                    Point(/* x= */ displayBounds.centerX(), /* y= */ displayBounds.centerY())
            }

        device.drag(
            startDraggingPoint.x,
            startDraggingPoint.y,
            endDraggingPoint.x,
            endDraggingPoint.y,
            /* steps= */ 100,
        )
        wmHelper
            .StateSyncBuilder()
            .withAppTransitionIdle()
            .waitForAndVerify()
    }

    /** Drag a window to a snap resize region, found at the left and right edges of the screen. */
    fun dragToSnapResizeRegion(
        wmHelper: WindowManagerStateHelper,
        device: UiDevice,
        isLeft: Boolean,
    ) {
        val windowRect = wmHelper.getWindowRegion(innerHelper).bounds
        // Set start x-coordinate as center of app header.
        val startX = windowRect.centerX()
        val startY = windowRect.top

        val displayRect = getDisplayRect(wmHelper)

        val endX = if (isLeft) {
            displayRect.left + SNAP_RESIZE_DRAG_INSET
        } else {
            displayRect.right - SNAP_RESIZE_DRAG_INSET
        }
        val endY = displayRect.centerY() / 2

        // drag the window to snap resize
        device.drag(startX, startY, endX, endY, /* steps= */ 100)
        wmHelper
            .StateSyncBuilder()
            .withAppTransitionIdle()
            .waitForAndVerify()
    }

    fun dragRight(wmHelper: WindowManagerStateHelper, device: UiDevice, distance: Int) {
        val windowRect = wmHelper.getWindowRegion(innerHelper).bounds
        // Set start x-coordinate as center of app header.
        val startX = windowRect.centerX()
        val startY = windowRect.top

        val endX = startX + distance
        val endY = startY + distance

        // drag the window to to the right by [distance].
        device.drag(startX, startY, endX, endY, /* steps= */ 100)
        wmHelper
            .StateSyncBuilder()
            .withAppTransitionIdle()
            .waitForAndVerify()
    }

    private fun getStartCoordinatesForCornerResize(
        windowRect: Rect,
        corner: Corners
    ): Pair<Int, Int> {
        return when (corner) {
            Corners.LEFT_TOP -> Pair(windowRect.left, windowRect.top)
            Corners.RIGHT_TOP -> Pair(windowRect.right, windowRect.top)
            Corners.LEFT_BOTTOM -> Pair(windowRect.left, windowRect.bottom)
            Corners.RIGHT_BOTTOM -> Pair(windowRect.right, windowRect.bottom)
        }
    }

    private fun getStartCoordinatesForEdgeResize(
        windowRect: Rect,
        edge: Edges
    ): Pair<Int, Int> {
        return when (edge) {
            Edges.LEFT -> Pair(windowRect.left, windowRect.bottom / 2)
            Edges.RIGHT -> Pair(windowRect.right, windowRect.bottom / 2)
            Edges.TOP -> Pair(windowRect.right / 2, windowRect.top)
            Edges.BOTTOM -> Pair(windowRect.right / 2, windowRect.bottom)
        }
    }

    /** Exit desktop mode by dragging the app handle to the top drag zone. */
    fun exitDesktopWithDragToTopDragZone(
        wmHelper: WindowManagerStateHelper,
        device: UiDevice,
    ) {
        dragAppWindowToTopDragZone(wmHelper, device)
        waitForTransitionToFullscreen(wmHelper)
    }

    /** Maximize an app by dragging the app handle to the top drag zone. */
    fun maximizeAppWithDragToTopDragZone(
        wmHelper: WindowManagerStateHelper,
        device: UiDevice,
    ) {
        dragAppWindowToTopDragZone(wmHelper, device)
    }

    private fun dragAppWindowToTopDragZone(wmHelper: WindowManagerStateHelper, device: UiDevice) {
        val windowRect = wmHelper.getWindowRegion(innerHelper).bounds
        val displayRect = getDisplayRect(wmHelper)

        val startX = windowRect.centerX()
        val endX = displayRect.centerX()
        val startY = windowRect.top
        val endY = 0 // top of the screen

        // drag the app window to top drag zone
        device.drag(startX, startY, endX, endY, 100)
    }

    fun enterDesktopModeViaKeyboard(
        wmHelper: WindowManagerStateHelper,
    ) {
        if (isAnyDesktopWindowVisible(wmHelper)) error("Already in Desktop Mode")

        val keyEventHelper = KeyEventHelper(getInstrumentation())
        keyEventHelper.press(KEYCODE_DPAD_DOWN, META_META_ON or META_CTRL_ON)
        waitForTransitionToFreeform(wmHelper)
    }

    fun exitDesktopModeToFullScreenViaKeyboard(
        wmHelper: WindowManagerStateHelper,
    ) {
        val keyEventHelper = KeyEventHelper(getInstrumentation())
        keyEventHelper.press(KEYCODE_DPAD_UP, META_META_ON or META_CTRL_ON)
        waitForTransitionToFullscreen(wmHelper)
    }

    private fun clickAppHandle(wmHelper: WindowManagerStateHelper, device: UiDevice) {
        val windowRect = wmHelper.getWindowRegion(innerHelper).bounds
        val startX = windowRect.centerX()
        // Click a little under the top to prevent opening the notification shade.
        val startY = windowRect.top + 30

        // Click on the app handle coordinates.
        device.click(startX, startY)
    }

    fun enterDesktopModeFromAppHandleMenu(
        wmHelper: WindowManagerStateHelper,
        device: UiDevice,
        isImmersiveApp: Boolean = false,
    ) {
        if (isAnyDesktopWindowVisible(wmHelper)) error("Already in Desktop Mode")
        if (isImmersiveApp) {
            val windowRect = wmHelper.getWindowRegion(innerHelper).bounds
            val startX = windowRect.centerX()
            val startY = windowRect.top
            val endY = windowRect.centerY() / 2
            // Swipe down from the top edge of the screen to show the app handle
            device.swipe(startX, startY, startX, endY, 10)
        }
        clickAppHandle(wmHelper, device)
        wmHelper.StateSyncBuilder().withAppTransitionIdle().waitForAndVerify()

        val pill = getDesktopAppViewByRes(PILL_CONTAINER)
        val desktopModeButton =
            pill.children?.find { it.resourceName.endsWith(DESKTOP_MODE_BUTTON) }
                ?: error("Unable to find Desktop Mode button")

        desktopModeButton.click()
        waitForTransitionToFreeform(wmHelper)
    }

    fun enterImmersiveMode(
        wmHelper: WindowManagerStateHelper,
        device: UiDevice
    ) {
        val caption = getCaptionForTheApp(wmHelper, device)
        val maximizeButton = getMaximizeButtonForTheApp(caption)
        maximizeButton.longClick()
        wmHelper.StateSyncBuilder().withAppTransitionIdle().waitForAndVerify()

        val buttonResId = IMMERSIVE_BUTTON_IN_MENU
        val maximizeMenu = getDesktopAppViewByRes(MAXIMIZE_MENU)

        val immersiveButton =
            maximizeMenu
                ?.wait(Until.findObject(By.res(SYSTEMUI_PACKAGE, buttonResId)), TIMEOUT.toMillis())
                ?: error("Unable to find object with resource id $buttonResId")
        immersiveButton.click()
        wmHelper.StateSyncBuilder().withAppTransitionIdle().waitForAndVerify()
    }

    /** Exits immersive mode to maximized in desktop window via META+= keyboard shortcut. */
    fun exitImmersiveToDesktopWithKeyboard(
        wmHelper: WindowManagerStateHelper
    ) {
        val keyEventHelper = KeyEventHelper(getInstrumentation())
        keyEventHelper.press(KEYCODE_EQUALS, META_META_ON)
        wmHelper.StateSyncBuilder().withAppTransitionIdle().waitForAndVerify()
    }

    fun enterSplitScreenFromAppHandleMenu(
        wmHelper: WindowManagerStateHelper,
        device: UiDevice
    ) {
        clickAppHandle(wmHelper, device)

        val pill = getDesktopAppViewByRes(PILL_CONTAINER)
        val splitScreenButton =
            pill.children?.find { it.resourceName.endsWith(SPLIT_SCREEN_BUTTON) }
                ?: error("Unable to find Split Screen button")

        splitScreenButton.click()
        wmHelper.StateSyncBuilder().withAppTransitionIdle().waitForAndVerify()
    }

    fun exitDesktopModeToFullScreenWithAppHeader(wmHelper: WindowManagerStateHelper) {
        val openMenuButton = getDesktopAppViewByRes(OPEN_MENU_BUTTON)
        openMenuButton?.click()
        wmHelper.StateSyncBuilder().withAppTransitionIdle().waitForAndVerify()

        val pill = getDesktopAppViewByRes(PILL_CONTAINER)
        val fullScreenModeButton =
            pill
                ?.children
                ?.find { it.resourceName.endsWith(FULL_SCREEN_BUTTON) }

        fullScreenModeButton?.click()
        wmHelper.StateSyncBuilder().withAppTransitionIdle().waitForAndVerify()
    }

    fun exitDesktopModeToSplitScreenWithAppHeader(wmHelper: WindowManagerStateHelper) {
        val openMenuButton = getDesktopAppViewByRes(OPEN_MENU_BUTTON)
        openMenuButton?.click()
        wmHelper.StateSyncBuilder().withAppTransitionIdle().waitForAndVerify()

        val pill = getDesktopAppViewByRes(PILL_CONTAINER)
        val splitScreenModeButton =
            pill
                ?.children
                ?.find { it.resourceName.endsWith(SPLIT_SCREEN_BUTTON) }

        splitScreenModeButton?.click()
        wmHelper.StateSyncBuilder().withAppTransitionIdle().waitForAndVerify()
    }

    fun restartFromAppHandleMenu(wmHelper: WindowManagerStateHelper) {
        val openMenuButton = getDesktopAppViewByRes(OPEN_MENU_BUTTON)
        openMenuButton?.click()
        wmHelper.StateSyncBuilder().withAppTransitionIdle().waitForAndVerify()

        val restartHandleMenu = getDesktopAppViewByRes(RESTART_BUTTON)
        restartHandleMenu?.click()
        wmHelper.StateSyncBuilder().withAppTransitionIdle().waitForAndVerify()

        val restartDialogConfirmButton =
            getDesktopAppViewByRes(RESTART_DIALOG_RESTART_BUTTON)
        restartDialogConfirmButton?.click()
        wmHelper.StateSyncBuilder().withAppTransitionIdle().waitForAndVerify()
    }

    fun moveToNextDisplayViaKeyboard(wmHelper: WindowManagerStateHelper, expectedDisplayId: Int) {
        val keyEventHelper = KeyEventHelper(getInstrumentation())
        keyEventHelper.press(KEYCODE_D, META_META_ON or META_CTRL_ON)
        wmHelper.StateSyncBuilder().withAppTransitionIdle().waitForAndVerify()

        wmHelper.StateSyncBuilder().apply {
            add("App is on display #$expectedDisplayId") { dump ->
                val display = requireNotNull(dump.wmState.getDisplay(expectedDisplayId)) {
                    "Display $expectedDisplayId not found"
                }
                display.containsActivity(innerHelper)
            }
        }.waitForAndVerify()
    }

    /**
     * Opens a specified number of the same application.
     *
     * This method iterates a number of times, defined by the {@code numTasks}, and on each
     * iteration, it launches a new instance of an application.
     *
     * @param wmHelper The helper class that waits until some action (like app opening) is completed.
     * @param numTasks The number of tasks to open from the same application.
     */
    fun openTasks(wmHelper: WindowManagerStateHelper, numTasks: Int) {
        for (i in 0..<numTasks) {
            launchViaIntent(
                wmHelper = wmHelper,
                intent = innerHelper.openAppIntent.apply {
                    addFlags(FLAG_ACTIVITY_MULTIPLE_TASK or FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }
    }

    fun clickCaption(
        wmHelper: WindowManagerStateHelper,
        device: UiDevice,
        displayId: Int = DEFAULT_DISPLAY
    ) {
        val caption = checkNotNull(getCaptionForTheApp(wmHelper, device)) {
           "Unable to find caption"
        }
        caption.click()
        wmHelper
            .StateSyncBuilder()
            .withAppTransitionIdle(displayId)
            .withTopVisibleApp(innerHelper)
            .waitForAndVerify()
    }

    private fun getDesktopAppViewByRes(viewResId: String): UiObject2 =
        DeviceHelpers.waitForObj(By.res(SYSTEMUI_PACKAGE, viewResId), TIMEOUT)

    private fun getDisplayRect(wmHelper: WindowManagerStateHelper): Rect =
        wmHelper.currentState.wmState.getDefaultDisplay()?.displayRect
            ?: throw IllegalStateException("Default display is null")


    /** Wait for transition to full screen to finish. */
    fun waitForTransitionToFullscreen(wmHelper: WindowManagerStateHelper) {
        wmHelper
            .StateSyncBuilder()
            .withFullScreenApp(innerHelper)
            .withAppTransitionIdle()
            .waitForAndVerify()
    }

    /** Wait for transition to freeform to finish. */
    fun waitForTransitionToFreeform(wmHelper: WindowManagerStateHelper) {
        wmHelper
            .StateSyncBuilder()
            .withWindowSurfaceAppeared(innerHelper)
            .withFreeformApp(innerHelper)
            .withAppTransitionIdle()
            .waitForAndVerify()
    }

    private fun getWindowInsets(context: Context, typeMask: Int): Insets {
        val wm: WindowManager = context.getSystemService(WindowManager::class.java)
            ?: error("Unable to connect to WindowManager service")
        val metricInsets = wm.currentWindowMetrics.windowInsets
        return metricInsets.getInsetsIgnoringVisibility(typeMask)
    }

    // Requirement of DesktopWindowingMode is having a minimum of 1 app in WINDOWING_MODE_FREEFORM.
    private fun isInDesktopWindowingMode(wmHelper: WindowManagerStateHelper) =
        wmHelper.getWindow(innerHelper)?.windowingMode == WINDOWING_MODE_FREEFORM

    private fun isInFullscreenMode(wmHelper: WindowManagerStateHelper) =
        wmHelper.getWindow(innerHelper)?.windowingMode == WINDOWING_MODE_FULLSCREEN

    private fun isAnyDesktopWindowVisible(wmHelper: WindowManagerStateHelper) =
        wmHelper.currentState.wmState.hasFreeformWindow()

    private fun areSnapWindowRegionsMatchingWithinThreshold(
        surfaceRegion: Region, expectedRegion: Region, toLeft: Boolean
    ): Boolean {
        val surfaceBounds = surfaceRegion.bounds
        val expectedBounds = expectedRegion.bounds
        // If snapped to left, right bounds will be cut off by the center divider.
        // Else if snapped to right, the left bounds will be cut off.
        val leftSideMatching: Boolean
        val rightSideMatching: Boolean
        if (toLeft) {
            leftSideMatching = surfaceBounds.left == expectedBounds.left
            rightSideMatching =
                abs(surfaceBounds.right - expectedBounds.right) <=
                        surfaceBounds.right * SNAP_WINDOW_MAX_THRESHOLD_DIFF
        } else {
            leftSideMatching =
                abs(surfaceBounds.left - expectedBounds.left) <=
                        surfaceBounds.left * SNAP_WINDOW_MAX_THRESHOLD_DIFF
            rightSideMatching = surfaceBounds.right == expectedBounds.right
        }

        return surfaceBounds.top == expectedBounds.top &&
                surfaceBounds.bottom == expectedBounds.bottom &&
                leftSideMatching &&
                rightSideMatching
    }

    enum class MaximizeDesktopAppTrigger {
        MAXIMIZE_MENU,
        DOUBLE_TAP_APP_HEADER,
        KEYBOARD_SHORTCUT,
        MAXIMIZE_BUTTON_IN_MENU
    }

    enum class WindowDraggingDirection {
        CENTER
    }

    enum class SplitDirection {
        LEFT,
        RIGHT
    }

    private companion object {
        val TIMEOUT: Duration = Duration.ofSeconds(3)
        const val SNAP_RESIZE_DRAG_INSET: Int = 5 // inset to avoid dragging to display edge
        const val CAPTION: String = "desktop_mode_caption"
        const val MAXIMIZE_BUTTON_VIEW: String = "maximize_button_view"
        const val MAXIMIZE_MENU: String = "maximize_menu"
        const val CLOSE_BUTTON: String = "close_window"
        const val PILL_CONTAINER: String = "windowing_pill"
        const val DESKTOP_MODE_BUTTON: String = "desktop_button"
        const val SNAP_LEFT_BUTTON: String = "maximize_menu_snap_left_button"
        const val SNAP_RIGHT_BUTTON: String = "maximize_menu_snap_right_button"
        const val MAXIMIZE_BUTTON_IN_MENU: String = "maximize_menu_size_toggle_button"
        const val MINIMIZE_BUTTON_VIEW: String = "minimize_window"
        const val HEADER_EMPTY_VIEW: String = "caption_handle"
        const val OPEN_MENU_BUTTON: String = "open_menu_button"
        const val RESTART_BUTTON: String = "handle_menu_restart_button"
        const val RESTART_DIALOG_RESTART_BUTTON: String = "letterbox_restart_dialog_restart_button"
        const val FULL_SCREEN_BUTTON: String = "fullscreen_button"
        const val SPLIT_SCREEN_BUTTON: String = "split_screen_button"
        const val IMMERSIVE_BUTTON_IN_MENU: String = "maximize_menu_immersive_toggle_button"
        val caption: BySelector
            get() = By.res(SYSTEMUI_PACKAGE, CAPTION)

        // In DesktopMode, window snap can be done with just a single window. In this case, the
        // divider tiling between left and right window won't be shown, and hence its states are not
        // obtainable in test.
        // As the test should just focus on ensuring window goes to one side of the screen, an
        // acceptable approach is to ensure snapped window still fills > 95% of either side of the
        // screen.
        const val SNAP_WINDOW_MAX_THRESHOLD_DIFF = 0.05
    }
}
