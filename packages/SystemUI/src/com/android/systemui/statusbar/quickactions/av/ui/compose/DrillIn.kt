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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.android.systemui.statusbar.quickactions.av.ui.viewmodel.ButtonViewModel

/** A generic scaffolding composable for drill-in pages, providing a title and a back button. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DrillIn(
    drillInTitle: String,
    returnToMainPage: () -> Unit,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier) {
        Box(modifier = Modifier.fillMaxWidth()) {
            IconButton(
                onClick = { returnToMainPage() },
                modifier = Modifier.align(Alignment.CenterStart),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription =
                        stringResource(
                            com.android.systemui.res.R.string.av_drill_in_return_to_main
                        ),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 56.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = drillInTitle,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                subtitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        val verticalOffset = if (subtitle != null) 16.dp else 11.dp
        Spacer(modifier = Modifier.height(verticalOffset))
        content()
    }
}

@Composable
fun DrillInButtonColumn(
    buttonViewModels: List<ButtonViewModel>,
    buttonFactory: @Composable (Shape, ButtonViewModel) -> Unit,
    // Whether to split the buttons into sections based on active buttons.
    splitSections: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.Top),
        horizontalAlignment = Alignment.Start,
        modifier = modifier,
    ) {
        buttonViewModels.forEachIndexed { index, buttonViewModel ->
            val bigRadius = 20.dp
            val smallRadius = 2.dp

            val previousViewModel = buttonViewModels.getOrNull(index - 1)
            val subsequentViewModel = buttonViewModels.getOrNull(index + 1)

            val isActive = buttonViewModel.state.isEnabled
            val previousIsActive = previousViewModel?.state?.isEnabled ?: false
            val subsequentIsActive = subsequentViewModel?.state?.isEnabled ?: false

            val isFirst = (index == 0)
            val isLast = (index == buttonViewModels.size - 1)

            val isFirstInBlock = isFirst || (splitSections && (isActive || previousIsActive))
            val isLastInBlock = isLast || (splitSections && (isActive || subsequentIsActive))

            val topCornerRadius = if (isFirstInBlock) bigRadius else smallRadius
            val bottomCornerRadius = if (isLastInBlock) bigRadius else smallRadius

            val shape: Shape =
                RoundedCornerShape(
                    topStart = topCornerRadius,
                    topEnd = topCornerRadius,
                    bottomStart = bottomCornerRadius,
                    bottomEnd = bottomCornerRadius,
                )

            buttonFactory(shape, buttonViewModel)
        }
    }
}
