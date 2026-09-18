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

import androidx.test.filters.FlakyTest
import com.android.wm.shell.flicker.bubbles.utils.FlickerAssertionHelper.isBubbled
import com.android.wm.shell.flicker.bubbles.utils.FlickerAssertionHelper.isNotBubbled
import com.android.wm.shell.flicker.bubbles.utils.FlickerAssertionHelper.moveInSingleDirection
import com.android.wm.shell.flicker.bubbles.utils.FlickerAssertionHelper.resizeConsistently
import com.android.wm.shell.flicker.bubbles.utils.TransitionSnapshotMatcher
import org.junit.Test

/**
 * Test cases for verifying that a visible app in either multi-task or fullscreen mode can be
 * relaunched into a bubble. This verifies:
 * - [BubbleAppShowsAtEndTestCases]
 * - [BubbleBecomesVisibleTestCases]
 * - The app window and layer remain visible throughout the transition.
 * - The app's bounds change during the transition.
 */
interface RelaunchVisibleAppToBubbleTestCases :
    BubbleAppShowsAtEndTestCases, BubbleBecomesVisibleTestCases {

    /** Verifies the app window is always visible during the transition. */
    @Test
    fun appWindowIsAlwaysVisible() {
        wmTraceSubject.isAppWindowVisible(testApp).forAllEntries()
    }

    /** Verifies the app layer is visible at the start and end of the transition. */
    @FlakyTest(bugId = 472629753)
    @Test
    fun appOrTransitionSnapshotLayerIsAlwaysVisible() {
        layersTraceSubject.isVisible(testApp.or(TransitionSnapshotMatcher(testApp))).forAllEntries()
    }

    /** Verifies that [testApp] resizes consistently. */
    @Test
    fun appLayerResizeConsistently() {
        layersTraceSubject.resizeConsistently(
            TransitionSnapshotMatcher(testApp),
            // the value may be changed slightly when starting scaling and shrinking.
            threshold = 1,
        )
    }

    /** Verifies that [testApp] only moves in one direction (no jumping around) when visible. */
    @Test
    fun appLayerMoveInSingleDirection() {
        layersTraceSubject.moveInSingleDirection(
            TransitionSnapshotMatcher(testApp),
            // the value may be changed slightly when starting scaling and shrinking.
            threshold = 1,
        )
    }

    /** Verifies the app layer becomes bubbled during the transition. */
    @Test
    fun appLayerBecomesBubbled() {
        layersTraceSubject.isNotBubbled(testApp).then().isBubbled(testApp).forAllEntries()
    }
}
