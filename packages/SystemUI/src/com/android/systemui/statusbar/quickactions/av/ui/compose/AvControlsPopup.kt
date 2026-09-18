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

package com.android.systemui.statusbar.quickactions.av.ui.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.android.systemui.shade.ui.ShadeColors
import com.android.systemui.statusbar.quickactions.av.ui.viewmodel.AvControlsPopupViewModel
import com.android.systemui.statusbar.quickactions.av.ui.viewmodel.PageType

/** Displays a popup containing the Audio, Video and Privacy controls. */
@Composable
fun AvControlsChipPopup(viewModel: AvControlsPopupViewModel, modifier: Modifier = Modifier) {
    // check(Flags.desktopAvControlsPopup()) { "Flag desktop_av_controls_popup is not enabled." }

    var screen by remember { mutableStateOf(PageType.MAIN) }
    val setCurrentPage: (PageType) -> Unit = { screen = it }
    val returnToMainPage = { screen = PageType.MAIN }

    Surface(
        // TODO(469370207): consider wrapping the hardcoded dimensions in s resource
        modifier = modifier.width(376.dp).clip(shape = RoundedCornerShape(36.dp)).fillMaxWidth(),
        // color = Color(ShadeColors.shadePanelScrimBehind(LocalContext.current)),
        color =
            Color(
                ShadeColors.shadePanel(
                    LocalContext.current,
                    blurSupported = false,
                    withScrim = false,
                )
            ),
    ) {
        Box(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            when (screen) {
                PageType.MAIN -> {
                    AvControlsPanelContent(
                        viewModelFactory = viewModel.avControlsPanelContentViewModelFactory,
                        setCurrentPage = setCurrentPage,
                    )
                }

                PageType.SENSOR_ACTIVITY -> {
                    SensorActivityDrillIn(
                        viewModelFactory = viewModel.sensorActivityViewModelFactory,
                        setCurrentPage = setCurrentPage,
                    )
                }

                PageType.BLUR -> {
                    BlurDrillIn(
                        viewModelFactory = viewModel.blurDrillInViewModelFactory,
                        returnToMainPage = returnToMainPage,
                    )
                }

                PageType.STUDIO_LOOK -> {
                    StudioLookDrillIn(
                        viewModelFactory = viewModel.studioLookDrillInViewModelFactory,
                        returnToMainPage = returnToMainPage,
                    )
                }
            }
        }
    }
}
