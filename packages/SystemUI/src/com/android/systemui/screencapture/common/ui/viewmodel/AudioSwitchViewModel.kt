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

package com.android.systemui.screencapture.common.ui.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import javax.inject.Inject

/**
 * Interface to mark your ViewModels as ones capable of managing an audio capture switch.
 *
 * Example:
 * ```
 * class FooViewModel(vm: AudioSwitchViewModelImpl): AudioSwitchViewModel by vm
 * ```
 *
 * And then in compose:
 * ```
 * @Composable
 * fun Foo(viewModel: FooViewModel) {
 *   Switch(checked = viewModel.captureAudio.value, onCheckedChange = viewModel::setCaptureAudio)
 * }
 * ```
 */
interface AudioSwitchViewModel {
    /** Whether target audio should be captured. */
    val captureAudio: State<Boolean>

    /** Sets whether target audio should be captured. */
    fun setCaptureAudio(captureAudio: Boolean)
}

class AudioSwitchViewModelImpl @Inject constructor() : AudioSwitchViewModel {

    private val _captureAudio = mutableStateOf(false)
    override val captureAudio: State<Boolean> = _captureAudio

    override fun setCaptureAudio(captureAudio: Boolean) {
        _captureAudio.value = captureAudio
    }
}
