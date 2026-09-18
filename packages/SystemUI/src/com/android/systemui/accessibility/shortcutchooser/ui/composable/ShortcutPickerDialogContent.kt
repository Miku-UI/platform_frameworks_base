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

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.android.compose.PlatformOutlinedButton
import com.android.compose.dialog.AlertDialogContent
import com.android.compose.theme.PlatformTheme
import com.android.systemui.accessibility.shortcutchooser.shared.model.AccessibilityTargetModel
import com.android.systemui.res.R

/**
 * The content used by [SystemUIDialogFactory.create] to create toggle dialogs for different
 * shortcut type.
 *
 * @param targets The list of targets enabled for a specific shortcut type
 * @param onEditClick The callback for the negative button
 * @param onDoneClick The callback for the positive button
 * @param onTargetClick The callback when clicking on the [ToggleTargetRow]
 */
@Composable
fun ShortcutPickerDialogContent(
    targets: List<AccessibilityTargetModel>,
    showEditButton: Boolean,
    onEditClick: () -> Unit,
    onDoneClick: () -> Unit,
    onTargetClick: (AccessibilityTargetModel) -> Unit,
) {
    PlatformTheme {
        AlertDialogContent(
            title = {
                Text(
                    stringResource(R.string.accessibility_shortcutchooser_picker_dialog_title),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Start,
                )
            },
            content = {
                ShortcutTargetsList(targets, modifier = Modifier.heightIn(max = 400.dp)) {
                    ShortcutToggleRow(it, onClick = { onTargetClick(it) })
                }
            },
            negativeButton =
                if (showEditButton) {
                    {
                        PlatformOutlinedButton(
                            onClick = onEditClick,
                            modifier = Modifier.testTag("edit_button"),
                        ) {
                            Text(stringResource(R.string.accessibility_shortcutchooser_edit_button))
                        }
                    }
                } else {
                    null
                },
            positiveButton = {
                PlatformOutlinedButton(
                    onClick = onDoneClick,
                    modifier = Modifier.testTag("done_button"),
                ) {
                    Text(stringResource(R.string.accessibility_shortcutchooser_done_button))
                }
            },
            contentBottomPadding = 18.dp,
        )
    }
}
