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
import com.android.wm.shell.flicker.bubbles.utils.FlickerAssertionHelper.hasVisibleChild
import com.android.wm.shell.flicker.bubbles.utils.FlickerAssertionHelper.isBubbled
import com.android.wm.shell.flicker.bubbles.utils.TaskLayerMatcher
import org.junit.Test

/** Verifies that trampoline and running activity layers are always in Bubble when visible. */
interface TaskTrampolineBecomesExpandedTestCases : BubbleFlickerSubjects {

    /** Verifies the trampoline activity layer must be bubbled when visible. */
    @Test
    fun trampolineActivityInBubbleLayer() {
        val trampolineTaskMatcher = TaskLayerMatcher(BubbleFlickerTrampolineTestBase.trampolineApp)
        val visibleTrampolineTaskList =
            layersTraceSubject.layers { trampolineTaskMatcher.layerMatchesAnyOf(it) }
        visibleTrampolineTaskList.forEach {
            if (it.layer.hasVisibleChild()) {
                it.check { "${it.name} must be bubbled" }.that(it.layer.isBubbled()).isEqual(true)
            }
        }
    }

    /** Verifies the running activity layer must be bubbled when visible. */
    @Test
    fun runningActivityInBubbleLayer() {
        val runningTaskMatcher = TaskLayerMatcher(BubbleFlickerTrampolineTestBase.runningApp)
        val visibleRunningTaskList =
            layersTraceSubject.layers { runningTaskMatcher.layerMatchesAnyOf(it) }
        visibleRunningTaskList.forEach {
            if (it.layer.hasVisibleChild()) {
                it.check { "${it.name} must be bubbled" }.that(it.layer.isBubbled()).isEqual(true)
            }
        }
    }

    /** The trampoline activity is expected to finish itself. */
    @Test
    fun trampolineAppWindowIsGoneAtEnd() {
        wmStateSubjectAtEnd.notContainsAppWindow(BubbleFlickerTrampolineTestBase.trampolineApp)
    }

    /** The trampoline activity is expected to finish itself. */
    @Test
    fun trampolineAppLayerIsInvisibleAtEnd() {
        layerTraceEntrySubjectAtEnd.isInvisible(BubbleFlickerTrampolineTestBase.trampolineApp)
    }
}
