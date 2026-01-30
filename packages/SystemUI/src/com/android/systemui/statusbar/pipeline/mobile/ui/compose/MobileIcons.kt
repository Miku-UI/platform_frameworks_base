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

package com.android.systemui.statusbar.pipeline.mobile.ui.compose

import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.systemui.statusbar.pipeline.mobile.ui.viewmodel.MobileIconsViewModel
import com.android.systemui.statusbar.pipeline.mobile.ui.viewmodel.StackedMobileIconViewModel
import com.android.systemui.statusbar.pipeline.shared.ui.composable.StackedMobileIcon

/** Renders the mobile icons for the status bar. */
@Composable
fun MobileIcons(
    viewModel: MobileIconsViewModel,
    stackedMobileIconViewModel: StackedMobileIconViewModel,
    modifier: Modifier = Modifier,
) {
    val isStackable by viewModel.isStackable.collectAsStateWithLifecycle()
    if (isStackable) {
        StackedMobileIcon(viewModel = stackedMobileIconViewModel, modifier = modifier)
    } else {
        val mobileSubViewModels by viewModel.mobileSubViewModels.collectAsStateWithLifecycle()
        val iconPaddingSp = 4.sp
        val iconSpacingSp = 2.sp
        val padding = with(LocalDensity.current) { iconPaddingSp.toDp() }
        val spacing = with(LocalDensity.current) { iconSpacingSp.toDp() }

        Row(
            horizontalArrangement = spacedBy(spacing),
            verticalAlignment = Alignment.CenterVertically,
            modifier = modifier.padding(horizontal = padding),
        ) {
            mobileSubViewModels.forEach { mobileViewModel ->
                MobileIcon(viewModel = mobileViewModel)
            }
        }
    }
}
