/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.systemui.motion

import android.os.Build
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.testScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assume
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import platform.test.motion.MotionTestRule
import platform.test.motion.compose.ComposeToolkit
import platform.test.motion.compose.FixedConfiguration
import platform.test.motion.testing.createGoldenPathManager
import platform.test.screenshot.DeviceEmulationSpec
import platform.test.screenshot.Displays
import platform.test.screenshot.PathConfig
import platform.test.screenshot.utils.compose.createComposeScreenshotTestRule

/**
 * Create a [MotionTestRule] for motion tests of Compose-based System UI.
 *
 * When run in pre/postsubmit, tests using this rule are skipped unless the target device is
 * aosp_cf_x86_64_only_phone. Local test runs are not subject to this device check and will run on
 * any connected device.
 *
 * The provided [deviceEmulationSpec] is applied on top of the device that the test is running on,
 * enabling testing of various form factors.
 *
 * See http://go/motion-testing#target-device for instructions
 */
fun createSysUiComposeMotionTestRule(
    kosmos: Kosmos,
    deviceEmulationSpec: DeviceEmulationSpec = DeviceEmulationSpec(Displays.Phone),
    pathConfig: PathConfig = PathConfig(),
): MotionTestRule<ComposeToolkit> {
    val goldenPathManager =
        createGoldenPathManager("frameworks/base/packages/SystemUI/tests/goldens", pathConfig)
    val testScope = kosmos.testScope
    val composeScreenshotTestRule =
        createComposeScreenshotTestRule(
            deviceEmulationSpec,
            goldenPathManager,
            effectContext = testScope.coroutineContext + Dispatchers.Main,
        )
    val fixedConfiguration =
        FixedConfiguration(
            density =
                Density(
                    density = deviceEmulationSpec.display.densityDpi / 160f,
                    fontScale = deviceEmulationSpec.fontScale,
                )
        )
    return MotionTestRule(
        ComposeToolkit(composeScreenshotTestRule.composeRule, testScope, fixedConfiguration),
        goldenPathManager,
        bitmapDiffer = composeScreenshotTestRule,
        extraRules =
            RuleChain.outerRule(composeScreenshotTestRule).let { outerRule ->
                val targetDevice = resolveDeviceTarget()
                if (targetDevice != null) {
                    outerRule.around(DeviceCheckRule(targetDevice))
                } else {
                    outerRule
                }
            },
    )
}

private fun resolveDeviceTarget(): String? {
    return if (enforceDeviceCheck()) {
        "aosp_cf_x86_64_only_phone"
    } else {
        null
    }
}

private fun enforceDeviceCheck(): Boolean {
    return "true" ==
        InstrumentationRegistry.getArguments().getString("motionTestsEnforceDeviceCheck", "false")
}

private class DeviceCheckRule(private val targetDevice: String) : TestRule {

    override fun apply(base: Statement, description: Description): Statement {
        val currDevice = Build.PRODUCT
        val isCFPhoneDevice = currDevice == targetDevice
        return object : Statement() {
            override fun evaluate() {
                Assume.assumeTrue(
                    "Skipping test: ${description.displayName}. Test will run only on " +
                        "$targetDevice device, current device is $currDevice",
                    isCFPhoneDevice,
                )
                base.evaluate()
            }
        }
    }
}
