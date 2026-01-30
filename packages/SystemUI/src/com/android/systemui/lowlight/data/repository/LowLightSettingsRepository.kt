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

package com.android.systemui.lowlight.data.repository

import android.content.res.Resources
import android.provider.Settings
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.lowlight.shared.model.LowLightDisplayBehavior
import com.android.systemui.res.R
import com.android.systemui.shared.settings.data.repository.SecureSettingsRepository
import com.android.systemui.utils.coroutines.flow.flatMapLatestConflated
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * The {@link LowLightSettingsRepository} provides access to settings associated with low-light
 * behavior, included whether it is enabled and the user chosen behavior. It also allows for setting
 * these values.
 */
interface LowLightSettingsRepository {
    /**
     * Returns a flow for tracking whether low-light behavior is enabled for the active user. This
     * is a separate setting as the user might have a preferred behavior, but temporarily disabled
     * the functionality.
     */
    fun getLowLightDisplayBehaviorEnabled(): Flow<Boolean>

    /** Updates whether the active user has low-light behavior enabled. */
    suspend fun setLowLightDisplayBehaviorEnabled(enabled: Boolean)

    /**
     * Returns the chosen {@link LowLightDisplayBehavior} for the active user. Note that enabled
     * state is tracked in {@link #getLowLightDisplayBehaviorEnabled}.
     */
    fun getLowLightDisplayBehavior(): Flow<LowLightDisplayBehavior>

    /** Sets the {@link LowLightDisplayBehavior} for the active user. */
    suspend fun setLowLightDisplayBehavior(behavior: LowLightDisplayBehavior)

    val allowLowLightBehaviorWhenLocked: Boolean
}

class LowLightSettingsRepositoryImpl
@Inject
constructor(
    @Background private val bgDispatcher: CoroutineDispatcher,
    private val secureSettingsRepository: SecureSettingsRepository,
    @Main private val resources: Resources,
) : LowLightSettingsRepository {
    private val lowLightDisplayBehaviorEnabledDefault by lazy {
        resources.getBoolean(
            com.android.internal.R.bool.config_lowLightDisplayBehaviorEnabledDefault
        )
    }

    private val lowLightDisplayBehaviorDefault by lazy {
        resources.getInteger(com.android.internal.R.integer.config_lowLightDisplayBehaviorDefault)
    }

    override fun getLowLightDisplayBehaviorEnabled(): Flow<Boolean> =
        secureSettingsRepository
            .boolSetting(
                Settings.Secure.LOW_LIGHT_DISPLAY_BEHAVIOR_ENABLED,
                lowLightDisplayBehaviorEnabledDefault,
            )
            .flowOn(bgDispatcher)

    override suspend fun setLowLightDisplayBehaviorEnabled(enabled: Boolean) {
        secureSettingsRepository.setBoolean(
            Settings.Secure.LOW_LIGHT_DISPLAY_BEHAVIOR_ENABLED,
            enabled,
        )
    }

    override fun getLowLightDisplayBehavior(): Flow<LowLightDisplayBehavior> =
        getLowLightDisplayBehaviorEnabled()
            .flatMapLatestConflated { enabled ->
                if (enabled) {
                    secureSettingsRepository
                        .intSetting(
                            Settings.Secure.LOW_LIGHT_DISPLAY_BEHAVIOR,
                            lowLightDisplayBehaviorDefault,
                        )
                        .map { it.toLowLightDisplayBehavior() }
                } else {
                    flowOf(LowLightDisplayBehavior.NONE)
                }
            }
            .flowOn(bgDispatcher)

    override suspend fun setLowLightDisplayBehavior(behavior: LowLightDisplayBehavior) {
        secureSettingsRepository.setInt(
            Settings.Secure.LOW_LIGHT_DISPLAY_BEHAVIOR,
            behavior.toSettingsInt(),
        )
    }

    override val allowLowLightBehaviorWhenLocked by lazy {
        resources.getBoolean(R.bool.config_allowLowLightBehaviorWhenLocked)
    }

    private fun Int.toLowLightDisplayBehavior(): LowLightDisplayBehavior {
        return when (this) {
            Settings.Secure.LOW_LIGHT_DISPLAY_BEHAVIOR_NO_DREAM -> LowLightDisplayBehavior.NO_DREAM
            Settings.Secure.LOW_LIGHT_DISPLAY_BEHAVIOR_SCREEN_OFF ->
                LowLightDisplayBehavior.SCREEN_OFF
            Settings.Secure.LOW_LIGHT_DISPLAY_BEHAVIOR_LOW_LIGHT_CLOCK_DREAM ->
                LowLightDisplayBehavior.LOW_LIGHT_DREAM
            else -> LowLightDisplayBehavior.UNKNOWN
        }
    }

    private fun LowLightDisplayBehavior.toSettingsInt(): Int {
        return when (this) {
            LowLightDisplayBehavior.NO_DREAM -> Settings.Secure.LOW_LIGHT_DISPLAY_BEHAVIOR_NO_DREAM
            LowLightDisplayBehavior.SCREEN_OFF ->
                Settings.Secure.LOW_LIGHT_DISPLAY_BEHAVIOR_SCREEN_OFF
            LowLightDisplayBehavior.LOW_LIGHT_DREAM ->
                Settings.Secure.LOW_LIGHT_DISPLAY_BEHAVIOR_LOW_LIGHT_CLOCK_DREAM
            else -> lowLightDisplayBehaviorDefault
        }
    }
}
