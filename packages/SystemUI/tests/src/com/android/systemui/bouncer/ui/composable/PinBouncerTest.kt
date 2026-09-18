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

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.compose.theme.PlatformTheme
import com.android.systemui.SysuiTestCase
import com.android.systemui.authentication.shared.model.AuthenticationMethodModel
import com.android.systemui.bouncer.ui.viewmodel.pinBouncerViewModelFactory
import com.android.systemui.haptics.msdl.bouncerHapticPlayer
import com.android.systemui.res.R
import com.android.systemui.testKosmos
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SmallTest
class PinBouncerTest : SysuiTestCase() {

    private val kosmos = testKosmos()

    @get:Rule val composeTestRule = createComposeRule()

    private val viewModel =
        kosmos.pinBouncerViewModelFactory.create(
            isInputEnabled = MutableStateFlow(true).asStateFlow(),
            onIntentionalUserInput = {},
            authenticationMethod = AuthenticationMethodModel.Pin,
            bouncerHapticPlayer = kosmos.bouncerHapticPlayer,
        )

    @Composable
    private fun PinBouncerUnderTest() {
        PlatformTheme {
            PinPad(viewModel = viewModel, verticalSpacing = 12.dp, modifier = Modifier.size(400.dp))
        }
    }

    @Test
    fun pinPad_buttonsHaveCorrectSemantics() {
        composeTestRule.setContent { PinBouncerUnderTest() }
        composeTestRule.waitForIdle()

        // Check digit buttons
        for (i in 0..9) {
            val digitNode =
                composeTestRule
                    .onNodeWithText(i.toString())
                    .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        }

        // Check action buttons
        val deleteDescription = context.getString(R.string.keyboardview_keycode_delete)
        composeTestRule
            .onNodeWithContentDescription(deleteDescription)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))

        val enterDescription = context.getString(R.string.keyboardview_keycode_enter)
        composeTestRule
            .onNodeWithContentDescription(enterDescription)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
    }
}
