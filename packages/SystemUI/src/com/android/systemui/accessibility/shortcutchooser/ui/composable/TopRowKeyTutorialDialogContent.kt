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

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.android.compose.PlatformButton
import com.android.compose.PlatformOutlinedButton
import com.android.compose.dialog.AlertDialogContent
import com.android.compose.theme.PlatformTheme
import com.android.systemui.res.R

/**
 * The content used by `SystemUIDialogFactory.create` to create tutorial dialog for the top-row key.
 *
 * @param onAddFeaturesClick The callback for the positive button
 * @param onCancelClick The callback for the negative button
 */
@Composable
fun TopRowKeyTutorialDialogContent(onAddFeaturesClick: () -> Unit, onCancelClick: () -> Unit) {
    PlatformTheme {
        AlertDialogContent(
            title = {
                Text(
                    stringResource(
                        R.string.accessibility_shortcutchooser_toprow_tutorial_dialog_title
                    )
                )
            },
            content = {
                Text(
                    stringResource(
                        R.string.accessibility_shortcutchooser_toprow_tutorial_dialog_content
                    )
                )
            },
            icon = {
                Icon(
                    painter = painterResource(R.drawable.ic_qs_category_accessibility),
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                )
            },
            negativeButton = {
                PlatformOutlinedButton(
                    onClick = onCancelClick,
                    modifier = Modifier.testTag("cancel_button"),
                ) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
            positiveButton = {
                PlatformButton(
                    onClick = onAddFeaturesClick,
                    modifier = Modifier.testTag("add_features_button"),
                ) {
                    Text(
                        stringResource(
                            R.string
                                .accessibility_shortcutchooser_toprow_tutorial_dialog_positive_button_text
                        )
                    )
                }
            },
        )
    }
}
