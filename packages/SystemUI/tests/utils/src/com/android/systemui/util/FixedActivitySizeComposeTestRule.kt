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

package com.android.systemui.util

import android.app.ActivityOptions
import android.graphics.Rect
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.rules.ActivityScenarioRule
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import platform.test.screenshot.DeviceEmulationRule
import platform.test.screenshot.DeviceEmulationSpec

/**
 * A [TestRule] that creates a Compose test environment with a fixed, emulated activity device size.
 * The test activity is set to fill the whole display.
 *
 * @param spec The [DeviceEmulationSpec] defining the properties of the device to emulate.
 */
class FixedActivitySizeComposeTestRule(spec: DeviceEmulationSpec) : TestRule {
    val composeTestRule = createComposeRule()

    private val deviceEmulationRule = DeviceEmulationRule(spec)

    private val activityRule = ActivityScenarioRule(
        ComponentActivity::class.java,
        ActivityOptions.makeBasic()
            .setLaunchBounds(Rect(0, 0, spec.display.width, spec.display.height)).toBundle()
    )

    /**
     * [activityRule] needs to apply before [composeTestRule] to ensure the test activity configured
     * as expected.
     */
    private val ruleChain: RuleChain =
        RuleChain.outerRule(deviceEmulationRule).around(activityRule).around(composeTestRule)

    override fun apply(base: Statement?, description: Description?): Statement {
        return ruleChain.apply(base, description)
    }
}