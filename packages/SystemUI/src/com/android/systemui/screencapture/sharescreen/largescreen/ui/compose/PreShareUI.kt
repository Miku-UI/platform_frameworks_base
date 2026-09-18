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

package com.android.systemui.screencapture.sharescreen.largescreen.ui.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import com.android.systemui.screencapture.sharescreen.ui.viewmodel.ScreenCaptureShareScreenViewModel

/** Main component for the screen share UI. */
@Composable
fun PreShareUI(shareScreenViewModel: ScreenCaptureShareScreenViewModel) {
    Box(
        contentAlignment = Alignment.TopCenter,
        modifier =
            Modifier.fillMaxSize().padding(top = 16.dp).semantics { testTagsAsResourceId = true },
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.wrapContentWidth(),
        ) {
            val targetsViewModel = shareScreenViewModel.currentTargetsModel

            PreShareToolbar(
                shareScreenViewModel = shareScreenViewModel,
                expanded = true,
                onCloseClick = { shareScreenViewModel.onCloseClicked() },
                shareButtonEnabled = targetsViewModel.selectedTarget.value != null,
            )
            ShareContentSelector(shareScreenViewModel)
        }
    }
}
