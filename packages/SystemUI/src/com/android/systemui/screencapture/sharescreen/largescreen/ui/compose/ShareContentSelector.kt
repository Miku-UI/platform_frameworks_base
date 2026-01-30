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

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.android.systemui.res.R
import com.android.systemui.screencapture.common.ui.compose.LoadingIcon
import com.android.systemui.screencapture.common.ui.compose.loadIcon
import com.android.systemui.screencapture.common.ui.viewmodel.RecentTaskViewModel
import com.android.systemui.screencapture.sharescreen.largescreen.ui.viewmodel.AudioSwitchViewModel
import com.android.systemui.screencapture.sharescreen.largescreen.ui.viewmodel.ShareContentListViewModel

@Composable
fun ShareContentSelector(
    shareContentListViewModel: ShareContentListViewModel,
    audioSwitchViewModel: AudioSwitchViewModel,
    recentTaskViewModelFactory: RecentTaskViewModel.Factory,
) {
    val selectedRecentTaskViewModel = shareContentListViewModel.selectedRecentTaskViewModel
    val itemSelected = selectedRecentTaskViewModel != null

    Surface(color = MaterialTheme.colorScheme.surfaceBright, shape = RoundedCornerShape(20.dp)) {
        Column(
            modifier =
                Modifier.width(560.dp)
                    .padding(start = 10.dp, top = 14.dp, end = 10.dp, bottom = 2.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Share an app",
                modifier = Modifier.padding(start = 8.dp, end = 8.dp).height(24.dp).fillMaxWidth(),
                style = MaterialTheme.typography.titleMedium,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(start = 4.dp, end = 4.dp),
            ) {
                // The sharing content item list.
                ShareContentList(
                    viewModel = shareContentListViewModel,
                    recentTaskViewModelFactory = recentTaskViewModelFactory,
                    selectedRecentTaskViewModel = selectedRecentTaskViewModel,
                )
                ItemPreview(
                    preview = selectedRecentTaskViewModel?.thumbnail?.getOrNull()?.asImageBitmap(),
                    modifier = Modifier.weight(1f).height(140.dp).width(230.dp),
                    itemSelected = itemSelected,
                )
            }
            DisclaimerText()
            AudioSwitch(audioSwitchViewModel, selectedRecentTaskViewModel)
        }
    }
}

@Composable
private fun ItemPreview(
    preview: ImageBitmap?,
    modifier: Modifier = Modifier,
    itemSelected: Boolean,
) {
    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (itemSelected) {
            if (preview != null) {
                Image(
                    bitmap = preview,
                    contentDescription = null,
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Fit,
                )
            }
        } else {
            Text(
                text = stringResource(R.string.screen_share_no_select_app_thumbnail),
                color = MaterialTheme.colorScheme.primaryContainer,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun DisclaimerText() {
    Text(
        text =
            "Disclaimer When you’re sharing your entire screen, anything shown on your screen" +
                " is recorded. So be careful with things like passwords, payment details," +
                " messages, photos, and audio & video.",
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier.padding(start = 8.dp, end = 8.dp).fillMaxWidth(),
    )
}

@Composable
private fun AudioSwitch(
    audioSwitchViewModel: AudioSwitchViewModel,
    selectedRecentTaskViewModel: RecentTaskViewModel?,
) {
    val checked = audioSwitchViewModel.audioSwitchChecked

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.padding(4.dp, bottom = 12.dp).height(24.dp).fillMaxWidth(),
    ) {
        LoadingIcon(
            icon =
                loadIcon(
                        viewModel = audioSwitchViewModel,
                        resId = R.drawable.ic_speaker_on,
                        contentDescription = null,
                    )
                    .value,
            modifier = Modifier.size(20.dp).padding(2.dp),
        )
        Text(
            text = stringResource(R.string.screen_share_app_audio_sharing),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.width(452.dp),
        )
        Switch(
            checked = checked,
            onCheckedChange = {
                audioSwitchViewModel.audioSwitchChecked = !audioSwitchViewModel.audioSwitchChecked
            },
            enabled = selectedRecentTaskViewModel != null,
            thumbContent =
                if (checked) {
                    {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            modifier = Modifier.size(SwitchDefaults.IconSize),
                        )
                    }
                } else {
                    null
                },
        )
    }
}
