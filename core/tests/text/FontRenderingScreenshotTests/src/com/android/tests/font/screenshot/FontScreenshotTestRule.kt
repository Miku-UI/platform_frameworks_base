/*
 * Copyright 2026 The Android Open Source Project
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

package com.android.tests.font.screenshot

import org.junit.rules.RuleChain
import org.junit.rules.TestName
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import platform.test.screenshot.BitmapDiffer
import platform.test.screenshot.DeviceEmulationRule
import platform.test.screenshot.DeviceEmulationSpec
import platform.test.screenshot.Displays
import platform.test.screenshot.GoldenPathManager
import platform.test.screenshot.MaterialYouColorsRule
import platform.test.screenshot.ScreenshotAsserterFactory
import platform.test.screenshot.ScreenshotTestRule

/**
 * A [TestRule] that provides device emulation and screenshot testing capabilities for font tests.
 */
class FontScreenshotTestRule(
    pathManager: GoldenPathManager = FontGoldenPathManager(),
    private val screenshotRule: ScreenshotTestRule = ScreenshotTestRule(pathManager),
) : TestRule, BitmapDiffer by screenshotRule, ScreenshotAsserterFactory by screenshotRule {

    private val deviceEmulationRule = DeviceEmulationRule(DeviceEmulationSpec(
        Displays.Phone,
        isDarkTheme = false,
        isLandscape = false
    ))

    override fun apply(base: Statement, description: Description): Statement? {
        return RuleChain.outerRule(MaterialYouColorsRule()).around(deviceEmulationRule)
            .around(screenshotRule).around(TestName()).apply(base, description)
    }
}