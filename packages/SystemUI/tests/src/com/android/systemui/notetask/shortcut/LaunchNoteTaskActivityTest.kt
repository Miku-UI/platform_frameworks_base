/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.systemui.notetask.shortcut

import android.app.Activity
import android.app.WindowConfiguration
import android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM
import android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN
import android.app.WindowConfiguration.WINDOWING_MODE_MULTI_WINDOW
import android.app.WindowConfiguration.WINDOWING_MODE_PINNED
import android.content.Intent
import android.platform.test.flag.junit.FlagsParameterization
import androidx.core.app.AppComponentFactory
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.rules.AppComponentFactoryRule
import androidx.test.filters.SmallTest
import com.android.dx.mockito.inline.extended.ExtendedMockito.verify
import com.android.systemui.Flags
import com.android.systemui.SysuiTestCase
import com.android.systemui.notetask.NoteTaskController
import com.android.systemui.notetask.NoteTaskEntryPoint
import com.android.systemui.notetask.NoteTaskEntryPoint.WIDGET_PICKER_SHORTCUT
import com.android.systemui.notetask.NoteTaskEntryPoint.WIDGET_PICKER_SHORTCUT_LAUNCH_IN_ACTIVITY
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import platform.test.runner.parameterized.ParameterizedAndroidJunit4
import platform.test.runner.parameterized.Parameters

@RunWith(ParameterizedAndroidJunit4::class)
@SmallTest
class LaunchNoteTaskActivityTest(private val testCase: TestCase) : SysuiTestCase() {

    init {
        mSetFlagsRule.setFlagsParameterization(
            FlagsParameterization(
                mapOf(
                    Flags.FLAG_FIX_NOTES_ROLE_IN_FREEFORM to
                        testCase.isFixNotesRoleInFreeformEnabled
                )
            )
        )
    }

    private val fakeWindowModeFetcher =
        object : WindowingModeFetcher {
            override fun getWindowingMode(activity: Activity): Int = testCase.windowingMode

            override fun isInMultiWindowMode(activity: Activity): Boolean =
                testCase.isInMultiWindowMode()
        }

    private val noteTaskController: NoteTaskController = mock<NoteTaskController>()

    @get:Rule(order = 1)
    val appFactoryRule =
        AppComponentFactoryRule(
            object : AppComponentFactory() {

                override fun instantiateActivityCompat(
                    cl: ClassLoader,
                    className: String,
                    intent: Intent?,
                ): Activity =
                    if (className == LaunchNoteTaskActivity::class.java.name) {
                        LaunchNoteTaskActivity(noteTaskController, fakeWindowModeFetcher)
                    } else {
                        super.instantiateActivity(cl, className, intent)
                    }
            }
        )

    @get:Rule(order = 2)
    val activityScenarioRule = ActivityScenarioRule(LaunchNoteTaskActivity::class.java)

    @Test
    fun startActivityOnNonWorkProfileUser_shouldLaunchNoteTask() {
        verify(noteTaskController).showNoteTaskAsUser(eq(testCase.resultEntryPoint), any())
    }

    data class TestCase(
        val isFixNotesRoleInFreeformEnabled: Boolean,
        val windowingMode: Int,
        val resultEntryPoint: NoteTaskEntryPoint,
    ) {
        fun isInMultiWindowMode(): Boolean = WindowConfiguration.inMultiWindowMode(windowingMode)
    }

    companion object {
        @Parameters(name = "{0}")
        @JvmStatic
        fun data(): Collection<TestCase> =
            listOf(
                TestCase(
                    isFixNotesRoleInFreeformEnabled = true,
                    windowingMode = WINDOWING_MODE_FULLSCREEN,
                    resultEntryPoint = WIDGET_PICKER_SHORTCUT,
                ),
                TestCase(
                    isFixNotesRoleInFreeformEnabled = false,
                    windowingMode = WINDOWING_MODE_FULLSCREEN,
                    resultEntryPoint = WIDGET_PICKER_SHORTCUT,
                ),
                TestCase(
                    isFixNotesRoleInFreeformEnabled = true,
                    windowingMode = WINDOWING_MODE_PINNED,
                    resultEntryPoint = WIDGET_PICKER_SHORTCUT_LAUNCH_IN_ACTIVITY,
                ),
                TestCase(
                    isFixNotesRoleInFreeformEnabled = false,
                    windowingMode = WINDOWING_MODE_PINNED,
                    resultEntryPoint = WIDGET_PICKER_SHORTCUT_LAUNCH_IN_ACTIVITY,
                ),
                TestCase(
                    isFixNotesRoleInFreeformEnabled = true,
                    windowingMode = WINDOWING_MODE_FREEFORM,
                    resultEntryPoint = WIDGET_PICKER_SHORTCUT,
                ),
                TestCase(
                    isFixNotesRoleInFreeformEnabled = false,
                    windowingMode = WINDOWING_MODE_FREEFORM,
                    resultEntryPoint = WIDGET_PICKER_SHORTCUT_LAUNCH_IN_ACTIVITY,
                ),
                TestCase(
                    isFixNotesRoleInFreeformEnabled = true,
                    windowingMode = WINDOWING_MODE_MULTI_WINDOW,
                    resultEntryPoint = WIDGET_PICKER_SHORTCUT_LAUNCH_IN_ACTIVITY,
                ),
                TestCase(
                    isFixNotesRoleInFreeformEnabled = false,
                    windowingMode = WINDOWING_MODE_MULTI_WINDOW,
                    resultEntryPoint = WIDGET_PICKER_SHORTCUT_LAUNCH_IN_ACTIVITY,
                ),
            )
    }
}
