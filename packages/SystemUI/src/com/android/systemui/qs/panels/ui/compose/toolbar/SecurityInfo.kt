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

package com.android.systemui.qs.panels.ui.compose.toolbar

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.android.compose.animation.Expandable
import com.android.compose.animation.rememberExpandableController
import com.android.systemui.animation.Expandable
import com.android.systemui.common.ui.compose.Icon
import com.android.systemui.qs.footer.ui.viewmodel.FooterActionsSecurityButtonViewModel
import com.android.systemui.qs.ui.compose.borderOnFocus

@Composable
fun SecurityInfo(
    viewModel: FooterActionsSecurityButtonViewModel?,
    showCollapsed: Boolean,
    modifier: Modifier = Modifier,
) {
    if (viewModel == null) {
        return
    }
    val onClick: ((Expandable) -> Unit)? =
        viewModel.onClick?.let { onClick ->
            val context = LocalContext.current
            { expandable -> onClick(context, expandable) }
        }
    CompositionLocalProvider(
        value = LocalContentColor provides MaterialTheme.colorScheme.onSurface
    ) {
        Expandable(
            controller =
                rememberExpandableController(color = Color.Transparent, shape = CircleShape),
            modifier =
                modifier
                    .borderOnFocus(color = MaterialTheme.colorScheme.secondary, CornerSize(0))
                    .semantics {
                        if (onClick != null) {
                            role = Role.Button
                        }
                    },
            onClick = onClick,
            useModifierBasedImplementation = true,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    icon = viewModel.icon,
                    modifier =
                        Modifier.minimumInteractiveComponentSize().size(24.dp).semantics {
                            if (showCollapsed) {
                                contentDescription = viewModel.text
                            }
                        },
                )
                if (!showCollapsed) {
                    Text(
                        text = viewModel.text,
                        maxLines = 1,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.basicMarquee(iterations = 1, initialDelayMillis = 1000),
                    )
                }
            }
        }
    }
}
