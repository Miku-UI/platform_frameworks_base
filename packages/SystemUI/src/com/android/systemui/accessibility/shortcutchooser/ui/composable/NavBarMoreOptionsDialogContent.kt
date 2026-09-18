/*
 * Copyright (C) 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.accessibility.shortcutchooser.ui.composable

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.android.compose.PlatformOutlinedButton
import com.android.compose.dialog.AlertDialogContent
import com.android.compose.theme.PlatformTheme
import com.android.systemui.accessibility.shortcutchooser.shared.model.AccessibilityTargetModel
import com.android.systemui.res.R

@Composable
fun NavBarMoreOptionsDialogContent(
    targets: List<AccessibilityTargetModel>,
    selectedTarget: String?,
    onDoneClick: () -> Unit,
    onTargetSelected: (AccessibilityTargetModel) -> Unit,
    modifier: Modifier = Modifier,
) {
    PlatformTheme {
        AlertDialogContent(
            modifier = modifier,
            title = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.accessibility_nav_bar_more_options_title),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.accessibility_nav_bar_more_options_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            },
            content = {
                ShortcutTargetsList(
                    targets = targets,
                    modifier = Modifier.heightIn(max = 400.dp).selectableGroup(),
                ) { target ->
                    ShortcutSingleSelectRow(
                        target = target,
                        selected = target.targetName == selectedTarget,
                        onClick = { onTargetSelected(target) },
                    )
                }
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
