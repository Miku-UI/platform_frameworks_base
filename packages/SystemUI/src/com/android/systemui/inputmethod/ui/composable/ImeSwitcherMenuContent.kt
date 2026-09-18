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

package com.android.systemui.inputmethod.ui.composable

import android.content.Context
import android.view.inputmethod.Flags as InputMethodFlags
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import com.android.compose.PlatformButton
import com.android.compose.PlatformOutlinedButton
import com.android.internal.R
import com.android.systemui.inputmethod.ui.viewmodel.ImeSwitcherMenuViewModel
import com.android.systemui.lifecycle.rememberActivated

/**
 * The UI for the content of the IME Switcher Menu Dialog.
 *
 * @param viewModelFactory the factory to create a view model.
 * @param dismissAction the action to invoke when the UI should be dismissed.
 */
@Composable
fun ImeSwitcherMenuContent(
    viewModelFactory: (context: Context) -> ImeSwitcherMenuViewModel,
    /** Callback when the UI should be dismissed. */
    dismissAction: () -> Unit,
) {
    val context = LocalContext.current
    val viewModel =
        rememberActivated(
            traceName = "imeSwitcherMenuViewModelFactory",
            key = Pair(viewModelFactory, context),
        ) {
            viewModelFactory.invoke(context)
        }

    val paneTitleDescription = stringResource(R.string.select_input_method)

    var containerModifier =
        Modifier.fillMaxWidth()
            .semantics {
                paneTitle = paneTitleDescription
                testTagsAsResourceId = true
            }
            .testTag("container")
    if (InputMethodFlags.imeSwitcherMenuSystemuiStyleUpdate()) {
        containerModifier =
            containerModifier
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(vertical = 24.dp)
    }

    Column(modifier = containerModifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Column(modifier = Modifier.weight(weight = 1f, fill = false)) {
            ImeSwitcherMenuList(
                viewModel.menuItems.toList(),
                viewModel,
                dismissAction,
                useLargeScreenLayout = false,
            )
        }
        viewModel.settingsButtonAction.value?.let { action ->
            if (InputMethodFlags.imeSwitcherMenuSystemuiStyleUpdate()) {
                Spacer(modifier = Modifier.height(32.dp))
            }

            SettingsFooter(
                settingsButtonAction = {
                    action.invoke()
                    dismissAction.invoke()
                }
            )
        }
    }
}

/**
 * The footer of the IME Switcher Menu Dialog, which contains the settings button.
 *
 * @param settingsButtonAction the action to invoke when the settings button is clicked. This action
 *   should also dismiss the UI.
 */
@Composable
private fun SettingsFooter(settingsButtonAction: () -> Unit) {
    val text = stringResource(R.string.input_method_switcher_settings_button)
    val description = stringResource(R.string.input_method_language_settings)

    if (InputMethodFlags.imeSwitcherMenuSystemuiStyleUpdate()) {
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .heightIn(min = 40.dp)
                    .padding(horizontal = 24.dp)
                    .testTag("settings_footer"),
            horizontalArrangement = Arrangement.End,
        ) {
            PlatformButton(
                modifier =
                    Modifier.wrapContentHeight().heightIn(min = 40.dp).testTag("settings_button"),
                onClick = settingsButtonAction,
            ) {
                Text(
                    style = MaterialTheme.typography.labelLarge,
                    text = text,
                    modifier = Modifier.semantics { contentDescription = description },
                )
            }
        }
    } else {
        Box(
            contentAlignment = Alignment.CenterEnd,
            modifier =
                Modifier.fillMaxWidth()
                    .padding(top = 8.dp, end = 16.dp, bottom = 16.dp)
                    .testTag("settings_footer"),
        ) {
            PlatformOutlinedButton(
                modifier = Modifier.testTag("settings_button"),
                onClick = settingsButtonAction,
            ) {
                Text(
                    text = text,
                    modifier =
                        Modifier.padding(vertical = 3.dp).semantics {
                            contentDescription = description
                        },
                )
            }
        }
    }
}
