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

import org.junit.Test

/**
 * Verifies that the [testApp] opens in fullscreen from invisible at the end of the transition.
 *
 * This verifies:
 * - The [previousApp] (launcher) is replaced by [testApp]: [AppReplacesPreviousAppTestCases]
 * - The [testApp] window stays in fullscreen as soon as it becomes visible.
 * - The [testApp] layer covers the entire screen at the end of the trace.
 */
interface AppOpenInFullscreenTestCases : AppReplacesPreviousAppTestCases {

    /** Launcher should be occluded by the fullscreen [testApp]. */
    override fun shouldAssertPreviousBecomesInvisible() = true

    /** Verifies the [testApp] window is fullscreen at the end of the transition. */
    @Test
    fun appWindowIsFullscreenAtEnd() {
        wmStateSubjectAtEnd.isFullscreen(testApp)
    }

    /** Verifies the [testApp] layer is visible and covers the entire screen at the end. */
    @Test
    fun appLayerCoversFullScreenAtEnd() {
        val displayBounds =
            layerTraceEntrySubjectAtEnd.entry.physicalDisplayBounds
                ?: error("Missing physical display bounds")
        layerTraceEntrySubjectAtEnd.visibleRegion(testApp).coversExactly(displayBounds)
    }

    /** Verifies the [testApp] window becomes visible and fullscreen at the same time. */
    @Test
    override fun appWindowBecomesVisible() {
        wmTraceSubject
            .isAppWindowInvisible(testApp)
            .then()
            .isAppWindowVisible(testApp)
            .isFullscreen(testApp)
            .forAllEntries()
    }
}
