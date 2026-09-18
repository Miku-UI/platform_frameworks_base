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

package com.android.wm.shell.flicker.bubbles

import android.platform.test.annotations.Presubmit
import android.platform.test.annotations.RequiresFlagsDisabled
import android.platform.test.annotations.RequiresFlagsEnabled
import android.tools.NavBar
import android.tools.device.apphelpers.StandardAppHelper
import android.tools.traces.component.IComponentNameMatcher
import androidx.test.filters.RequiresDevice
import com.android.server.wm.flicker.helpers.ShowWhenLockedAppHelper
import com.android.wm.shell.Flags
import com.android.wm.shell.Utils.testSetupRule
import com.android.wm.shell.flicker.bubbles.testcase.RelaunchVisibleAppToBubbleTestCases
import com.android.wm.shell.flicker.bubbles.testcase.RelaunchVisibleSplitAppToBubbleTestCases
import com.android.wm.shell.flicker.bubbles.utils.AssumptionRule
import com.android.wm.shell.flicker.bubbles.utils.FlickerAssertionHelper.moveInSingleDirection
import com.android.wm.shell.flicker.bubbles.utils.FlickerAssertionHelper.resizeConsistently
import com.android.wm.shell.flicker.bubbles.utils.RunOncePerParameterRule
import com.android.wm.shell.flicker.bubbles.utils.TransitionSnapshotMatcher
import com.android.wm.shell.flicker.bubbles.utils.VisibleSplitToBubbleTestScenario
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

/**
 * Test entering bubble for an app that was previously open in split-screen and visible via
 * trampoline task. Bubble is launched via clicking bubble menu from the task bar.
 *
 * To run this test:
 * ```
 * atest WMShellExplicitFlickerTestsBubbles:RelaunchVisibleSplitAppToBubbleTrampolineTest
 * ```
 *
 * Pre-steps:
 * ```
 *     Start the app in split-screen. One starts via trampoline app.
 *     Click one of split apps to grant focus.
 * ```
 *
 * Actions:
 * ```
 *     Click the bubble menu in task bar to launch the app into a bubble.
 * ```
 *
 * Verified tests:
 * - [BubbleFlickerTrampolineTestBase]
 * - [RelaunchVisibleAppToBubbleTestCases]
 */
// TODO(b/479182156) Remove this when bubbling is supported in desktop mode.
@RequiresFlagsDisabled(Flags.FLAG_DISABLE_BUBBLE_ANYTHING_DESKTOP_WINDOWING)
@RequiresFlagsEnabled(Flags.FLAG_ENABLE_CREATE_ANY_BUBBLE)
@RequiresDevice
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@Presubmit
@RunWith(Parameterized::class)
class RelaunchVisibleSplitAppToBubbleTrampolineTest(
    private val testScenario: VisibleSplitToBubbleTestScenario
) : BubbleFlickerTrampolineTestBase(), RelaunchVisibleSplitAppToBubbleTestCases {

    companion object {
        val testApp2: StandardAppHelper = ShowWhenLockedAppHelper(instrumentation)

        @Parameters(name = "{0}")
        @JvmStatic
        fun data(): List<VisibleSplitToBubbleTestScenario> =
            VisibleSplitToBubbleTestScenario.generateTestScenarios(
                toBubbleApp = runningApp,
                toFullscreenApp = testApp2,
                trampolineApp = trampolineApp,
            )

        val TRAMPOLINE_SNAPSHOT = TransitionSnapshotMatcher(trampolineApp.packageName)
    }

    @get:Rule(order = 1)
    val assumptionRule =
        AssumptionRule(
            condition = { tapl.isTablet },
            message = "The bubble bar is only available on large screen devices",
        )

    @get:Rule(order = 2)
    val setUpRule =
        RunOncePerParameterRule(
            testClass = this::class,
            wrappedRule = testSetupRule(NavBar.MODE_GESTURAL).around(testScenario.rule),
            params = arrayOf(testScenario),
        )

    override val traceDataReader
        get() = testScenario.rule.reader

    override val toFullscreenApp: IComponentNameMatcher
        get() = testApp2

    /** The trampoline activity is expected to finish itself. */
    @Test
    fun trampolineAppWindowIsGoneAtEnd() {
        wmStateSubjectAtEnd.notContainsAppWindow(trampolineApp)
    }

    /** The trampoline activity is expected to finish itself. */
    @Test
    fun trampolineAppLayerIsInvisibleAtEnd() {
        layerTraceEntrySubjectAtEnd.isInvisible(trampolineApp)
    }

    @Test
    override fun appOrTransitionSnapshotLayerIsAlwaysVisible() {
        layersTraceSubject.isVisible(testApp.or(TRAMPOLINE_SNAPSHOT)).forAllEntries()
    }

    @Test
    override fun appLayerResizeConsistently() {
        layersTraceSubject.resizeConsistently(
            TRAMPOLINE_SNAPSHOT,
            // the value may be changed slightly when starting scaling and shrinking.
            threshold = 1,
        )
    }

    @Test
    override fun appLayerMoveInSingleDirection() {
        layersTraceSubject.moveInSingleDirection(
            TRAMPOLINE_SNAPSHOT,
            // the value may be changed slightly when starting scaling and shrinking.
            threshold = 1,
        )
    }
}
