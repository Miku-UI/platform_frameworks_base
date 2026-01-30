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

package com.android.systemui.statusbar.featurepods.sharescreen.domain.interactor

import android.content.res.Resources
import com.android.systemui.common.ui.data.repository.ConfigurationRepository
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.res.R
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

@SysUISingleton
class ShareScreenPrivacyIndicatorInteractor
@Inject
constructor(
    @Main private val resources: Resources,
    configurationRepository: ConfigurationRepository,
    @Background scope: CoroutineScope,
) {
    private val isSharingActive = MutableStateFlow(false)

    val isChipVisible: StateFlow<Boolean> =
        combine(
                configurationRepository.onAnyConfigurationChange.onStart { emit(Unit) },
                isSharingActive,
            ) { _, isSharing ->
                resources.getBoolean(R.bool.config_largeScreenPrivacyIndicator) && isSharing
            }
            .stateIn(
                scope = scope,
                started = SharingStarted.WhileSubscribed(),
                initialValue = false,
            )

    fun showChip() {
        isSharingActive.value = true
    }

    fun hideChip() {
        isSharingActive.value = false
    }
}
