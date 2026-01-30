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

package com.android.systemui.volume.dialog.captions.domain

import com.android.systemui.accessibility.domain.interactor.CaptioningInteractor
import com.android.systemui.volume.dialog.dagger.scope.VolumeDialog
import com.android.systemui.volume.dialog.dagger.scope.VolumeDialogScope
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Exposes [VolumeDialogCaptionsButtonViewModel]. */
@VolumeDialogScope
class VolumeDialogCaptionsButtonInteractor
@Inject
constructor(
    @VolumeDialog private val coroutineScope: CoroutineScope,
    private val captioningInteractor: CaptioningInteractor,
) {
    val isEnabled: StateFlow<Boolean> =
        captioningInteractor.isSystemAudioCaptioningEnabled.stateIn(
            coroutineScope,
            SharingStarted.Eagerly,
            false,
        )

    val isVisible: StateFlow<Boolean> =
        captioningInteractor.isSystemAudioCaptioningUiEnabled.stateIn(
            coroutineScope,
            SharingStarted.Eagerly,
            false,
        )

    fun onButtonClicked() {
        coroutineScope.launch {
            captioningInteractor.setIsSystemAudioCaptioningEnabled(!isEnabled.value)
        }
    }
}
