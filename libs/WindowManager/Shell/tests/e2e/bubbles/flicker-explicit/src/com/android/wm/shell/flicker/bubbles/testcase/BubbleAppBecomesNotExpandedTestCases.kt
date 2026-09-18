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

import android.tools.traces.component.ComponentNameMatcher.Companion.BUBBLE
import android.tools.traces.component.ComponentNameMatcher.Companion.LAUNCHER
import android.tools.traces.component.IComponentNameMatcher
import org.junit.Test

/**
 * Verifies that the [testApp] becomes invisible from expanded bubble state.
 *
 * This verifies:
 * - The [testApp] becomes invisible: [AppAnimateOutTestCases]
 * - The focus changed from [testApp] to [previousApp] (default to be [LAUNCHER]).
 * - The top app changed from [previousApp] to [testApp].
 * - The [testApp] has rounded corner at the start of transition.
 * - The [BUBBLE] covers the [testApp] at the end of transition.
 *
 * The test cases to verify [testApp] becomes invisible and [previousApp] replaces [testApp] to be
 * top focused because the bubble app goes to collapsed or dismissed state.
 */
interface BubbleAppBecomesNotExpandedTestCases : AppAnimateOutTestCases {

    val previousApp: IComponentNameMatcher
        get() = LAUNCHER

    /** Verifies the focus changed from bubbled [testApp] to [previousApp]. */
    @Test
    fun focusChanges() {
        focusEventSubject.focusChanges(testApp.toWindowName(), previousApp.toWindowName())
    }

    /** Verifies the [previousApp] replaces the bubbled [testApp] to be the top window. */
    @Test
    fun previousAppWindowReplacesTestAppAsTopWindow() {
        wmTraceSubject
            .isAppWindowOnTop(testApp)
            .then()
            .isAppWindowOnTop(previousApp)
            .forAllEntries()
    }

    /** Verifies the [testApp] window has rounded corner at the start of the transition. */
    @Test
    fun appWindowHasRoundedCornerAtStart() {
        layerTraceEntrySubjectAtStart.hasRoundedCorners(testApp)
    }
}
