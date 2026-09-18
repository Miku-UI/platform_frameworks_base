/*
 * Copyright 2025 The Android Open Source Project
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

package com.android.server.wm.flicker.ime

import android.platform.test.annotations.Presubmit
import android.tools.device.apphelpers.StandardAppHelper
import android.tools.flicker.FlickerBuilder
import android.tools.flicker.FlickerTest
import android.tools.flicker.FlickerTestFactory
import android.tools.flicker.junit.FlickerParametersRunnerFactory
import android.tools.traces.parsers.toFlickerComponent
import androidx.test.filters.RequiresDevice
import com.android.server.wm.flicker.BaseTest
import com.android.server.wm.flicker.helpers.ImeAppHelper
import com.android.server.wm.flicker.testapp.ActivityOptions
import com.android.server.wm.flicker.testapp.ActivityOptions.Ime.Default.ACTION_START_ADJACENT_ACTIVITY
import com.android.wm.shell.flicker.utils.SplitScreenUtils
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.junit.runners.Parameterized

/**
 * Test IME window opening transitions within a split-screen environment.
 * To run this test: `atest FlickerTestsIme:ShowImeInSplitScreenTest`
 */
@RequiresDevice
@RunWith(Parameterized::class)
@Parameterized.UseParametersRunnerFactory(FlickerParametersRunnerFactory::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class ShowImeInSplitScreenTest(flicker: FlickerTest) : BaseTest(flicker) {

    private val primaryApp = ImeAppHelper(instrumentation)
    private val secondaryApp = StandardAppHelper(
        instrumentation,
        appName = ActivityOptions.SplitScreen.Secondary.LABEL,
        componentMatcher = ActivityOptions.SplitScreen.Secondary.COMPONENT.toFlickerComponent()
    )

    /** {@inheritDoc} */
    override val transition: FlickerBuilder.() -> Unit = {
        setup {
            primaryApp.launchViaIntent(wmHelper)

            broadcastActionTrigger.doAction(ACTION_START_ADJACENT_ACTIVITY)
            SplitScreenUtils.waitForSplitComplete(wmHelper, primaryApp, secondaryApp)

            // Launch the primary again to make the window focused again.
            primaryApp.launchViaIntent(wmHelper)
        }
        transitions {
            primaryApp.openIME(wmHelper)
        }
        teardown {
            primaryApp.closeIME(wmHelper)
            primaryApp.exit(wmHelper)
            secondaryApp.exit(wmHelper)
        }
    }

    @Presubmit
    @Test
    fun imeWindowBecomesVisible() = flicker.imeWindowBecomesVisible()

    @Presubmit
    @Test
    fun imeLayerBecomesVisible() = flicker.imeLayerBecomesVisible()

    @Presubmit
    @Test
    fun primaryAppWindowRemainsVisibleOnTopInSplit() {
        flicker.assertWm {
            this.isAppWindowVisible(primaryApp)
        }
    }

    @Presubmit
    @Test
    fun secondaryAppWindowRemainsVisibleInSplit() {
        flicker.assertWm {
            this.isAppWindowVisible(secondaryApp)
        }
    }

    @Presubmit
    @Test
    fun primaryAppLayerRemainsVisibleInSplit() {
        flicker.assertLayers {
            this.isVisible(primaryApp)
        }
    }

    @Presubmit
    @Test
    fun secondaryAppLayerRemainsVisibleInSplit() {
        flicker.assertLayers {
            this.isVisible(secondaryApp)
        }
    }

    companion object {
        @Parameterized.Parameters(name = "{0}")
        @JvmStatic
        fun getParams() = FlickerTestFactory.nonRotationTests()
    }
}
