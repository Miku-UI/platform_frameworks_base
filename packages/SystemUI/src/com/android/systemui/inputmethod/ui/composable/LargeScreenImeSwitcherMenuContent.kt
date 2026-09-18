/*
 * Copyright (C) 2026 The Android Open Source Project
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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.android.internal.R
import com.android.systemui.inputmethod.ui.viewmodel.ImeSwitcherMenuViewModel
import com.android.systemui.lifecycle.rememberActivated

/**
 * The large-screen UI for the content of the IME Switcher Menu Dialog.
 *
 * @param viewModelFactory the factory to create a view model.
 * @param dismissAction the action to invoke when the UI should be dismissed.
 */
@Composable
fun LargeScreenImeSwitcherMenuContent(
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
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(MenuDimensions.InternalPadding)
                .semantics {
                    paneTitle = paneTitleDescription
                    testTagsAsResourceId = true
                }
                .testTag("container"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Header(
            settingsButtonAction =
                viewModel.settingsButtonAction.value?.let { action ->
                    {
                        action()
                        dismissAction()
                    }
                }
        )
        Spacer(modifier = Modifier.size(MenuDimensions.AfterHeaderSpace))
        Column(
            modifier =
                Modifier.weight(weight = 1f, fill = false).testTag("large_screen_ime_switcher_menu")
        ) {
            ImeSwitcherMenuList(
                viewModel.menuItems.toList(),
                viewModel,
                dismissAction,
                useLargeScreenLayout = true,
            )
        }
    }
}

/**
 * The header of the large-screen IME switcher menu, which contains the title and the settings
 * button.
 *
 * @param settingsButtonAction the action to invoke when the settings button is clicked. This action
 *   should also dismiss the UI.
 */
@Composable
private fun Header(settingsButtonAction: (() -> Unit)?) {
    val title =
        stringResource(com.android.systemui.res.R.string.input_method_switcher_title_large_screen)
    val settingsButtonDescription = stringResource(R.string.input_method_language_settings)
    Row(
        modifier = Modifier.fillMaxWidth().padding(MenuDimensions.HeaderInternalPadding),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(modifier = Modifier.size(MenuDimensions.HeaderButtonSize))
        Text(
            text = title,
            modifier = Modifier.align(Alignment.CenterVertically),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        val endContentModifier = Modifier.size(MenuDimensions.HeaderButtonSize)
        if (settingsButtonAction != null) {
            IconButton(
                modifier = endContentModifier.testTag("settings_button"),
                onClick = settingsButtonAction,
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = settingsButtonDescription,
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        } else {
            Spacer(modifier = endContentModifier)
        }
    }
}

private object MenuDimensions {
    val InternalPadding = PaddingValues(vertical = 14.dp)
    val HeaderInternalPadding = PaddingValues(horizontal = 14.dp)
    val HeaderButtonSize = 36.dp
    val AfterHeaderSpace = 8.dp
}
