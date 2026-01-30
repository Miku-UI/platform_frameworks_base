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

package com.android.systemui.keyguard.domain.interactor

import android.app.WallpaperColors
import android.content.Context
import android.os.Bundle
import android.os.IBinder
import android.view.Display
import com.android.systemui.keyguard.data.repository.KeyguardPreviewRepository
import com.android.systemui.keyguard.shared.model.ClockSizeSetting
import com.android.systemui.plugins.keyguard.ui.clocks.ClockController
import com.android.systemui.shade.domain.interactor.ShadeModeInteractor
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

@AssistedFactory
interface KeyguardPreviewInteractorFactory {
    fun create(repository: KeyguardPreviewRepository): KeyguardPreviewInteractor
}

class KeyguardPreviewInteractor
@AssistedInject
constructor(
    @Assisted private val repository: KeyguardPreviewRepository,
    clockInteractor: KeyguardClockInteractor,
    shadeModeInteractor: ShadeModeInteractor,
) {
    val previewContext: Context
        get() = repository.previewContext

    val previewClock: Flow<ClockController> = repository.previewClock

    val previewClockSize: Flow<ClockSizeSetting> =
        combine(repository.requestedClockSize, clockInteractor.selectedClockSize) {
            requestedClockSize,
            selectedClockSize ->
            requestedClockSize ?: selectedClockSize
        }

    val shouldHideClock: Boolean
        get() = repository.shouldHideClock

    val shouldHighlightSelectedAffordance: Boolean
        get() = repository.shouldHighlightSelectedAffordance

    val isFullWidthShade: Boolean = run {
        if (repository.display == null || repository.display.displayId == 0) {
            shadeModeInteractor.isFullWidthShade.value
        } else {
            // For the unfolded preview in a folded screen; it's landscape by default
            // For the folded preview in an unfolded screen; it's portrait by default
            repository.display.name != "Inner Display"
        }
    }
    val request: Bundle
        get() = repository.request

    val hostToken: IBinder?
        get() = repository.hostToken

    val targetWidth: Int
        get() = repository.targetWidth

    val targetHeight: Int
        get() = repository.targetHeight

    val display: Display?
        get() = repository.display

    val wallpaperColors: WallpaperColors?
        get() = repository.wallpaperColors
}
