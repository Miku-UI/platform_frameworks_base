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

package com.android.systemui.communal

import com.android.systemui.CoreStartable
import com.android.systemui.communal.domain.interactor.CommunalSettingsInteractor
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.media.controls.ui.controller.MediaHierarchyManager
import com.android.systemui.media.controls.ui.view.MediaHost
import com.android.systemui.media.controls.ui.view.MediaHostState
import com.android.systemui.media.dagger.MediaModule
import com.android.systemui.media.remedia.shared.flag.MediaControlsInComposeFlag
import javax.inject.Inject
import javax.inject.Named

@SysUISingleton
class CommunalMediaStartable
@Inject
constructor(
    private val communalSettingsInteractor: CommunalSettingsInteractor,
    @Named(MediaModule.COMMUNAL_HUB) private val mediaHost: MediaHost,
) : CoreStartable {
    override fun start() {
        if (MediaControlsInComposeFlag.isEnabled) {
            return
        }

        // Initialize our media host for the UMO. This only needs to happen once and must be done
        // before the MediaHierarchyManager attempts to move the UMO to the hub.
        with(mediaHost) {
            expansion = MediaHostState.EXPANDED
            expandedMatchesParentHeight = true
            // When V2 is enabled, only show active media to match lock screen, non-resumable media,
            // which can persist for up to 2 days.
            showsOnlyActiveMedia = communalSettingsInteractor.isV2FlagEnabled()
            falsingProtectionNeeded = false
            disableScrolling = true
            init(MediaHierarchyManager.LOCATION_COMMUNAL_HUB)
        }
    }
}
