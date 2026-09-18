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

package com.android.wm.shell.flicker.bubbles.testcase

import android.graphics.Region
import android.tools.helpers.WindowUtils
import android.tools.traces.component.ComponentNameMatcher.Companion.LAUNCHER
import android.tools.traces.component.IComponentNameMatcher
import androidx.test.filters.FlakyTest
import com.android.wm.shell.flicker.bubbles.utils.FlickerAssertionHelper.moveInSingleDirection
import com.android.wm.shell.flicker.bubbles.utils.FlickerAssertionHelper.resizeConsistently
import com.android.wm.shell.flicker.bubbles.utils.TransitionSnapshotMatcher
import org.junit.Test

/**
 * Test cases for verifying that a visible app in split-screen mode can be relaunched into a bubble.
 *
 * This verifies:
 * - [RelaunchVisibleAppToBubbleTestCases]
 * - The [toFullscreenApp] (the other split app) becomes fullscreen and always visible.
 * - The [LAUNCHER] remains invisible.
 */
interface RelaunchVisibleSplitAppToBubbleTestCases : RelaunchVisibleAppToBubbleTestCases {

    /**
     * The app that will transition to fullscreen.
     *
     * This app is expected to be visible and in split-screen at the start of the transition.
     */
    val toFullscreenApp: IComponentNameMatcher

    /** Verifies the [toFullscreenApp] window is always visible. */
    @Test
    fun toFullscreenAppWindowIsAlwaysVisible() {
        wmTraceSubject.isAppWindowVisible(toFullscreenApp).forAllEntries()
    }

    /** Verifies the [toFullscreenApp] layer is always visible. */
    @Test
    fun toFullscreenAppOrTransitionSnapshotLayerIsAlwaysVisible() {
        layersTraceSubject
            .isVisible(toFullscreenApp.or(TransitionSnapshotMatcher(toFullscreenApp)))
            .forAllEntries()
    }

    /** Verifies the [toFullscreenApp] window expands to fullscreen. */
    @Test
    fun toFullscreenAppWindowBecomesFullscreen() {
        val displayBounds = WindowUtils.displayBounds
        wmTraceSubject
            .visibleRegion(toFullscreenApp)
            .isStrictlySmallerThan(Region(displayBounds))
            .then()
            .coversExactly(displayBounds)
    }

    /** Verifies the [toFullscreenApp] layer expands to fullscreen. */
    @FlakyTest(bugId = 472381509)
    @Test
    fun toFullscreenAppLayerBecomesFullscreen() {
        val displayBounds = WindowUtils.displayBounds
        layersTraceSubject
            .visibleRegion(toFullscreenApp)
            .isStrictlySmallerThan(Region(displayBounds))
            .then()
            .coversExactly(displayBounds)
            .forAllEntries()
    }

    /** Verifies the launcher window is not visible. */
    @Test
    fun launcherWindowIsNotVisible() {
        wmTraceSubject.isAppWindowInvisible(LAUNCHER).forAllEntries()
    }

    /** Verifies the launcher layer is not visible. */
    @Test
    fun launcherLayerIsNotVisible() {
        layersTraceSubject.isInvisible(LAUNCHER).forAllEntries()
    }

    /** Verifies that [toFullscreenApp] expands consistently. */
    @Test
    fun toFullscreenAppLayerResizeConsistently() {
        layersTraceSubject.resizeConsistently(toFullscreenApp)
    }

    /**
     * Verifies that [toFullscreenApp] only moves in one direction (no jumping around) when visible.
     */
    @Test
    fun toFullscreenAppLayerMoveInSingleDirection() {
        layersTraceSubject.moveInSingleDirection(toFullscreenApp)
    }
}
