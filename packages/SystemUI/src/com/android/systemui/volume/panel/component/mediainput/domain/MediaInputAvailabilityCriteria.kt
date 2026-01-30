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

package com.android.systemui.volume.panel.component.mediainput.domain

import com.android.systemui.volume.dialog.domain.interactor.DesktopAudioTileDetailsFeatureInteractor
import com.android.systemui.volume.panel.dagger.scope.VolumePanelScope
import com.android.systemui.volume.panel.domain.ComponentAvailabilityCriteria
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

@VolumePanelScope
class MediaInputAvailabilityCriteria
@Inject
constructor(
    private val desktopAudioTileDetailsFeatureInteractor: DesktopAudioTileDetailsFeatureInteractor
) : ComponentAvailabilityCriteria {

    override fun isAvailable(): Flow<Boolean> =
        flowOf(desktopAudioTileDetailsFeatureInteractor.isEnabled())
}
