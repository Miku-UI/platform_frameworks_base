/*
 * Copyright 2025 The Android Open Source Project
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

package com.android.systemui.ambientcue.domain.interactor

import android.graphics.Rect
import com.android.systemui.ambientcue.data.repository.AmbientCueRepository
import com.android.systemui.keyguard.domain.interactor.KeyguardInteractor
import com.android.systemui.plugins.cuebar.ActionModel
import com.android.systemui.shade.domain.interactor.ShadeInteractor
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update

class AmbientCueInteractor
@Inject
constructor(
    private val repository: AmbientCueRepository,
    shadeInteractor: ShadeInteractor,
    keyguardInteractor: KeyguardInteractor,
) {
    val isRootViewAttached: StateFlow<Boolean> = repository.isRootViewAttached
    val actions: StateFlow<List<ActionModel>> = repository.actions
    val isImeVisible: StateFlow<Boolean> = repository.isImeVisible
    val isOccludedBySystemUi: Flow<Boolean> =
        combine(shadeInteractor.isShadeFullyCollapsed, keyguardInteractor.isKeyguardVisible) {
            isShadeFullyCollapsed,
            isKeyguardVisible ->
            !isShadeFullyCollapsed || isKeyguardVisible
        }
    val isGestureNav: StateFlow<Boolean> = repository.isGestureNav
    val recentsButtonPosition: StateFlow<Rect?> = repository.recentsButtonPosition
    val isTaskBarVisible: StateFlow<Boolean> = repository.isTaskBarVisible
    val isAmbientCueEnabled: StateFlow<Boolean> = repository.isAmbientCueEnabled
    val ambientCueTimeoutMs: StateFlow<Int> = repository.ambientCueTimeoutMs

    fun setDeactivated(isDeactivated: Boolean) {
        repository.isDeactivated.update { isDeactivated }
    }

    fun setImeVisible(isVisible: Boolean) {
        repository.isImeVisible.update { isVisible }
    }
}
