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

package com.android.systemui.accessibility.shortcutchooser.ui.composable

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.android.compose.PlatformButton
import com.android.compose.dialog.AlertDialogContent
import com.android.compose.theme.PlatformTheme
import com.android.systemui.accessibility.shortcutchooser.shared.model.AccessibilityTargetModel
import com.android.systemui.res.R

/**
 * Dialog content for quickly accessing all installed accessibility features. Used by
 * `SystemUIDialogFactory.create`.
 *
 * @param onDoneClick The callback for clicking on the done button
 * @param onTargetClick The callback for clicking on an accessibility feature target in the list.
 * @param targets The list of accessibility feature targets to display
 */
@Composable
fun QuickAccessDialogContent(
    onDoneClick: () -> Unit,
    onTargetClick: (AccessibilityTargetModel) -> Unit,
    targets: List<AccessibilityTargetModel>,
) {
    PlatformTheme {
        AlertDialogContent(
            modifier = Modifier.testTag("quick_access_dialog"),
            icon = {
                Icon(
                    imageVector = Icons.Default.AccessibilityNew,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                )
            },
            title = {
                Text(text = stringResource(R.string.accessibility_quick_access_dialog_title))
            },
            content = {
                ShortcutTargetsList(targets, modifier = Modifier.heightIn(max = 450.dp)) {
                    ShortcutToggleRow(it, onClick = { onTargetClick(it) })
                }
            },
            positiveButton = {
                PlatformButton(onClick = onDoneClick, modifier = Modifier.testTag("done_button")) {
                    Text(stringResource(R.string.accessibility_shortcutchooser_done_button))
                }
            },
            contentBottomPadding = 18.dp,
        )
    }
}
