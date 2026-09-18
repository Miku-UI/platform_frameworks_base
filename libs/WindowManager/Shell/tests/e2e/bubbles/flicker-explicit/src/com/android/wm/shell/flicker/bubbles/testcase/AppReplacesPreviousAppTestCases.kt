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

import android.tools.traces.component.ComponentNameMatcher
import org.junit.Test

/**
 * Verifies that the [testApp] opens on top of the [previousApp] at the end of the transition.
 *
 * This verifies:
 * - The [testApp] becomes visible: [AppAnimateInTestCases]
 * - The [previousApp] becomes invisible: [PreviousAppAnimateOutTestCases] (optional if
 *   [previousApp] is launcher. See [shouldAssertPreviousBecomesInvisible])
 * - The focus changed from [previousApp] to [testApp].
 * - The top app changed from [previousApp] to [testApp].
 */
interface AppReplacesPreviousAppTestCases : AppAnimateInTestCases, PreviousAppAnimateOutTestCases {

    /** Verifies the focus changed from [previousApp] to [testApp]. */
    @Test
    fun focusChanges() {
        focusEventSubject.focusChanges(previousApp.toWindowName(), testApp.toWindowName())
    }

    /** Verifies the bubbled [testApp] replaces [previousApp] to be the top window. */
    @Test
    fun appWindowReplacesPreviousAppAsTopWindow() {
        wmTraceSubject
            .isAppWindowOnTop(previousApp)
            .then()
            .isAppWindowOnTop(
                ComponentNameMatcher.SNAPSHOT.or(ComponentNameMatcher.SPLASH_SCREEN),
                isOptional = true,
            )
            .then()
            .isAppWindowOnTop(testApp)
            .forAllEntries()
    }
}
