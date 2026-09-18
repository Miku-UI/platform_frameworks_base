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

package com.android.systemui.privacy.ui.compose

import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.android.compose.animation.Expandable
import com.android.compose.animation.rememberExpandableController
import com.android.compose.theme.LocalAndroidColorScheme
import com.android.compose.theme.PlatformTheme
import com.android.systemui.animation.Expandable
import com.android.systemui.privacy.PrivacyType
import com.android.systemui.privacy.ui.compose.PrivacyChipContainer.Colors
import com.android.systemui.privacy.ui.compose.PrivacyChipContainer.Dimensions.compressedPadding
import com.android.systemui.privacy.ui.compose.PrivacyChipContainer.Dimensions.iconSize
import com.android.systemui.privacy.ui.compose.PrivacyChipContainer.Dimensions.interItemSpacing
import com.android.systemui.privacy.ui.compose.PrivacyChipContainer.Dimensions.privacyTextExtraPadding
import com.android.systemui.privacy.ui.compose.PrivacyChipContainer.Dimensions.regularPadding
import com.android.systemui.res.R

/**
 * Privacy chip container.
 *
 * It separates location from the other types.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PrivacyChipContainer(
    privacyTypes: Set<PrivacyType>,
    /** Whether to show a leading Privacy string before the chips */
    showPrivacyText: Boolean,
    modifier: Modifier = Modifier,
    /** Whether to use smaller padding */
    compressedPaddings: Boolean = false,
    /** Expandable to use for click animation. */
    expandable: Expandable = remember { Expandable() },
) {
    if (privacyTypes.isEmpty()) {
        return
    }
    PlatformTheme {
        val nonLocation =
            remember(privacyTypes) {
                privacyTypes.filterNot { it == PrivacyType.TYPE_LOCATION }.toSet()
            }
        val hasLocation =
            remember(privacyTypes) { privacyTypes.contains(PrivacyType.TYPE_LOCATION) }
        Expandable(
            controller =
                rememberExpandableController(color = Colors.container, shape = CircleShape),
            expandable = expandable,
            defaultMinSize = false,
            useModifierBasedImplementation = true,
            modifier = modifier,
        ) {
            Row(
                modifier =
                    Modifier.padding(if (compressedPaddings) compressedPadding else regularPadding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = spacedBy(interItemSpacing),
            ) {
                if (showPrivacyText) {
                    key("privacy-text") {
                        Text(
                            text = stringResource(R.string.privacy_chip_container_leading_title),
                            color = Colors.chevronAndText,
                            style = MaterialTheme.typography.labelMediumEmphasized,
                            modifier = Modifier.padding(horizontal = privacyTextExtraPadding),
                        )
                    }
                }
                key("nonLocation") { NonLocationPrivacyChip(nonLocation) }
                if (hasLocation) {
                    key("location") { LocationPrivacyChip() }
                }
                key("chevron") {
                    Icon(
                        painter = painterResource(R.drawable.ic_chevron_end_round_filed),
                        contentDescription = null,
                        tint = Colors.chevronAndText,
                        modifier = Modifier.size(iconSize),
                    )
                }
            }
        }
    }
}

private object PrivacyChipContainer {
    object Dimensions {
        val privacyTextExtraPadding = 4.dp
        val interItemSpacing = 2.dp
        val compressedPadding = PaddingValues(start = 2.dp, end = 4.dp, top = 2.dp, bottom = 2.dp)
        val regularPadding = PaddingValues(start = 4.dp, end = 6.dp, top = 4.dp, bottom = 4.dp)
        val iconSize = 16.dp
    }

    object Colors {
        val container: Color
            @ReadOnlyComposable @Composable get() = LocalAndroidColorScheme.current.surfaceEffect1

        val chevronAndText: Color
            @ReadOnlyComposable @Composable get() = MaterialTheme.colorScheme.onSurface
    }
}
