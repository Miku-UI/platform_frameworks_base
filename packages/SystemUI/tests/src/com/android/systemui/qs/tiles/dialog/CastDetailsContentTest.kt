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

package com.android.systemui.qs.tiles.dialog

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.internal.app.MediaRouteChooserContentManager
import com.android.internal.app.MediaRouteControllerContentManager
import com.android.systemui.SysuiTestCase
import com.android.systemui.qs.tiles.impl.mediaroute.mediaRouteChooserContentManager
import com.android.systemui.qs.tiles.impl.mediaroute.mediaRouteControllerContentManager
import com.android.systemui.testKosmos
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify

@SmallTest
@RunWith(AndroidJUnit4::class)
class CastDetailsContentTest : SysuiTestCase() {
    @get:Rule val composeRule = createComposeRule()
    private val kosmos = testKosmos()
    private val chooserContentManager: MediaRouteChooserContentManager =
        kosmos.mediaRouteChooserContentManager
    private val controllerContentManager: MediaRouteControllerContentManager =
        kosmos.mediaRouteControllerContentManager
    private val viewModel: CastDetailsViewModel =
        mock<CastDetailsViewModel> {
            on { createChooserContentManager() } doReturn chooserContentManager
            on { createControllerContentManager() } doReturn controllerContentManager
        }

    @Test
    fun shouldShowChooserDialogTrue_showChooserUI() {
        viewModel.stub { on { shouldShowChooserDialog() } doReturn true }

        composeRule.setContent { CastDetailsContent(viewModel) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(CastDetailsViewModel.CHOOSER_VIEW_TEST_TAG).assertExists()
        composeRule
            .onNodeWithTag(CastDetailsViewModel.CONTROLLER_VIEW_TEST_TAG)
            .assertDoesNotExist()
        composeRule.onNodeWithText("Disconnect").assertDoesNotExist()

        verify(chooserContentManager).bindViews(any())
        verify(chooserContentManager).onAttachedToWindow()
    }

    @Test
    fun shouldShowChooserDialogFalse_showControllerUI() {
        viewModel.stub { on { shouldShowChooserDialog() } doReturn false }

        composeRule.setContent { CastDetailsContent(viewModel) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(CastDetailsViewModel.CONTROLLER_VIEW_TEST_TAG).assertExists()
        composeRule.onNodeWithText("Disconnect").assertExists()
        composeRule.onNodeWithTag(CastDetailsViewModel.CHOOSER_VIEW_TEST_TAG).assertDoesNotExist()

        verify(controllerContentManager).bindViews(any())
        verify(controllerContentManager).onAttachedToWindow()
    }

    @Test
    fun clickOnDisconnectButton_shouldCallDisconnect() {
        viewModel.stub { on { shouldShowChooserDialog() } doReturn false }

        composeRule.setContent { CastControllerDisconnectButton(controllerContentManager) }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Disconnect").assertIsDisplayed().performClick()
        composeRule.waitForIdle()

        verify(controllerContentManager).onDisconnectButtonClick()
    }
}
