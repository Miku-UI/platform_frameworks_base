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

package com.android.systemui.bouncer.ui.composable

import android.app.AlertDialog
import android.content.testableContext
import android.platform.test.annotations.EnableFlags
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.compose.animation.scene.TestContentScope
import com.android.compose.theme.PlatformTheme
import com.android.systemui.SysuiTestCase
import com.android.systemui.bouncer.ui.BouncerDialogFactory
import com.android.systemui.bouncer.ui.viewmodel.bouncerOverlayContentViewModelFactory
import com.android.systemui.lifecycle.rememberViewModel
import com.android.systemui.res.R
import com.android.systemui.testKosmos
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SmallTest
class BouncerContentComposeTest : SysuiTestCase() {
    private val kosmos = testKosmos()

    @get:Rule val composeTestRule = createComposeRule()

    private val bouncerDialogFactory =
        object : BouncerDialogFactory {
            override fun invoke(): AlertDialog {
                throw AssertionError()
            }
        }

    @Composable
    private fun BouncerContentUnderTest() {
        PlatformTheme {
            TestContentScope {
                BouncerContent(
                    viewModel =
                        rememberViewModel("test") {
                            kosmos.bouncerOverlayContentViewModelFactory.create()
                        },
                    layout = BouncerOverlayLayout.BESIDE_USER_SWITCHER,
                    modifier = Modifier,
                    dialogFactory = bouncerDialogFactory,
                )
            }
        }
    }

    @Test
    @EnableFlags(com.android.systemui.Flags.FLAG_BACK_BUTTON_ON_BOUNCER)
    fun backButton_shownOnLargeScreens() {
        kosmos.testableContext.orCreateTestableResources.addOverride(
            R.bool.config_improveLargeScreenInteractionOnLockscreen,
            true,
        )

        composeTestRule.setContent { BouncerContentUnderTest() }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("BackButton").assertIsDisplayed()
    }

    @Test
    @EnableFlags(com.android.systemui.Flags.FLAG_BACK_BUTTON_ON_BOUNCER)
    fun backButton_hiddenOnSmallScreens() {
        kosmos.testableContext.orCreateTestableResources.addOverride(
            R.bool.config_improveLargeScreenInteractionOnLockscreen,
            false,
        )

        composeTestRule.setContent { BouncerContentUnderTest() }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("BackButton").isNotDisplayed()
    }
}
