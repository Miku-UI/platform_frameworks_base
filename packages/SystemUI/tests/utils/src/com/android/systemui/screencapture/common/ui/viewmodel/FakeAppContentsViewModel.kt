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

import android.media.projection.IAppContentProjectionCallback
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import com.android.systemui.screencapture.common.domain.model.ScreenCaptureAppContent
import com.android.systemui.screencapture.common.domain.model.TargetModel
import java.lang.ref.WeakReference
import kotlinx.coroutines.awaitCancellation

class FakeAppContentsViewModel(
    var fakeViewModelFactory: AppContentViewModel.Factory,
    fakeDrawableLoaderViewModel: DrawableLoaderViewModel,
    fakeAudioSwitchViewModel: AudioSwitchViewModel = AudioSwitchViewModelImpl(),
) :
    AppContentsViewModel,
    DrawableLoaderViewModel by fakeDrawableLoaderViewModel,
    AudioSwitchViewModel by fakeAudioSwitchViewModel {

    private val fakeAppContents = mutableStateOf<List<ScreenCaptureAppContent>?>(null)
    val fakeProjectionCallbacks =
        mutableMapOf<String, WeakReference<IAppContentProjectionCallback>>()

    override val targets: State<List<ScreenCaptureAppContent>?> = fakeAppContents

    private val _selectedTarget = mutableStateOf<AppContentViewModel?>(null)
    override val selectedTarget: State<AppContentViewModel?> = _selectedTarget

    override val projectionCallback: State<WeakReference<IAppContentProjectionCallback>?> =
        derivedStateOf {
            selectedTarget.value?.let { fakeProjectionCallbacks[it.model.packageName] }
        }

    override fun setSelectedTarget(target: TargetViewModel?) {
        _selectedTarget.value = target as AppContentViewModel?
    }

    override fun createViewModelFor(target: TargetModel): AppContentViewModel =
        fakeViewModelFactory.create(target as ScreenCaptureAppContent)

    fun setAppContents(appContents: List<ScreenCaptureAppContent>?) {
        fakeAppContents.value = appContents
    }

    fun setAppContents(vararg appContents: ScreenCaptureAppContent) {
        setAppContents(appContents.toList())
    }

    var activateCallCount = 0
    var deactivateCallCount = 0

    override suspend fun activate(): Nothing {
        activateCallCount++
        try {
            awaitCancellation()
        } finally {
            deactivateCallCount++
        }
    }
}
