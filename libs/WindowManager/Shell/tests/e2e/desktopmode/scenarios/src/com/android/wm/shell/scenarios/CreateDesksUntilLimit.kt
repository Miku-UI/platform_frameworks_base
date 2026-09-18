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

package com.android.wm.shell.scenarios

import android.app.Instrumentation
import android.tools.traces.parsers.WindowManagerStateHelper
import androidx.test.platform.app.InstrumentationRegistry
import com.android.launcher3.tapl.LauncherInstrumentation
import com.android.server.wm.flicker.helpers.DesktopModeAppHelper
import com.android.server.wm.flicker.helpers.SimpleAppHelper
import com.android.wm.shell.Utils
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Test

@Ignore("Base Test Class")
abstract class CreateDesksUntilLimit : TestScenarioBase() {

    private val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
    private val tapl = LauncherInstrumentation()
    private val wmHelper = WindowManagerStateHelper(instrumentation)
    private val testApp = DesktopModeAppHelper(SimpleAppHelper(instrumentation))

    @Before
    fun setup() {
        testApp.launchViaIntent(wmHelper)
    }

    @Test
    open fun createDeskUntilLimit() {
        var overview = Utils.switchToOverview(tapl)
        // All tasks / desks were removed from overview during set up, but it's still possible that
        // one desk is there when desktop-first displays force-recreate a default desk. So account
        // for the possibility of 0 or 1 desks existing here before creating up to the limit.
        val numOfDesks = overview.desktopTasksCount
        val remainingDesks = DESK_LIMIT - numOfDesks
        repeat(remainingDesks) { overview = overview.createDeskViaClickAddDesktopButton() }
        // Fling to the 1st (right-most) task to check the "add desk" button is now gone.
        overview.flingToFirstTask()
    }

    @After
    fun teardown() {
        testApp.exit(wmHelper)
    }

    private companion object {
        private const val DESK_LIMIT = 4
    }
}
