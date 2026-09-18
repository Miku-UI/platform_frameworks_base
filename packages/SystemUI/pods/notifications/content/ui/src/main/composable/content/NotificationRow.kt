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

package com.android.systemui.notifications.content.ui.composable.content

import android.graphics.drawable.Drawable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.systemui.notifications.content.ui.composable.component.AppIcon
import com.android.systemui.notifications.content.ui.composable.component.Expander
import com.android.systemui.notifications.content.ui.composable.component.LargeIcon
import com.android.systemui.notifications.content.ui.viewmodel.NotificationContentViewModel

/**
 * Lays out a notification row with a header consisting of a circular (app) icon, two lines
 * ([firstLine] and optional [secondLine], assumed to be some kind of text composable), an optional
 * large icon and an optional expand button. The [content], if present, is laid out below this
 * header.
 *
 * The [expanded] field determines the behavior of the expand button:
 * - if `null`, no expand button is shown
 * - if `true`, the arrow on the button is pointing up
 * - if `false`, the arrow on the button is pointing down
 *
 * This row should lay things out nicely when any optional element is missing. When there's no large
 * icon, the [firstLine] must be aligned with the expander. When there's no [secondLine], the
 * [firstLine] must be vertically centered to the icon(s).
 *
 * TODO: b/431222735 - Consider decoupling this further from the notification content. We could pass
 *   in custom composables for the circular start icon, the square end icon and even the expander.
 */
@Composable
internal fun NotificationRow(
    viewModel: NotificationContentViewModel,
    expanded: Boolean?,
    firstLine: @Composable () -> Unit,
    secondLine: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    content: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier
            .heightIn(max = viewModel.maxHeightDp.dp)
            .padding(vertical = 12.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        AppIcon(viewModel.appIcon, Modifier.padding(top = 4.dp, bottom = 4.dp, end = 16.dp))
        Column(Modifier.weight(1f)) {
            if (viewModel.largeIcon != null) {
                HeaderWithLargeIcon(
                    expanded,
                    viewModel.largeIcon!!,
                    viewModel.maxLargeIconAspectRatio,
                    firstLine,
                    secondLine,
                )
            } else {
                HeaderWithoutLargeIcon(expanded, firstLine, secondLine)
            }
            if (content != null) {
                Box(Modifier.padding(top = 4.dp)) { content() }
            }
        }
    }
}

/**
 * When the large icon is present, show the two lines of text, then the icon to the right of them,
 * then the expander.
 */
@Composable
private fun HeaderWithLargeIcon(
    expanded: Boolean?,
    largeIcon: Drawable,
    maxAspectRatio: Float,
    firstLine: @Composable () -> Unit,
    secondLine: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth()) {
        Column(
            // The text container has a min height in order to align correctly to the app icon when
            // there's only one line of text, or the font size is smaller.
            modifier = Modifier.weight(1f).padding(top = 4.dp).heightIn(min = 40.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            firstLine()
            secondLine()
        }
        LargeIcon(largeIcon, Modifier.padding(start = 16.dp), maxAspectRatio)
        expanded?.let {
            Expander(expanded = it, modifier = Modifier.padding(top = 4.dp, start = 8.dp))
        }
    }
}

/**
 * When the large icon is not present, the second line of text takes up all the available space
 * under the expander.
 */
@Composable
private fun HeaderWithoutLargeIcon(
    expanded: Boolean?,
    firstLine: @Composable () -> Unit,
    secondLine: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        // The text container has a min height in order to align correctly to the app icon when
        // there's only one line of text, or the font size is smaller.
        modifier.padding(top = 4.dp).fillMaxWidth().heightIn(min = 40.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        if (expanded != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { firstLine() }
                Expander(expanded = expanded, modifier = Modifier.padding(start = 8.dp))
            }
        } else {
            firstLine()
        }
        secondLine()
    }
}
