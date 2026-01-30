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

package com.android.systemui.lowlight.domain.interactor

import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.lowlight.data.repository.LowLightRepository
import com.android.systemui.lowlight.shared.model.LowLightActionEntry
import com.android.systemui.lowlight.shared.model.LowLightDisplayBehavior
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * {@link LowLightInteractor} abstracts the low-light settings and configuration to simply track the
 * active low-light behavior/action state.
 */
@SysUISingleton
class LowLightInteractor
@Inject
constructor(
    private val repository: LowLightRepository,
    private val lowLightSettingsInteractor: LowLightSettingsInteractor,
    @Background private val bgDispatcher: CoroutineDispatcher,
) {
    fun getLowLightActionEntry(behavior: LowLightDisplayBehavior): LowLightActionEntry? =
        repository.getEntry(behavior)

    /**
     * Returns the {@link LowLightActionEntry} (or {@code null} if none available) that matches the
     * user selected behavior when enabled.
     */
    fun getActiveLowLightActionEntry(): Flow<LowLightActionEntry?> =
        lowLightSettingsInteractor.lowLightDisplayBehavior
            .map { behavior -> getLowLightActionEntry(behavior) }
            .flowOn(bgDispatcher)
}
