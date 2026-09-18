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

package com.android.wm.shell.flicker

import android.app.Instrumentation
import android.platform.test.annotations.Postsubmit
import android.tools.NavBar
import android.tools.Rotation
import android.tools.flicker.AssertionInvocationGroup
import android.tools.flicker.FlickerConfig
import android.tools.flicker.annotation.ExpectedScenarios
import android.tools.flicker.annotation.FlickerConfigProvider
import android.tools.flicker.assertors.ComponentTemplate
import android.tools.flicker.assertors.assertions.AppWindowBecomesInvisible
import android.tools.flicker.config.AssertionTemplates
import android.tools.flicker.config.FlickerConfig
import android.tools.flicker.config.FlickerConfigEntry
import android.tools.flicker.config.FlickerServiceConfig
import android.tools.flicker.config.ScenarioId
import android.tools.flicker.extractors.TaggedScenarioExtractorBuilder
import android.tools.flicker.junit.FlickerServiceJUnit4ClassRunner
import android.tools.traces.events.CujType
import android.tools.traces.parsers.WindowManagerStateHelper
import androidx.test.platform.app.InstrumentationRegistry
import com.android.server.wm.flicker.helpers.PipAppHelper
import com.android.server.wm.flicker.helpers.SimpleAppHelper
import com.android.server.wm.flicker.testapp.ActivityOptions
import com.android.wm.shell.Utils
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test closing a pip window via the dismiss button
 *
 * To run this test: `atest WMShellFlickerServicePipTests:ClosePipWithDismissButtonTest`
 *
 * Actions:
 * ```
 *     Launch an app in pip mode [pipApp],
 *     Click on the pip window
 *     Click on dismiss button and wait window disappear
 * ```
 */
@Postsubmit
@RunWith(FlickerServiceJUnit4ClassRunner::class)
class ClosePipWithDismissButtonTest {
    @Rule
    @JvmField
    val testSetupRule = Utils.testSetupRule(NavBar.MODE_GESTURAL, Rotation.ROTATION_0)

    @Before
    fun setup() {
        // Launch a background non-home app to avoid keep-clear-area updates.
        testApp.launchViaIntent(wmHelper)
        pipApp.launchViaIntent(
            wmHelper,
            stringExtras = mapOf(ActivityOptions.Pip.EXTRA_ENTER_PIP to "true"),
        )
    }

    @ExpectedScenarios(["PIP_REMOVE_WITH_DISMISS_BUTTON"])
    @Test
    fun closePipWithDismissButton() {
        pipApp.closePipWindow(wmHelper)
    }

    companion object {
        val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
        val wmHelper = WindowManagerStateHelper(instrumentation)
        val testApp: SimpleAppHelper = SimpleAppHelper(instrumentation)
        val pipApp: PipAppHelper = PipAppHelper(instrumentation)
        private val pipAppComponentTemplate = ComponentTemplate("PIP_APP") { _ -> pipApp }

        private val PIP_REMOVE_CUJ_EXTRACTOR =
            TaggedScenarioExtractorBuilder()
                .setTargetTag(CujType.CUJ_PIP_TRANSITION)
                .setAdditionalCujFilter { it.tag == "REMOVE_PIP" }
                .build()

        private val PIP_REMOVE_CUJ_CONFIG =
            FlickerConfigEntry(
                scenarioId = ScenarioId("PIP_REMOVE_WITH_DISMISS_BUTTON"),
                extractor = PIP_REMOVE_CUJ_EXTRACTOR,
                assertions =
                    AssertionTemplates.COMMON_ASSERTIONS +
                        mapOf(
                            AppWindowBecomesInvisible(pipAppComponentTemplate) to
                                AssertionInvocationGroup.BLOCKING
                        ),
                enabled = true,
            )

        @JvmStatic
        @FlickerConfigProvider
        fun flickerConfigProvider(): FlickerConfig =
            FlickerConfig().use(FlickerServiceConfig.DEFAULT).use(PIP_REMOVE_CUJ_CONFIG)
    }
}
