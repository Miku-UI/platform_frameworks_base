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

import androidx.compose.runtime.getValue
import com.android.systemui.keyguard.domain.interactor.KeyguardInteractor
import com.android.systemui.keyguard.domain.interactor.KeyguardTransitionInteractor
import com.android.systemui.keyguard.shared.model.KeyguardState
import com.android.systemui.lifecycle.HydratedActivatable
import com.android.systemui.minmode.MinModeManager
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.shade.ui.viewmodel.NotificationShadeWindowModel
import com.android.systemui.util.kotlin.BooleanFlowOperators.anyOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import java.util.Optional
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart

class LockscreenAlphaViewModel
@AssistedInject
constructor(
    transitionInteractor: KeyguardTransitionInteractor,
    minModeManager: Optional<MinModeManager>,
    notificationShadeWindowModel: NotificationShadeWindowModel,
    private val alternateBouncerToAodTransitionViewModel: AlternateBouncerToAodTransitionViewModel,
    private val alternateBouncerToGoneTransitionViewModel:
        AlternateBouncerToGoneTransitionViewModel,
    private val alternateBouncerToLockscreenTransitionViewModel:
        AlternateBouncerToLockscreenTransitionViewModel,
    private val alternateBouncerToOccludedTransitionViewModel:
        AlternateBouncerToOccludedTransitionViewModel,
    private val aodToLockscreenTransitionViewModel: AodToLockscreenTransitionViewModel,
    private val aodToOccludedTransitionViewModel: AodToOccludedTransitionViewModel,
    private val dozingToLockscreenTransitionViewModel: DozingToLockscreenTransitionViewModel,
    private val dozingToOccludedTransitionViewModel: DozingToOccludedTransitionViewModel,
    private val lockscreenToAodTransitionViewModel: LockscreenToAodTransitionViewModel,
    private val lockscreenToDozingTransitionViewModel: LockscreenToDozingTransitionViewModel,
    private val lockscreenToOccludedTransitionViewModel: LockscreenToOccludedTransitionViewModel,
    private val occludedToAlternateBouncerTransitionViewModel:
        OccludedToAlternateBouncerTransitionViewModel,
    private val occludedToAodTransitionViewModel: OccludedToAodTransitionViewModel,
    private val occludedToDozingTransitionViewModel: OccludedToDozingTransitionViewModel,
    private val occludedToLockscreenTransitionViewModel: OccludedToLockscreenTransitionViewModel,
    private val offToLockscreenTransitionViewModel: OffToLockscreenTransitionViewModel,
    private val keyguardInteractor: KeyguardInteractor,
    @Assisted private val viewStateAccessor: ViewStateAccessor,
) : HydratedActivatable() {
    /**
     * The `alpha` property depends on this property. To prevent a NullPointerException, ensure
     * `hideKeyguard` is initialized before `alpha`.
     */
    private val hideKeyguard: Flow<Boolean> =
        anyOf(
            if (minModeManager.isPresent) {
                minModeManager.get().isMinModeInForegroundFlow
            } else {
                flowOf(false)
            },
            notificationShadeWindowModel.isKeyguardOccluded,
            transitionInteractor
                .transitionValue(KeyguardState.OFF)
                .map { it > 1f - offToLockscreenTransitionViewModel.alphaStartAt }
                .onStart { emit(false) },
            transitionInteractor
                .transitionValue(
                    content = Scenes.Gone,
                    stateWithoutSceneContainer = KeyguardState.GONE,
                )
                .map { it == 1f }
                .onStart { emit(false) },
        )

    /** Alpha value applied to all LockscreenElements. */
    val alpha: Float by
        alpha(viewState = viewStateAccessor).hydratedStateOf(traceName = "alpha", initialValue = 0f)

    /** An observable for the alpha level for the entire keyguard root view. */
    private fun alpha(viewState: ViewStateAccessor): Flow<Float> {
        return combine(
                hideKeyguard,
                // The transitions are mutually exclusive, so they are safe to merge to get the last
                // value emitted by any of them. Do not add flows that cannot make this guarantee.
                merge(
                        keyguardInteractor.dismissAlpha,
                        alternateBouncerToAodTransitionViewModel.lockscreenAlpha(viewState),
                        alternateBouncerToGoneTransitionViewModel.lockscreenAlpha(viewState),
                        alternateBouncerToLockscreenTransitionViewModel.lockscreenAlpha(viewState),
                        alternateBouncerToOccludedTransitionViewModel.lockscreenAlpha,
                        aodToLockscreenTransitionViewModel.lockscreenAlpha(viewState),
                        aodToOccludedTransitionViewModel.lockscreenAlpha(viewState),
                        dozingToLockscreenTransitionViewModel.lockscreenAlpha(viewState),
                        dozingToOccludedTransitionViewModel.lockscreenAlpha(viewState),
                        lockscreenToAodTransitionViewModel.lockscreenAlpha(viewState),
                        lockscreenToAodTransitionViewModel.lockscreenAlphaOnFold,
                        lockscreenToDozingTransitionViewModel.lockscreenAlpha,
                        lockscreenToOccludedTransitionViewModel.lockscreenAlpha,
                        occludedToAlternateBouncerTransitionViewModel.lockscreenAlpha,
                        occludedToAodTransitionViewModel.lockscreenAlpha,
                        occludedToDozingTransitionViewModel.lockscreenAlpha,
                        occludedToLockscreenTransitionViewModel.lockscreenAlpha,
                        offToLockscreenTransitionViewModel.lockscreenAlpha,
                    )
                    .onStart { emit(0f) },
            ) { hideKeyguard, alpha ->
                if (hideKeyguard) {
                    0f
                } else {
                    alpha
                }
            }
            .distinctUntilChanged()
    }

    @AssistedFactory
    interface Factory {
        fun create(viewStateAccessor: ViewStateAccessor): LockscreenAlphaViewModel
    }
}
