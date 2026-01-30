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

package com.android.systemui.shade.data.repository

import android.content.res.Resources
import androidx.annotation.BoolRes
import com.android.systemui.common.ui.data.repository.ConfigurationRepository
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.res.R
import com.android.systemui.scene.shared.flag.SceneContainerFlag
import com.android.systemui.shade.ShadeDisplayAware
import com.android.systemui.util.kotlin.emitOnStart
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Provides shade configuration values for the window currently hosting the shade. This is mainly a
 * convenience wrapper; the same values may be accessed directly from a [ShadeDisplayAware] context.
 */
@SysUISingleton
class ShadeConfigRepository
@Inject
constructor(
    @ShadeDisplayAware private val resources: Resources,
    @ShadeDisplayAware private val configurationRepository: ConfigurationRepository,
) {
    /** @see ShadeModeInteractor.isFullWidthShade */
    val isFullWidthShade: Flow<Boolean> = booleanConfigFlow(R.bool.config_isFullWidthShade)

    /** Whether Dual Shade should be used instead of Split Shade, regardless of setting. */
    val isSplitShadeDisabled: Boolean =
        SceneContainerFlag.isEnabled && resources.getBoolean(R.bool.config_disableSplitShade)

    /** @see isFullWidthShade (the `val` above) */
    fun isFullWidthShade(): Boolean = resources.getBoolean(R.bool.config_isFullWidthShade)

    private fun booleanConfigFlow(@BoolRes resID: Int): Flow<Boolean> {
        return configurationRepository.onConfigurationChange.emitOnStart().map {
            resources.getBoolean(resID)
        }
    }
}
