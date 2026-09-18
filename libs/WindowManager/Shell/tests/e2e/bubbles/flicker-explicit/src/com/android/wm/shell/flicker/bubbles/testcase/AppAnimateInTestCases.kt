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

import com.android.wm.shell.flicker.bubbles.utils.BubbleFlickerSubjects
import com.android.wm.shell.flicker.bubbles.utils.FlickerAssertionHelper.assertLayerAlphaChangeConsistently
import com.android.wm.shell.flicker.bubbles.utils.FlickerAssertionHelper.assertLayerMoveInSingleDirection
import com.android.wm.shell.flicker.bubbles.utils.FlickerAssertionHelper.assertLayerResizeConsistently
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Verifies that the [testApp] animates in from invisible.
 *
 * This verifies:
 * - The [testApp] window/layer becomes visible from invisible.
 * - The [testApp] layer fades in (optional if the spec wants to keep alpha unchanged).
 * - The [testApp] layer animates in only one direction (optional if the spec wants to keep bounds
 *   unchanged).
 */
interface AppAnimateInTestCases : BubbleFlickerSubjects {

    /** Verifies the [testApp] window becomes visible from invisible. */
    @Test
    fun appWindowBecomesVisible() {
        wmTraceSubject
            .isAppWindowInvisible(testApp)
            .then()
            .isAppWindowVisible(testApp)
            .forAllEntries()
    }

    /** Verifies the [testApp] layer becomes visible from invisible. */
    @Test
    fun appLayerBecomesVisible() {
        layersTraceSubject.isInvisible(testApp).then().isVisible(testApp).forAllEntries()
    }

    /** Verifies the [testApp] window is visible at the end of transition. */
    @Test
    fun appWindowIsVisibleAtEnd() {
        wmStateSubjectAtEnd.isAppWindowVisible(testApp)
    }

    /** Verifies the [testApp] layer is visible at the end of transition. */
    @Test
    fun appLayerIsVisibleAtEnd() {
        layerTraceEntrySubjectAtEnd.isVisible(testApp)
    }

    /**
     * Verifies the [testApp] layer's alpha value only increases (optional if the spec wants to keep
     * alpha unchanged).
     */
    @Test
    fun appLayerFadeIn() {
        assertLayerAlphaChangeConsistently(
            layersTraceSubject = layersTraceSubject,
            layerMatcher = testApp,
            isFadeIn = true,
        )
    }

    /** Verifies that [testApp] resizes consistently. */
    @Test
    fun appLayerResizeConsistently() {
        // On phone, the Bubble expand animation goes to right, and then back left a little, which
        // may shrink a little.
        assumeTrue(isTablet)
        assertLayerResizeConsistently(
            layersTraceSubject = layersTraceSubject,
            layerMatcher = testApp,
        )
    }

    /** Verifies that [testApp] only moves in one direction (no jumping around) when visible. */
    @Test
    fun appLayerMoveInSingleDirection() {
        // On phone, the Bubble expand animation goes to right, and then back left a little.
        assumeTrue(isTablet)
        assertLayerMoveInSingleDirection(
            layersTraceSubject = layersTraceSubject,
            layerMatcher = testApp,
        )
    }
}
