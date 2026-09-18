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

import android.tools.traces.component.IComponentNameMatcher
import com.android.wm.shell.flicker.utils.SPLIT_SCREEN_DIVIDER_COMPONENT
import org.junit.Test

/**
 * Verifies that the [testApp] opens and replaces the [previousApp] while in split-screen mode.
 *
 * This verifies:
 * - The [testApp] becomes visible at the end of the transition: [AppAnimateInTestCases]
 * - The [previousApp] is visible at the start of the transition.
 * - The [otherApp] on the other side of the split stays visible throughout the transition.
 * - The split screen divider is always visible.
 * - The focus and top app window change from [previousApp] to [testApp]:
 *   [AppReplacesPreviousAppTestCases]
 */
interface AppReplacesPreviousAppInSplitScreenTestCases : AppReplacesPreviousAppTestCases {

    val otherApp: IComponentNameMatcher

    /** Verifies the other app layer is always visible during the transition. */
    @Test
    fun otherAppIsAlwaysVisible() {
        layersTraceSubject.isVisible(otherApp).forAllEntries()
    }

    /** Verifies the previous app layer is visible at the start of transition. */
    @Test
    fun previousAppIsVisibleAtStart() {
        layerTraceEntrySubjectAtStart.isVisible(previousApp)
    }

    /** Verifies the test app layer is visible at the end of transition. */
    @Test
    fun testAppIsVisibleAtEnd() {
        layerTraceEntrySubjectAtEnd.isVisible(testApp)
    }

    /** Verifies the split divider layer is always visible. */
    @Test
    fun splitDividerIsAlwaysVisible() {
        layersTraceSubject.isVisible(SPLIT_SCREEN_DIVIDER_COMPONENT).forAllEntries()
    }
}
