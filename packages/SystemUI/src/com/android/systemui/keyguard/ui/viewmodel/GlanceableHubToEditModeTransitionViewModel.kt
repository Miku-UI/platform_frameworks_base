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

package com.android.systemui.keyguard.ui.viewmodel

import com.android.systemui.Flags.hubEditModeTransition
import com.android.systemui.communal.domain.interactor.CommunalSceneInteractor
import com.android.systemui.communal.shared.model.EditModeState
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.keyguard.ui.transitions.BlurConfig
import com.android.systemui.keyguard.ui.transitions.GlanceableHubTransition
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map

@SysUISingleton
class GlanceableHubToEditModeTransitionViewModel
@Inject
constructor(blurConfig: BlurConfig, communalSceneInteractor: CommunalSceneInteractor) :
    GlanceableHubTransition {

    override val windowBlurRadius: Flow<Float> =
        if (hubEditModeTransition()) {
            communalSceneInteractor.editModeState
                // Apply window blur when edit mode is not showing, and remove the blur immediately
                // when the edit mode activity is ready to show. No transition is needed because
                // SystemUI applies an opague background that covers the views below.
                .map { editModeState ->
                    if (editModeState == null || editModeState < EditModeState.READY_TO_SHOW) {
                        blurConfig.maxBlurRadiusPx
                    } else {
                        blurConfig.minBlurRadiusPx
                    }
                }
                .distinctUntilChanged()
                // Emit only when the blur radius changes
                .drop(1)
        } else {
            emptyFlow()
        }
}
