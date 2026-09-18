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
import org.junit.Test

/**
 * The test cases to check that the expanded Bubble is switched to another Task.
 *
 * Also verifies expanding bubble with multiple bubble scenarios
 * - [MultipleBubbleExpandBubbleAppTestCases]
 */
interface SwitchExpandedBubbleTestCases : MultipleBubbleExpandBubbleAppTestCases {

    @Test
    override fun appWindowReplacesPreviousAppAsTopWindow() {
        wmTraceSubject
            .isAppWindowOnTop(previousApp)
            .then()
            // Launcher may get focus when tapping on bubble icon.
            .isAppWindowOnTop(LAUNCHER, isOptional = true)
            .then()
            .isAppWindowOnTop(testApp)
            .forAllEntries()
    }

    /** Verifies the [testApp] window replaces [previousApp] as the visible app. */
    @Test
    fun appWindowReplacesPreviousAppAsVisibleWindow() {
        wmTraceSubject
            .isAppWindowVisible(previousApp)
            .then()
            // There may be a timing that the previousApp is hidden, but the testApp hasn't shown.
            .isAppWindowInvisible(testApp, isOptional = true)
            .then()
            .isAppWindowVisible(testApp)
            .forAllEntries()
    }

    /** Verifies the [testApp] layer replaces [previousApp] as the visible app. */
    @Test
    fun appLayerReplacePreviousAppAsVisibleLayer() {
        layersTraceSubject
            .isVisible(previousApp)
            .then()
            // There may be a timing that the previousApp is hidden, but the testApp hasn't shown.
            .isInvisible(testApp, isOptional = true)
            .then()
            .isVisible(testApp)
            .forAllEntries()
    }
}
