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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.android.systemui.res.R
import com.android.systemui.screencapture.common.ui.compose.LoadingIcon
import com.android.systemui.screencapture.common.ui.compose.loadIcon
import com.android.systemui.screencapture.common.ui.viewmodel.AppContentsViewModel
import com.android.systemui.screencapture.common.ui.viewmodel.DisplaysViewModel
import com.android.systemui.screencapture.common.ui.viewmodel.RecentTasksViewModel
import com.android.systemui.screencapture.common.ui.viewmodel.TargetsViewModel
import com.android.systemui.screencapture.sharescreen.ui.viewmodel.ScreenCaptureShareScreenViewModel

@Composable
fun ShareContentSelector(shareScreenViewModel: ScreenCaptureShareScreenViewModel) {
    val targetsViewModel = shareScreenViewModel.currentTargetsModel
    val hasIcons = targetsViewModel !is DisplaysViewModel
    val listWidth = if (hasIcons) 250.dp else 214.dp
    val dialogWidth = if (hasIcons) 524.dp else 488.dp

    Surface(color = MaterialTheme.colorScheme.surfaceBright, shape = RoundedCornerShape(20.dp)) {
        Column(
            modifier = Modifier.width(dialogWidth).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val selectedItem by targetsViewModel.selectedTarget
            Text(
                text =
                    stringResource(
                        when (targetsViewModel) {
                            is AppContentsViewModel -> R.string.screen_share_tab_sharing_title
                            is RecentTasksViewModel ->
                                R.string.screen_share_app_window_sharing_title
                            is DisplaysViewModel ->
                                R.string.screen_share_entire_screen_sharing_title
                            else -> throw IllegalArgumentException("Unknown TargetsViewModel type")
                        }
                    ),
                modifier = Modifier.padding(horizontal = 4.dp).fillMaxWidth(),
                style = MaterialTheme.typography.titleMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                // The sharing content item list.
                ShareContentList(viewModel = targetsViewModel, modifier = Modifier.width(listWidth))
                ItemPreview(
                    preview = selectedItem?.thumbnail?.getOrNull()?.asImageBitmap(),
                    modifier = Modifier.height(140.dp).width(230.dp),
                    itemSelected = selectedItem != null,
                    text =
                        stringResource(
                            when (targetsViewModel) {
                                is AppContentsViewModel ->
                                    R.string.screen_share_no_select_tab_thumbnail
                                is RecentTasksViewModel ->
                                    R.string.screen_share_no_select_app_thumbnail
                                is DisplaysViewModel ->
                                    R.string.screen_share_no_select_display_thumbnail
                                else ->
                                    throw IllegalArgumentException("Unknown TargetsViewModel type")
                            }
                        ),
                )
            }
            DisclaimerText(targetsViewModel, shareScreenViewModel.requestingAppName)
            if (targetsViewModel is AppContentsViewModel && shareScreenViewModel.isAudioRequested) {
                AudioSwitch(targetsViewModel)
            }
        }
    }
}

@Composable
private fun ItemPreview(
    preview: ImageBitmap?,
    modifier: Modifier = Modifier,
    itemSelected: Boolean,
    text: String,
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
                text = text,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                style = MaterialTheme.typography.labelMedium.copy(textAlign = TextAlign.Center),
                modifier = Modifier.padding(horizontal = 52.dp),
            )
        }
    }
}

@Composable
private fun DisclaimerText(targetsViewModel: TargetsViewModel, requestingAppName: String) {
    Text(
        text =
            stringResource(
                when (targetsViewModel) {
                    is AppContentsViewModel -> R.string.screen_share_disclaimer_tab_sharing
                    is RecentTasksViewModel -> R.string.screen_share_disclaimer_app_sharing
                    is DisplaysViewModel -> R.string.screen_share_disclaimer_full_screen_sharing
                    else -> throw IllegalArgumentException("Unknown TargetsViewModel type")
                },
                requestingAppName,
            ),
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier.padding(horizontal = 4.dp).fillMaxWidth(),
    )
}

@Composable
private fun AudioSwitch(targetsViewModel: TargetsViewModel) {
    val checked by targetsViewModel.captureAudio

    val audioSwitchA11yDescription =
        stringResource(
            if (checked) R.string.screen_share_a11y_tab_audio_on
            else R.string.screen_share_a11y_tab_audio_off
        )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth(),
    ) {
        LoadingIcon(
            icon =
                loadIcon(
                        viewModel = targetsViewModel,
                        resId = R.drawable.ic_speaker_on,
                        contentDescription = null,
                    )
                    .value,
            modifier = Modifier.size(24.dp).padding(2.dp),
        )
        Text(
            text = stringResource(R.string.screen_share_audio_sharing_text),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.width(420.dp),
        )
        Switch(
            checked = checked,
            onCheckedChange = targetsViewModel::setCaptureAudio,
            modifier =
                Modifier.height(20.dp).width(52.dp).semantics {
                    this.contentDescription = audioSwitchA11yDescription
                },
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
