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
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import com.android.settingslib.spa.screenshot.util.settingsScreenshotTestRule
import com.android.settingslib.spa.widget.preference.BulletPreference
import com.android.settingslib.spa.widget.preference.Preference
import com.android.settingslib.spa.widget.preference.PreferenceModel
import com.android.settingslib.spa.widget.scaffold.MoreOptionsAction
import com.android.settingslib.spa.widget.scaffold.RegularScaffold
import com.android.settingslib.spa.widget.ui.Category
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import platform.test.runner.parameterized.ParameterizedAndroidJunit4
import platform.test.runner.parameterized.Parameters
import platform.test.screenshot.DeviceEmulationSpec
import platform.test.screenshot.PhoneAndTabletMinimal

@RunWith(ParameterizedAndroidJunit4::class)
class RegularScaffoldScreenshotTest(emulationSpec: DeviceEmulationSpec) {
  companion object {
    @Parameters(name = "{0}")
    @JvmStatic
    fun getTestSpecs() = DeviceEmulationSpec.Companion.PhoneAndTabletMinimal
  }

  @get:Rule val screenshotRule = settingsScreenshotTestRule(emulationSpec)

  @Test
  fun test() {
    screenshotRule.screenshotTest("regularScaffold") {
      RegularScaffold(
          title = "Display",
          actions = {
            Button(onClick = {}) { Text(text = "Save") }
            MoreOptionsAction {}
          },
      ) {
        Category(title = "Category") {
          Preference(
              model =
                  object : PreferenceModel {
                    override val title = "Preference Title"
                    override val summary = { "Preference Summary" }
                  },
          )
        }
        BulletPreference(
            title = "Bullet point title",
            summary = "Bullet point description.",
            icon = Icons.Outlined.StarOutline,
        )
      }
    }
  }
}
