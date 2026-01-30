/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.settingslib.spa.widget.illustration

import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.size
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.settingslib.spa.test.R
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IllustrationTest {
    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun image_displayed() {
        composeTestRule.setContent {
            Illustration(
                object : IllustrationModel {
                    override val resId = R.drawable.accessibility_captioning_banner
                    override val resourceType = ResourceType.IMAGE
                }
            )
        }

        assertThat(composeTestRule.onRoot().getBoundsInRoot().size.height.value).isNonZero()
    }

    @Test
    fun lottie_emptyResource_notDisplayed() {
        composeTestRule.setContent {
            Illustration(
                object : IllustrationModel {
                    override val resId = R.raw.empty
                    override val resourceType = ResourceType.LOTTIE
                }
            )
        }

        assertThat(composeTestRule.onRoot().getBoundsInRoot().size.height).isEqualTo(0.dp)
    }
}
