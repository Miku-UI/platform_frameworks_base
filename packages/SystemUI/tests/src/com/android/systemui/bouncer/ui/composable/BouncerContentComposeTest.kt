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

import android.content.testableContext
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions.CustomActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.compose.animation.scene.TestContentScope
import com.android.compose.theme.PlatformTheme
import com.android.systemui.SysuiTestCase
import com.android.systemui.bouncer.ui.viewmodel.bouncerOverlayContentViewModelFactory
import com.android.systemui.keyguard.data.repository.fakeDeviceEntryFaceAuthRepository
import com.android.systemui.lifecycle.rememberViewModel
import com.android.systemui.res.R
import com.android.systemui.statusbar.phone.SystemUIDialog
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
@SmallTest
class BouncerContentComposeTest : SysuiTestCase() {

    private companion object {
        const val BACK_BUTTON_TAG = "BackButton"
        const val A11Y_BUTTON_TAG = "AccessibilityButton"
        const val BOUNCER_CONTENT_ROOT_TAG = "BouncerContentRoot"
    }

    private val kosmos = testKosmos()

    @get:Rule val composeTestRule = createComposeRule()

    @Mock private lateinit var bouncerDialogFactory: SystemUIDialog.Factory

    @Composable
    private fun BouncerContentUnderTest() {
        PlatformTheme {
            TestContentScope {
                BouncerContentLayout(
                    viewModel =
                        rememberViewModel("test") {
                            kosmos.bouncerOverlayContentViewModelFactory.create()
                        },
                    layout = BouncerOverlayLayout.BESIDE_USER_SWITCHER,
                    modifier = Modifier.testTag(BOUNCER_CONTENT_ROOT_TAG),
                    dialogFactory = bouncerDialogFactory,
                )
            }
        }
    }

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        whenever(bouncerDialogFactory.create()).thenThrow(AssertionError())
    }

    @Test
    @EnableFlags(com.android.systemui.Flags.FLAG_BACK_BUTTON_ON_BOUNCER_FIX)
    fun backButton_shownOnLargeScreens() {
        kosmos.testableContext.orCreateTestableResources.addOverride(
            R.bool.config_improveLargeScreenInteractionOnLockscreen,
            true,
        )

        composeTestRule.setContent { BouncerContentUnderTest() }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(BACK_BUTTON_TAG).assertIsDisplayed()
    }

    @Test
    @EnableFlags(com.android.systemui.Flags.FLAG_BACK_BUTTON_ON_BOUNCER_FIX)
    fun backButton_hiddenOnSmallScreens() {
        kosmos.testableContext.orCreateTestableResources.addOverride(
            R.bool.config_improveLargeScreenInteractionOnLockscreen,
            false,
        )

        composeTestRule.setContent { BouncerContentUnderTest() }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(BACK_BUTTON_TAG).isNotDisplayed()
    }

    @Test
    @EnableFlags(com.android.systemui.Flags.FLAG_BOUNCER_ACCESSIBILITY_BUTTON_FOR_DESKTOP)
    fun accessibilityButton_withConfigEnabled_showsAccessibilityButton() {
        kosmos.testableContext.orCreateTestableResources.addOverride(
            R.bool.config_showAccessibilityButtonOnBouncer,
            true,
        )

        composeTestRule.setContent { BouncerContentUnderTest() }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(A11Y_BUTTON_TAG).assertIsDisplayed()
    }

    @Test
    @EnableFlags(com.android.systemui.Flags.FLAG_BOUNCER_ACCESSIBILITY_BUTTON_FOR_DESKTOP)
    fun accessibilityButton_withConfigDisabled_showsNoAccessibilityButton() {
        kosmos.testableContext.orCreateTestableResources.addOverride(
            R.bool.config_showAccessibilityButtonOnBouncer,
            false,
        )

        composeTestRule.setContent { BouncerContentUnderTest() }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(A11Y_BUTTON_TAG).assertDoesNotExist()
    }

    @Test
    @DisableFlags(com.android.systemui.Flags.FLAG_BOUNCER_ACCESSIBILITY_BUTTON_FOR_DESKTOP)
    fun accessibilityButton_withFlagDisabled_showsNoAccessibilityButton() {
        kosmos.testableContext.orCreateTestableResources.addOverride(
            R.bool.config_showAccessibilityButtonOnBouncer,
            true,
        )

        composeTestRule.setContent { BouncerContentUnderTest() }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(A11Y_BUTTON_TAG).assertDoesNotExist()
    }

    @Test
    fun accessibilityActions_retriesFaceAuth_disabled() {
        // Disable face auth
        kosmos.fakeDeviceEntryFaceAuthRepository.canRunFaceAuth.value = false

        composeTestRule.setContent { BouncerContentUnderTest() }
        composeTestRule.waitForIdle()

        // Assert the a11y action is missing
        composeTestRule
            .onNodeWithTag(BOUNCER_CONTENT_ROOT_TAG)
            .assertMissingCustomAction(context.getString(R.string.retry_face))
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun accessibilityActions_retriesFaceAuth_enabled() {
        // Enable face auth
        kosmos.fakeDeviceEntryFaceAuthRepository.canRunFaceAuth.value = true

        composeTestRule.setContent { BouncerContentUnderTest() }
        composeTestRule.waitForIdle()

        // Assert the a11y action is available
        composeTestRule
            .onNodeWithTag(BOUNCER_CONTENT_ROOT_TAG)
            .performCustomAccessibilityActionWithLabel(context.getString(R.string.retry_face))
    }

    private fun SemanticsNodeInteraction.assertMissingCustomAction(
        label: String
    ): SemanticsNodeInteraction {
        assertThat(
                fetchSemanticsNode().config.getOrElse(CustomActions, { emptyList() }).map {
                    it.label
                }
            )
            .doesNotContain(label)
        return this
    }
}
