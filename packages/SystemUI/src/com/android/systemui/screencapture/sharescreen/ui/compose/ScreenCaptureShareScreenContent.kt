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

package com.android.systemui.screencapture.sharescreen.ui.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.android.systemui.lifecycle.rememberViewModel
import com.android.systemui.screencapture.common.ui.compose.ScreenCaptureContent
import com.android.systemui.screencapture.sharescreen.largescreen.ui.compose.LargeScreenCaptureShareScreenContent
import com.android.systemui.screencapture.sharescreen.smallscreen.ui.compose.SmallScreenCaptureShareScreenContent
import com.android.systemui.screencapture.sharescreen.ui.viewmodel.ScreenCaptureShareScreenViewModel
import dagger.Lazy
import javax.inject.Inject

/** Entry point for Screen Share composable content. */
class ScreenCaptureShareScreenContent
@Inject
constructor(
    private val shareScreenViewModelFactory: ScreenCaptureShareScreenViewModel.Factory,
    private val largeShareScreenContent: Lazy<LargeScreenCaptureShareScreenContent>,
    private val smallShareScreenContent: Lazy<SmallScreenCaptureShareScreenContent>,
) : ScreenCaptureContent {
    @Composable
    override fun Content() {
        val viewModel =
            rememberViewModel("ScreenCaptureShareScreenContent#ScreenCaptureShareScreenViewModel") {
                shareScreenViewModelFactory.create()
            }
        val content: ScreenCaptureContent? by
            remember(viewModel.isLargeScreen) {
                derivedStateOf {
                    when (viewModel.isLargeScreen) {
                        true -> largeShareScreenContent.get()
                        false -> smallShareScreenContent.get()
                        else -> null
                    }
                }
            }
        content?.Content()
    }
}
