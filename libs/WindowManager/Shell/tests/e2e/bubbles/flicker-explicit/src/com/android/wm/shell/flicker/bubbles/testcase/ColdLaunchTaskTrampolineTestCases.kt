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

import com.android.wm.shell.flicker.bubbles.BubbleFlickerTrampolineTestBase
import com.android.wm.shell.flicker.bubbles.utils.BubbleFlickerSubjects
import com.android.wm.shell.flicker.bubbles.utils.SurfaceViewBubbleTaskSnapshotMatcher
import org.junit.Test

/** Verifies cold launching a task trampoline app into bubble. */
interface ColdLaunchTaskTrampolineTestCases : BubbleFlickerSubjects {

    /** Verifies the transition from the trampoline activity to the running activity. */
    @Test
    fun trampolineActivityColdLaunchToRunningActivity() {
        layersTraceSubject
            .skipUntilFirstAssertion()
            .isSplashScreenVisibleFor(BubbleFlickerTrampolineTestBase.trampolineApp)
            .then()
            // After the trampoline splash screen becomes invisible, it may take a few frame until
            // the running activity becomes visible. On phone, it would show the prev expanded
            // Bubble animating out snapshot SurfaceView to cover it.
            .isVisible(SurfaceViewBubbleTaskSnapshotMatcher(), isOptional = true)
            .then()
            // Check that trampoline starts the running app, running app can show a splash or not
            .isSplashScreenVisibleFor(BubbleFlickerTrampolineTestBase.runningApp, isOptional = true)
            .then()
            .isVisible(BubbleFlickerTrampolineTestBase.runningApp)
            .forAllEntries()
    }
}
