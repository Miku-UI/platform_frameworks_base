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

package com.android.systemui.statusbar.systemstatusicons.airplane.ui.viewmodel

import android.content.Context
import androidx.compose.runtime.getValue
import com.android.systemui.common.shared.model.ContentDescription
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.lifecycle.ExclusiveActivatable
import com.android.systemui.lifecycle.Hydrator
import com.android.systemui.res.R
import com.android.systemui.statusbar.pipeline.airplane.domain.interactor.AirplaneModeInteractor
import com.android.systemui.statusbar.systemstatusicons.SystemStatusIconsInCompose
import com.android.systemui.statusbar.systemstatusicons.ui.viewmodel.SystemStatusIconViewModel
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.combine

/**
 * View model for the airplane mode system status icon. Emits an airplane icon when airplane mode is
 * active and the icon should be shown. Null icon otherwise.
 */
class AirplaneModeIconViewModel
@AssistedInject
constructor(@Assisted context: Context, interactor: AirplaneModeInteractor) :
    SystemStatusIconViewModel.Default, ExclusiveActivatable() {
    init {
        /* check if */ SystemStatusIconsInCompose.isUnexpectedlyInLegacyMode()
    }

    private val hydrator = Hydrator("AirplaneModeIconViewModel.hydrator")

    override val slotName = context.getString(com.android.internal.R.string.status_bar_airplane)

    override val visible: Boolean by
        hydrator.hydratedStateOf(
            traceName = null,
            initialValue = false,
            source =
                combine(interactor.isAirplaneMode, interactor.isForceHidden) {
                    isAirplaneMode,
                    isForceHidden ->
                    isAirplaneMode && !isForceHidden
                },
        )

    override val icon: Icon?
        get() = visible.toUiState()

    override suspend fun onActivated(): Nothing {
        hydrator.activate()
    }

    private fun Boolean.toUiState(): Icon? =
        if (this) {
            Icon.Resource(
                resId = com.android.internal.R.drawable.ic_qs_airplane,
                contentDescription =
                    ContentDescription.Resource(R.string.accessibility_airplane_mode),
            )
        } else {
            null
        }

    @AssistedFactory
    interface Factory {
        fun create(context: Context): AirplaneModeIconViewModel
    }
}
