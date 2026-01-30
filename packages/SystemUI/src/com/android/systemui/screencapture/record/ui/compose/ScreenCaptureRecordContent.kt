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

package com.android.systemui.screencapture.record.ui.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.android.systemui.lifecycle.rememberViewModel
import com.android.systemui.screencapture.common.ScreenCaptureUiScope
import com.android.systemui.screencapture.common.ui.compose.ScreenCaptureContent
import com.android.systemui.screencapture.record.largescreen.ui.compose.LargeScreenCaptureContent
import com.android.systemui.screencapture.record.smallscreen.ui.compose.SmallScreenCaptureRecordContent
import com.android.systemui.screencapture.record.ui.viewmodel.ScreenCaptureRecordViewModel
import dagger.Lazy
import javax.inject.Inject

/** Entry point for Record composable content. */
@ScreenCaptureUiScope
class ScreenCaptureRecordContent
@Inject
constructor(
    private val screenCaptureRecordViewModelFactory: ScreenCaptureRecordViewModel.Factory,
    private val largeScreenCaptureContent: Lazy<LargeScreenCaptureContent>,
    private val smallScreenCaptureRecordContent: Lazy<SmallScreenCaptureRecordContent>,
) : ScreenCaptureContent {

    @Composable
    override fun Content() {
        val viewModel =
            rememberViewModel("ScreenCaptureRecordContent#ScreenCaptureRecordViewModel") {
                screenCaptureRecordViewModelFactory.create()
            }
        val content: ScreenCaptureContent? by
            remember(viewModel.isLargeScreen) {
                derivedStateOf {
                    when (viewModel.isLargeScreen) {
                        true -> largeScreenCaptureContent.get()
                        false -> smallScreenCaptureRecordContent.get()
                        else -> null
                    }
                }
            }
        content?.Content()
    }
}
