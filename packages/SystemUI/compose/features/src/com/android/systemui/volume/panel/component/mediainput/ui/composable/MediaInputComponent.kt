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

package com.android.systemui.volume.panel.component.mediainput.ui.composable

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.android.compose.animation.Expandable
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.common.ui.compose.Icon
import com.android.systemui.lifecycle.rememberViewModel
import com.android.systemui.qs.panels.ui.compose.infinitegrid.CommonTileDefaults.TileDetailsEntryTightCornerRadius
import com.android.systemui.qs.ui.compose.borderOnFocus
import com.android.systemui.res.R
import com.android.systemui.volume.panel.component.mediainput.ui.viewmodel.MediaInputViewModel
import com.android.systemui.volume.panel.component.mediastream.ui.composable.MediaStreamStyle
import com.android.systemui.volume.panel.ui.composable.ComposeVolumePanelUiComponent
import com.android.systemui.volume.panel.ui.composable.VolumePanelComposeScope
import javax.inject.Inject

class MediaInputComponent
@Inject
constructor(private val viewModelFactory: MediaInputViewModel.Factory) :
    ComposeVolumePanelUiComponent {

    @Composable
    override fun VolumePanelComposeScope.Content(modifier: Modifier) {
        val viewModel =
            rememberViewModel("MediaInputComponent-viewModel") { viewModelFactory.create() }
        val clickLabel = stringResource(R.string.volume_panel_enter_media_output_settings)

        if (!viewModel.hasInputDevice) {
            return
        }

        val style = MediaStreamStyle.style(isExpandedAudioTileDetailsView = true)

        Expandable(
            modifier =
                modifier
                    .borderOnFocus(
                        MaterialTheme.colorScheme.secondary,
                        CornerSize(TileDetailsEntryTightCornerRadius),
                    )
                    .fillMaxWidth()
                    // In most cases the height is expected to be equal to the height dimension's
                    // value, but it is set as the minimum here so that the tile can resize if
                    // necessary for larger font or display sizes.
                    .heightIn(min = dimensionResource(R.dimen.volume_panel_audio_tile_height))
                    .semantics {
                        role = Role.Button
                        liveRegion = LiveRegionMode.Polite
                        this.onClick(label = clickLabel) {
                            viewModel.onBarClick(null)
                            true
                        }
                    },
            color = style.backgroundColor,
            shape = RoundedCornerShape(12.dp),
            useModifierBasedImplementation = true,
            onClick = { viewModel.onBarClick(it) },
        ) { _ ->
            Row(
                modifier = Modifier.wrapContentHeight(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                viewModel.connectedDeviceName?.let {
                    ConnectedDeviceText(
                        checkNotNull(viewModel.connectedDeviceName),
                        Modifier.weight(1f).padding(start = style.paddingStart),
                        style,
                    )
                }

                ConnectedDeviceIcon(icon = viewModel.connectedDeviceIcon, style = style)
            }
        }
    }

    @Composable
    private fun ConnectedDeviceText(
        deviceName: String,
        modifier: Modifier = Modifier,
        style: MediaStreamStyle,
    ) {
        val currentDeviceLabel = stringResource(R.string.quick_settings_audio_current_input)

        Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                modifier = Modifier.basicMarquee(),
                text = currentDeviceLabel,
                style = style.labelTextStyle,
                maxLines = 1,
            )
            Text(
                modifier = Modifier.basicMarquee(),
                text = deviceName,
                style = style.deviceNameTextStyle,
                maxLines = 1,
            )
        }
    }

    @Composable
    private fun ConnectedDeviceIcon(
        icon: Icon,
        modifier: Modifier = Modifier,
        style: MediaStreamStyle,
    ) {
        Box(modifier = modifier.size(56.dp), contentAlignment = Alignment.Center) {
            Icon(icon = icon, tint = style.deviceIconColor, modifier = Modifier.size(24.dp))
        }
    }
}
