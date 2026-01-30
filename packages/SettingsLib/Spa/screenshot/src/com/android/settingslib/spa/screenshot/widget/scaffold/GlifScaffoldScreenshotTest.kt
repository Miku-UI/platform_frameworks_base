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

package com.android.settingslib.spa.screenshot.widget.scaffold

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrightnessMedium
import com.android.settingslib.spa.screenshot.util.settingsScreenshotTestRule
import com.android.settingslib.spa.widget.preference.CheckboxPreference
import com.android.settingslib.spa.widget.preference.CheckboxPreferenceModel
import com.android.settingslib.spa.widget.scaffold.BottomAppBarButton
import com.android.settingslib.spa.widget.scaffold.GlifScaffold
import com.android.settingslib.spa.widget.ui.Category
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import platform.test.runner.parameterized.ParameterizedAndroidJunit4
import platform.test.runner.parameterized.Parameters
import platform.test.screenshot.DeviceEmulationSpec
import platform.test.screenshot.PhoneAndTabletMinimal

@RunWith(ParameterizedAndroidJunit4::class)
class GlifScaffoldScreenshotTest(emulationSpec: DeviceEmulationSpec) {
    companion object {
        @Parameters(name = "{0}")
        @JvmStatic
        fun getTestSpecs() = DeviceEmulationSpec.Companion.PhoneAndTabletMinimal
    }

    @get:Rule val screenshotRule = settingsScreenshotTestRule(emulationSpec)

    @Test
    fun test() {
        screenshotRule.screenshotTest("glifScaffold") {
            GlifScaffold(
                imageVector = Icons.Outlined.BrightnessMedium,
                title = "Display",
                description = "Dark theme, font size, touch",
                actionButton = BottomAppBarButton("Next") {},
                dismissButton = BottomAppBarButton("Cancel") {},
            ) {
                Category {
                    CheckboxPreference(
                        object : CheckboxPreferenceModel {
                            override val title = "CheckboxPreference"
                            override val checked = { false }
                            override val onCheckedChange = null
                        }
                    )
                    CheckboxPreference(
                        object : CheckboxPreferenceModel {
                            override val title = "CheckboxPreference"
                            override val summary = { "Summary" }
                            override val checked = { true }
                            override val onCheckedChange = null
                        }
                    )
                }
            }
        }
    }
}
