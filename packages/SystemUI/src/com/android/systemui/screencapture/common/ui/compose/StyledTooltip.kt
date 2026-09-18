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

package com.android.systemui.screencapture.common.ui.compose

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun StyledTooltip(tooltipText: String, content: @Composable () -> Unit) {
    if (tooltipText.isNotBlank()) {
        val tooltipState = rememberTooltipState(isPersistent = false)
        val tooltipMaxWidth = TooltipDefaults.richTooltipMaxWidth
        val tertiaryColor = MaterialTheme.colorScheme.tertiaryFixed
        val onTertiaryColor = MaterialTheme.colorScheme.onTertiaryFixed

        TooltipBox(
            positionProvider =
                TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
            tooltip = {
                PlainTooltip(
                    caretShape = TooltipDefaults.caretShape(),
                    shape = RoundedCornerShape(size = 360.dp),
                    maxWidth = tooltipMaxWidth,
                    containerColor = tertiaryColor,
                    contentColor = onTertiaryColor,
                ) {
                    Text(
                        text = tooltipText,
                        style = MaterialTheme.typography.labelLarge,
                        color = onTertiaryColor,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                    )
                }
            },
            state = tooltipState,
            focusable = false,
            enableUserInput = true,
            content = content,
        )
    } else {
        content()
    }
}
