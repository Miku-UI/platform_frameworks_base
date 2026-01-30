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

package com.android.systemui.dreams.data.repository

import android.content.res.Resources
import android.provider.Settings
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.dreams.shared.model.WhenToDream
import com.android.systemui.shared.settings.data.repository.SecureSettingsRepository
import com.android.systemui.utils.coroutines.flow.flatMapLatestConflated
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn

interface DreamSettingsRepository {
    /** Returns when dreams are enabled. */
    fun getDreamsEnabled(): Flow<Boolean>

    /**
     * Returns a [WhenToDream] for the active user, indicating what state the device should be in to
     * trigger dreams.
     */
    fun getWhenToDreamState(): Flow<WhenToDream>
}

@SysUISingleton
class DreamSettingsRepositoryImpl
@Inject
constructor(
    @Background private val bgDispatcher: CoroutineDispatcher,
    @Main private val resources: Resources,
    private val secureSettingsRepository: SecureSettingsRepository,
) : DreamSettingsRepository {
    private val dreamsEnabledByDefault by lazy {
        resources.getBoolean(com.android.internal.R.bool.config_dreamsEnabledByDefault)
    }

    private val dreamsActivatedOnSleepByDefault by lazy {
        resources.getBoolean(com.android.internal.R.bool.config_dreamsActivatedOnSleepByDefault)
    }

    private val dreamsActivatedOnDockByDefault by lazy {
        resources.getBoolean(com.android.internal.R.bool.config_dreamsActivatedOnDockByDefault)
    }

    private val dreamsActivatedOnPosturedByDefault by lazy {
        resources.getBoolean(com.android.internal.R.bool.config_dreamsActivatedOnPosturedByDefault)
    }

    override fun getDreamsEnabled(): Flow<Boolean> =
        secureSettingsRepository
            .boolSetting(Settings.Secure.SCREENSAVER_ENABLED, dreamsEnabledByDefault)
            .flowOn(bgDispatcher)

    private fun getWhenToDreamSetting(): Flow<WhenToDream> =
        combine(
                secureSettingsRepository.boolSetting(
                    Settings.Secure.SCREENSAVER_ACTIVATE_ON_SLEEP,
                    dreamsActivatedOnSleepByDefault,
                ),
                secureSettingsRepository.boolSetting(
                    Settings.Secure.SCREENSAVER_ACTIVATE_ON_DOCK,
                    dreamsActivatedOnDockByDefault,
                ),
                secureSettingsRepository.boolSetting(
                    Settings.Secure.SCREENSAVER_ACTIVATE_ON_POSTURED,
                    dreamsActivatedOnPosturedByDefault,
                ),
            ) { onSleep, onDock, onPostured ->
                if (onSleep) WhenToDream.WHILE_CHARGING
                else if (onDock) WhenToDream.WHILE_DOCKED
                else if (onPostured) WhenToDream.WHILE_POSTURED else WhenToDream.NEVER
            }
            .flowOn(bgDispatcher)

    override fun getWhenToDreamState(): Flow<WhenToDream> =
        getDreamsEnabled()
            .flatMapLatestConflated { enabled ->
                if (enabled) {
                    getWhenToDreamSetting()
                } else {
                    flowOf(WhenToDream.NEVER)
                }
            }
            .flowOn(bgDispatcher)
}
