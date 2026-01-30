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

import android.net.Uri
import androidx.compose.foundation.AndroidExternalSurface
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.android.systemui.lifecycle.rememberViewModel
import com.android.systemui.screencapture.record.smallscreen.player.ui.viewmodel.VideoPlayerControlsViewModel
import com.android.systemui.screencapture.record.smallscreen.player.ui.viewmodel.VideoPlayerViewModel
import javax.inject.Inject

class VideoPlayer
@Inject
constructor(
    private val videoPlayerViewModelFactory: VideoPlayerViewModel.Factory,
    private val videoPlayerControlsViewModelFactory: VideoPlayerControlsViewModel.Factory,
) {
    @Composable
    fun Content(
        uri: Uri,
        modifier: Modifier = Modifier,
        videoControls: @Composable BoxScope.(vm: VideoPlayerControlsViewModel) -> Unit = { vm ->
            DefaultVideoPlayerControls(vm, Modifier.align(Alignment.BottomCenter))
        },
    ) {
        val playerViewModel =
            rememberViewModel(traceName = "VideoPlayer#viewModel", key = uri) {
                videoPlayerViewModelFactory.create(uri)
            }
        val player = playerViewModel.player
        Box(contentAlignment = Alignment.Center, modifier = modifier) {
            val aspectRatioModifier: Modifier =
                playerViewModel.videoAspectRatio?.let { Modifier.aspectRatio(it) } ?: Modifier
            Box(modifier = aspectRatioModifier) {
                AndroidExternalSurface(
                    onInit = {
                        onSurface { surface, _, _ ->
                            playerViewModel.onSurfaceCreated(surface)
                            surface.onDestroyed { playerViewModel.onSurfaceDestroyed() }
                        }
                    }
                )

                if (player != null) {
                    val viewModel =
                        rememberViewModel(
                            traceName = "VideoPlayer#controlsViewModel",
                            key = player,
                        ) {
                            videoPlayerControlsViewModelFactory.create(player)
                        }
                    videoControls(viewModel)
                }
            }
        }
    }
}
