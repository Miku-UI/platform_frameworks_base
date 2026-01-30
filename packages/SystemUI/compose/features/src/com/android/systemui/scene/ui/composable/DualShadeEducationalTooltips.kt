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

@file:OptIn(ExperimentalMaterial3Api::class)

package com.android.systemui.scene.ui.composable

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.RichTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.android.compose.theme.LocalAndroidColorScheme
import com.android.systemui.lifecycle.rememberViewModel
import com.android.systemui.scene.ui.viewmodel.DualShadeEducationalTooltipsViewModel

@Composable
fun DualShadeEducationalTooltips(
    viewModelFactory: DualShadeEducationalTooltipsViewModel.Factory,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val viewModel =
        rememberViewModel(traceName = "DualShadeEducationalTooltips") {
            viewModelFactory.create(context)
        }

    val visibleTooltip = viewModel.visibleTooltip

    Layout(
        content = {
            AnchoredTooltip(
                isVisible = visibleTooltip != null,
                text = visibleTooltip?.text,
                onShown = visibleTooltip?.onShown,
                onDismissed = visibleTooltip?.onDismissed,
            )
        },
        modifier = modifier.fillMaxSize(),
    ) { measurables, constraints ->
        check(measurables.size == 1)
        val placeable =
            measurables[0].measure(
                Constraints.fixed(
                    width = visibleTooltip?.anchorBounds?.width ?: 0,
                    height = visibleTooltip?.anchorBounds?.height ?: 0,
                )
            )
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeable.place(
                x = visibleTooltip?.anchorBounds?.left ?: 0,
                y = visibleTooltip?.anchorBounds?.top ?: 0,
            )
        }
    }
}

@Composable
private fun AnchoredTooltip(
    isVisible: Boolean,
    text: String?,
    onShown: (() -> Unit)?,
    onDismissed: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val tooltipState = rememberTooltipState(initialIsVisible = false, isPersistent = true)

    LaunchedEffect(isVisible) {
        if (isVisible) {
            onShown?.invoke()
            tooltipState.show()
        } else {
            tooltipState.dismiss()
        }
    }

    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(),
        tooltip = {
            RichTooltip(
                colors =
                    TooltipDefaults.richTooltipColors(
                        containerColor = LocalAndroidColorScheme.current.tertiaryFixed,
                        contentColor = LocalAndroidColorScheme.current.onTertiaryFixed,
                    ),
                caretSize = TooltipDefaults.caretSize,
                shadowElevation = 2.dp,
            ) {
                Text(text = text ?: "", modifier = Modifier.padding(8.dp))
            }
        },
        state = tooltipState,
        onDismissRequest = onDismissed,
        modifier = modifier,
    ) {
        Spacer(modifier = Modifier.fillMaxSize())
    }
}
