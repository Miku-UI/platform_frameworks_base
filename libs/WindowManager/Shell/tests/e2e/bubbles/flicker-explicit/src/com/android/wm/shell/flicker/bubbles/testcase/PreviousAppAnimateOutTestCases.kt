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

import android.tools.traces.component.ComponentNameMatcher.Companion.LAUNCHER
import android.tools.traces.component.IComponentNameMatcher
import com.android.wm.shell.flicker.bubbles.utils.BubbleFlickerSubjects
import com.android.wm.shell.flicker.bubbles.utils.FlickerAssertionHelper.assertLayerAlphaChangeConsistently
import com.android.wm.shell.flicker.bubbles.utils.FlickerAssertionHelper.assertLayerMoveInSingleDirection
import com.android.wm.shell.flicker.bubbles.utils.FlickerAssertionHelper.assertLayerResizeConsistently
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Verifies that the [previousApp] (default to be [LAUNCHER]) animates out from visible.
 *
 * This verifies:
 * - The [previousApp] window/layer becomes invisible from visible (optional for launcher. See
 *   [shouldAssertPreviousBecomesInvisible]).
 * - The [previousApp] layer fades out (optional if the spec wants to keep alpha unchanged).
 * - The [previousApp] layer animates out only one direction (optional if the spec wants to keep
 *   bounds unchanged).
 */
interface PreviousAppAnimateOutTestCases : BubbleFlickerSubjects {

    val previousApp: IComponentNameMatcher
        get() = LAUNCHER

    /**
     * Whether or not to assert the [previousApp] becomes invisible. By default, skip this assertion
     * if it is [LAUNCHER] since it can stay visible in background.
     */
    fun shouldAssertPreviousBecomesInvisible() = previousApp != LAUNCHER

    /** Verifies the [previousApp] window becomes invisible from visible. */
    @Test
    fun previousAppWindowBecomesInvisible() {
        assumeTrue(shouldAssertPreviousBecomesInvisible())

        wmTraceSubject
            .isAppWindowVisible(previousApp)
            .then()
            .isAppWindowInvisible(previousApp)
            .forAllEntries()
    }

    /** Verifies the [previousApp] layer becomes invisible from visible. */
    @Test
    fun previousAppLayerBecomesInvisible() {
        assumeTrue(shouldAssertPreviousBecomesInvisible())

        layersTraceSubject.isVisible(previousApp).then().isInvisible(previousApp).forAllEntries()
    }

    /** Verifies the [previousApp] window is invisible at the end of the transition. */
    @Test
    fun previousAppWindowIsInvisibleAtEnd() {
        assumeTrue(shouldAssertPreviousBecomesInvisible())

        wmStateSubjectAtEnd.isAppWindowInvisible(previousApp)
    }

    /** Verifies the [previousApp] layer is invisible at the end of the transition. */
    @Test
    fun previousAppLayerIsInvisibleAtEnd() {
        assumeTrue(shouldAssertPreviousBecomesInvisible())

        // PreviousApp may be gone if it's in dismissed state.
        layerTraceEntrySubjectAtEnd.isInvisible(previousApp)
    }

    /**
     * Verifies the [previousApp] layer's alpha value only decreases (optional if the spec wants to
     * keep alpha unchanged).
     */
    @Test
    fun previousAppLayerFadeOut() {
        assertLayerAlphaChangeConsistently(
            layersTraceSubject = layersTraceSubject,
            layerMatcher = previousApp,
            isFadeIn = false,
        )
    }

    /** Verifies that [previousApp] resizes consistently. */
    @Test
    fun previousAppLayerResizeConsistently() {
        assertLayerResizeConsistently(
            layersTraceSubject = layersTraceSubject,
            layerMatcher = previousApp,
        )
    }

    /** Verifies that [previousApp] only moves in one direction (no jumping around) when visible. */
    @Test
    fun previousAppLayerMoveInSingleDirection() {
        assertLayerMoveInSingleDirection(
            layersTraceSubject = layersTraceSubject,
            layerMatcher = previousApp,
        )
    }
}
