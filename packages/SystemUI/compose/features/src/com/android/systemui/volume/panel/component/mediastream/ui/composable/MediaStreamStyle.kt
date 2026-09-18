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

package com.android.systemui.volume.panel.component.mediastream.ui.composable

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.compose.theme.LocalAndroidColorScheme
import com.android.systemui.Flags
import com.android.systemui.shared.system.BlurUtils.isVolumeAndPowerBlurEnabled

/** Shared common styles that will be used by MediaInputComponent and MediaOutputComponent. */
data class MediaStreamStyle(
    val paddingStart: Dp,
    val backgroundColor: Color,
    val labelTextStyle: TextStyle,
    val deviceNameTextStyle: TextStyle,
    val deviceIconColor: Color,
) {
    companion object {
        @Composable
        fun style(isExpandedAudioTileDetailsView: Boolean): MediaStreamStyle {
            return if (isExpandedAudioTileDetailsView) {
                MediaStreamStyle(
                    paddingStart = 18.dp,
                    backgroundColor = LocalAndroidColorScheme.current.surfaceEffect1,
                    labelTextStyle =
                        MaterialTheme.typography.labelMedium.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                    deviceNameTextStyle =
                        MaterialTheme.typography.titleSmall.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                    deviceIconColor = MaterialTheme.colorScheme.onSurface,
                )
            } else {
                MediaStreamStyle(
                    paddingStart = 0.dp,
                    backgroundColor =
                        if (isVolumeAndPowerBlurEnabled()) {
                            Color.Transparent
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                    labelTextStyle =
                        MaterialTheme.typography.labelMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                    deviceNameTextStyle =
                        MaterialTheme.typography.titleMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                    deviceIconColor = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
