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

package com.android.systemui.screencapture.record.largescreen.domain.interactor

import android.graphics.Rect
import android.net.Uri
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.screencapture.record.largescreen.data.repository.LargeScreenCaptureParametersRepository
import com.android.systemui.screencapture.record.largescreen.shared.model.ScreenCaptureRegion
import com.android.systemui.screencapture.record.largescreen.shared.model.ScreenCaptureType
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

@SysUISingleton
class LargeScreenCaptureParametersInteractor
@Inject
constructor(private val largeScreenSettingsRepository: LargeScreenCaptureParametersRepository) {
    val customSaveLocationUriString: Flow<String> =
        largeScreenSettingsRepository.customSaveLocationUriString

    val isCustomSaveLocationActive: Flow<Boolean> =
        largeScreenSettingsRepository.isCustomSaveLocationActive

    suspend fun setCustomSaveLocation(uri: Uri) {
        largeScreenSettingsRepository.updateCustomSaveLocationUriString(uri)
    }

    suspend fun setIsCustomSaveLocationActive(isActive: Boolean) {
        largeScreenSettingsRepository.updateIsCustomSaveLocationActive(isActive)
    }

    suspend fun getSelectedCaptureType(): ScreenCaptureType {
        return largeScreenSettingsRepository.getSelectedCaptureType()
    }

    suspend fun getSelectedCaptureRegion(): ScreenCaptureRegion {
        return largeScreenSettingsRepository.getSelectedCaptureRegion()
    }

    suspend fun getSelectedCaptureRegionBox(): Rect? {
        return largeScreenSettingsRepository.getSelectedCaptureRegionBox()
    }

    suspend fun setSelectedCaptureType(type: ScreenCaptureType) {
        largeScreenSettingsRepository.updateSelectedCaptureTypeString(type)
    }

    suspend fun setSelectedCaptureRegion(region: ScreenCaptureRegion) {
        largeScreenSettingsRepository.updateSelectedCaptureRegionString(region)
    }

    suspend fun setSelectedCaptureRegionBox(regionBox: Rect?) {
        largeScreenSettingsRepository.updateSelectedCaptureRegionBoxString(regionBox)
    }
}
