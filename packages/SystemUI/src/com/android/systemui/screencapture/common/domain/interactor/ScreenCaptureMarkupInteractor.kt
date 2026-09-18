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

package com.android.systemui.screencapture.common.domain.interactor

import android.content.res.Resources
import android.graphics.Color
import androidx.core.content.res.use
import com.android.systemui.content.res.map
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.res.R
import com.android.systemui.screencapture.common.ScreenCapture
import com.android.systemui.screencapture.common.ScreenCaptureScope
import com.android.systemui.screencapture.common.data.repository.ScreenCaptureMarkupRepository
import com.android.systemui.screencapture.record.domain.interactor.ScreenCaptureRecordFeaturesInteractor
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@ScreenCaptureScope
class ScreenCaptureMarkupInteractor
@Inject
constructor(
    @Main resources: Resources,
    @ScreenCapture private val coroutineScope: CoroutineScope,
    private val repository: ScreenCaptureMarkupRepository,
    private val screenCaptureRecordFeaturesInteractor: ScreenCaptureRecordFeaturesInteractor,
) {
    val availableColors: List<Int> =
        resources.obtainTypedArray(R.array.screen_record_color_palette).use { array ->
            array.map { index -> getColor(index, Color.TRANSPARENT) }
        }
    val color: StateFlow<Int> =
        repository.color
            .map { it ?: availableColors.first() }
            .stateIn(
                coroutineScope,
                SharingStarted.Eagerly,
                repository.color.value ?: availableColors.first(),
            )
    val enabled: Flow<Boolean> =
        repository.enabled.map { enabled ->
            enabled && screenCaptureRecordFeaturesInteractor.isMarkupAvailable
        }

    fun setEnabled(enabled: Boolean) {
        repository.setEnabled(enabled)
    }

    fun setColor(color: Int) {
        repository.setColor(color)
    }
}
