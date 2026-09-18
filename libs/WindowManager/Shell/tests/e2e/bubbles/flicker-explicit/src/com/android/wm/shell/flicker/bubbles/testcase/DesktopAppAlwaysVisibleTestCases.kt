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

package com.android.wm.shell.flicker.bubbles.testcase

import android.tools.Rotation
import android.tools.helpers.WindowUtils
import android.tools.traces.component.ComponentNameMatcher.Companion.DESKTOP_WALLPAPER_ACTIVITY
import android.tools.traces.component.IComponentNameMatcher
import com.android.wm.shell.flicker.bubbles.utils.BubbleFlickerSubjects
import org.junit.Test

/**
 * Test cases to verify that the desktop app remains visible throughout the transition.
 *
 * This is used in scenarios where another UI (like an expanded bubble) appears on top of the
 * desktop, but the desktop app itself should remain visible in the background (i.e. not become
 * invisible or fully occluded by other non-transient surfaces).
 *
 * This interface requires [desktopApp] to be implemented, which should be set to the desktop app
 * being monitored.
 */
interface DesktopAppAlwaysVisibleTestCases : BubbleFlickerSubjects {

    /**
     * The desktop app under test.
     *
     * This app is expected to remain visible. In many test scenarios, this is the app that was on
     * top before the transition began.
     */
    val desktopApp: IComponentNameMatcher

    /**
     * The display rotation at the end of the transition.
     *
     * This is used to calculate the correct display bounds for assertions.
     */
    val endRotation: Rotation
        get() = Rotation.ROTATION_0

    /** Verifies the desktop app's window is always visible in the WM trace. */
    @Test
    fun desktopAppWindowIsAlwaysVisible() {
        wmTraceSubject.isAppWindowVisible(desktopApp).forAllEntries()
    }

    /** Verifies the desktop app's layer is always visible in the layers trace. */
    @Test
    fun desktopAppLayerIsAlwaysVisible() {
        layersTraceSubject.isVisible(desktopApp).forAllEntries()
    }

    /** Verifies the desktop wallpaper window is visible throughout the transition. */
    @Test
    fun desktopWallpaperWindowIsAlwaysVisible() {
        wmTraceSubject.isAppWindowVisible(DESKTOP_WALLPAPER_ACTIVITY).forAllEntries()
    }

    /**
     * Verifies the desktop app's window is inside the display bounds at the end of the transition.
     *
     * This ensures the app window is not positioned partially or fully off-screen.
     */
    @Test
    fun desktopAppWindowInsideDisplayBoundsAtEnd() {
        wmStateSubjectAtEnd
            .visibleRegion(desktopApp)
            .coversAtMost(WindowUtils.getDisplayBounds(endRotation))
    }
}
