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

package com.android.systemui.qs.tiles.dialog

import android.view.LayoutInflater
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.android.systemui.qs.tiles.dialog.AudioDetailsViewModel.ContentViewModel.DefaultPageViewModel
import com.android.systemui.qs.tiles.dialog.AudioDetailsViewModel.ContentViewModel.SwitcherPageViewModel
import com.android.systemui.res.R
import com.android.systemui.volume.panel.ui.composable.VolumePanelRoot

@Composable
fun AudioDetailsContent(audioDetailsViewModel: AudioDetailsViewModel) {
    LaunchedEffect(Unit) { audioDetailsViewModel.activate() }
    when (val currentViewModel = audioDetailsViewModel.contentViewModel) {
        is DefaultPageViewModel ->
            Box(modifier = Modifier.fillMaxWidth().height(600.dp)) {
                VolumePanelRoot(viewModel = currentViewModel.viewModel)
            }
        is SwitcherPageViewModel ->
            AndroidView(
                factory = { context ->
                    // TODO(b/378513663): Implement the switcher page view
                    LayoutInflater.from(context).inflate(R.layout.media_output_dialog, null)
                }
            )
        null -> {}
    }
}
