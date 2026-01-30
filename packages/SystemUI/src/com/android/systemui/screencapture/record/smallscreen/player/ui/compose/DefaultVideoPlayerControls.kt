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

package com.android.systemui.screencapture.record.smallscreen.player.ui.compose

import android.text.format.DateUtils
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.android.systemui.common.shared.model.ContentDescription
import com.android.systemui.res.R
import com.android.systemui.screencapture.common.ui.compose.LoadingIcon
import com.android.systemui.screencapture.common.ui.compose.loadIcon
import com.android.systemui.screencapture.common.ui.viewmodel.DrawableLoaderViewModel
import com.android.systemui.screencapture.record.smallscreen.player.ui.viewmodel.VideoPlayerControlsViewModel
import kotlin.math.roundToInt

@Composable
fun DefaultVideoPlayerControls(
    viewModel: VideoPlayerControlsViewModel,
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    contrastColor: Color = Color.Black.copy(alpha = 0.5f),
) {
    val backgroundBrush = Brush.verticalGradient(colors = listOf(Color.Transparent, contrastColor))
    Column(
        verticalArrangement = Arrangement.Bottom,
        modifier = modifier.heightIn(min = 164.dp).drawBehind { drawRect(backgroundBrush) },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.padding(horizontal = 4.dp).fillMaxWidth(),
        ) {
            PlayerButton(
                viewModel = viewModel,
                checked = viewModel.playing,
                onCheckedChanged = { viewModel.updatePlaying(it) },
                iconRes =
                    if (viewModel.playing) {
                        R.drawable.ic_media_pause_button
                    } else {
                        R.drawable.ic_media_play_button
                    },
                labelRes =
                    if (viewModel.playing) {
                        R.string.screen_record_pause
                    } else {
                        R.string.screen_record_play
                    },
                color = color,
            )
            ElapsedTimeText(
                currentPositionMillis = viewModel.videoPositionMillis,
                durationMillis = viewModel.videoDurationMillis,
                color = color,
            )
            PlayerButton(
                viewModel = viewModel,
                checked = viewModel.muted,
                onCheckedChanged = { viewModel.updateMuted(it) },
                iconRes =
                    if (viewModel.muted) {
                        R.drawable.ic_speaker_mute
                    } else {
                        R.drawable.ic_speaker_on
                    },
                labelRes =
                    if (viewModel.muted) {
                        R.string.screen_record_mute
                    } else {
                        R.string.screen_record_unmute
                    },
                color = color,
            )
        }

        val animatedValue by animateFloatAsState(viewModel.videoPositionMillis.toFloat())
        Slider(
            value = animatedValue,
            valueRange = 0f..viewModel.videoDurationMillis.toFloat(),
            onValueChange = { viewModel.seek(it.roundToInt()) },
            onValueChangeFinished = { viewModel.seekFinished() },
            colors =
                SliderDefaults.colors(
                    activeTrackColor = color,
                    inactiveTrackColor = color.copy(alpha = 0.38f),
                    inactiveTickColor = color,
                    thumbColor = color,
                ),
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Composable
private fun PlayerButton(
    viewModel: DrawableLoaderViewModel,
    checked: Boolean,
    onCheckedChanged: (Boolean) -> Unit,
    @DrawableRes iconRes: Int,
    @StringRes labelRes: Int,
    color: Color,
    modifier: Modifier = Modifier,
) {
    TextButton(
        onClick = { onCheckedChanged(!checked) },
        colors = ButtonDefaults.textButtonColors(contentColor = color),
        modifier = modifier.size(48.dp),
    ) {
        LoadingIcon(
            icon =
                loadIcon(
                        viewModel = viewModel,
                        resId = iconRes,
                        contentDescription = ContentDescription.Loaded(stringResource(labelRes)),
                    )
                    .value,
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun ElapsedTimeText(
    currentPositionMillis: Int,
    durationMillis: Int,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val elapsedTime = format(currentPositionMillis)
    val duration = format(durationMillis)
    Text(
        text =
            stringResource(
                R.string.screen_record_video_preview_elapsed_time_template,
                elapsedTime,
                duration,
            ),
        style = MaterialTheme.typography.labelMedium.copy(),
        textAlign = TextAlign.Center,
        color = color,
        modifier = modifier,
    )
}

private fun format(durationMillis: Int): String {
    return DateUtils.formatElapsedTime(durationMillis / DateUtils.SECOND_IN_MILLIS)
}
