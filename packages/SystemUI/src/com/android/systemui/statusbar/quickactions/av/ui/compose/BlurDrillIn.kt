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

package com.android.systemui.statusbar.quickactions.av.ui.compose

import androidx.compose.foundation.clickable
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.android.systemui.lifecycle.rememberViewModel
import com.android.systemui.statusbar.quickactions.av.ui.viewmodel.BlurDrillInViewModel
import com.android.systemui.statusbar.quickactions.av.ui.viewmodel.ButtonViewModel
import kotlinx.coroutines.launch

/** A drill-in page for selecting the blur intensity. */
@Composable
fun BlurDrillIn(
    viewModelFactory: BlurDrillInViewModel.Factory,
    returnToMainPage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel =
        rememberViewModel("BlurDrillIn.viewModel", key = returnToMainPage) {
            viewModelFactory.create(returnToMainPage)
        }
    DrillIn(
        drillInTitle = stringResource(viewModel.drillInTitle),
        returnToMainPage = { viewModel.returnToMainPage() },
        modifier = modifier,
    ) {
        DrillInButtonColumn(
            buttonViewModels =
                listOf(
                    viewModel.blurOffButton,
                    viewModel.blurLightButton,
                    viewModel.blurFullButton,
                ),
            buttonFactory = { shape: Shape, buttonViewModel: ButtonViewModel ->
                BlurSelectionButton(shape = shape, viewModel = buttonViewModel)
            },
            splitSections = true,
        )
    }
}

/** A button representing a specific blur option. */
@Composable
private fun BlurSelectionButton(shape: Shape, viewModel: ButtonViewModel) {
    val scope = rememberCoroutineScope()
    ListItem(
        leadingContent = {
            viewModel.state.image?.let {
                Icon(painter = painterResource(id = it), contentDescription = null)
            }
        },
        headlineContent = {
            viewModel.state.mainTitle?.let {
                Text(
                    text = stringResource(it),
                    style = MaterialTheme.typography.titleSmallEmphasized,
                )
            }
        },
        colors = itemColors(viewModel.state.isEnabled),
        modifier =
            Modifier.clip(shape = shape)
                .clickable(onClick = { scope.launch { viewModel.onClick() } }),
    )
}
