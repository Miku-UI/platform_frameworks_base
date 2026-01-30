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

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.android.systemui.notifications.ui.composable.component

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow.Companion.Ellipsis
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun Title(title: String, modifier: Modifier = Modifier) {
    Text(
        title,
        modifier,
        style = MaterialTheme.typography.titleSmallEmphasized,
        maxLines = 1,
        overflow = Ellipsis,
    )
}

@Composable
internal fun CollapsedText(content: String, modifier: Modifier = Modifier) {
    Text(
        content,
        modifier,
        style = MaterialTheme.typography.bodyMediumEmphasized,
        maxLines = 1,
        overflow = Ellipsis,
    )
}

@Composable
internal fun ExpandedText(content: String, maxLines: Int, modifier: Modifier = Modifier) {
    Text(
        content,
        modifier,
        style = MaterialTheme.typography.bodyMediumEmphasized,
        maxLines = maxLines,
        overflow = Ellipsis,
    )
}

@Composable
internal fun TopLineText(
    modifier: Modifier = Modifier,
    title: String? = null,
    appNameText: String? = null,
    headerTextSecondary: String? = null,
    headerText: String? = null,
    timeText: String? = null,
    verificationText: String? = null,
) {
    val density = LocalDensity.current
    PrioritizedRow(modifier = modifier) {
        val reducedWidth = 72.dp
        val hideWidth = with(density) { 24.sp.toDp() }
        var isFirstElement = true

        @Composable
        fun maybeAddSeparator() {
            if (!isFirstElement) {
                TopLineSeparator()
            } else {
                isFirstElement = false
            }
        }

        if (title != null) {
            isFirstElement = false
            Title(title, Modifier.shrinkable(importance = 3, minWidth = reducedWidth))
        }
        if (appNameText != null) {
            maybeAddSeparator()
            TopLineComponentText(
                text = appNameText,
                modifier = Modifier.shrinkable(importance = 1, minWidth = reducedWidth),
            )
        }
        if (headerTextSecondary != null) {
            maybeAddSeparator()
            TopLineComponentText(
                text = headerTextSecondary,
                modifier =
                    Modifier.hideable(
                        importance = 3,
                        reducedWidth = reducedWidth,
                        hideWidth = hideWidth,
                    ),
            )
        }
        if (headerText != null) {
            maybeAddSeparator()
            TopLineComponentText(
                text = headerText,
                modifier =
                    Modifier.hideable(
                        importance = 3,
                        reducedWidth = reducedWidth,
                        hideWidth = hideWidth,
                    ),
            )
        }
        if (timeText != null) {
            maybeAddSeparator()
            TopLineComponentText(text = timeText)
        }

        // No separators for verification text and trailing icons
        if (verificationText != null) {
            // TODO: b/431222735 - Display verification icon.
            TopLineComponentText(
                text = verificationText,
                modifier =
                    Modifier.shrinkable(importance = 2, minWidth = reducedWidth)
                        .padding(start = 4.dp),
            )
        }
        // TODO: b/431222735 - Display trailing icons: phishing, profile badge, and alerting.
    }
}

@Composable
private fun TopLineComponentText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodySmallEmphasized,
) {
    Text(text = text, modifier = modifier, style = style, maxLines = 1, overflow = Ellipsis)
}

@Composable
private fun PrioritizedRowScope.TopLineSeparator(modifier: Modifier = Modifier) {
    TopLineComponentText(text = "•", modifier = modifier.padding(horizontal = 4.dp).separator())
}
