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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.systemui.screencapture.common.ui.viewmodel.RecentTaskViewModel
import com.android.systemui.screencapture.sharescreen.largescreen.ui.viewmodel.AudioSwitchViewModel
import com.android.systemui.screencapture.sharescreen.largescreen.ui.viewmodel.PreShareToolbarViewModel
import com.android.systemui.screencapture.sharescreen.largescreen.ui.viewmodel.ShareContentListViewModel

/** Main component for the screen share UI. */
@Composable
fun PreShareUI(
    preShareToolbarViewModel: PreShareToolbarViewModel,
    shareContentListViewModel: ShareContentListViewModel,
    audioSwitchViewModel: AudioSwitchViewModel,
    recentTaskViewModelFactory: RecentTaskViewModel.Factory,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(560.dp),
    ) {
        PreShareToolbar(
            preShareToolbarViewModel = preShareToolbarViewModel,
            expanded = true,
            onCloseClick = { preShareToolbarViewModel.onCloseClicked() },
            shareButtonEnabled = shareContentListViewModel.selectedRecentTaskViewModel != null,
        )
        ShareContentSelector(
            shareContentListViewModel = shareContentListViewModel,
            recentTaskViewModelFactory = recentTaskViewModelFactory,
            audioSwitchViewModel = audioSwitchViewModel,
        )
    }
}
