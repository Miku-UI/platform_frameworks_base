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

package com.android.systemui.statusbar.systemstatusicons.zenmode.ui.viewmodel

import android.content.Context
import androidx.compose.runtime.getValue
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.lifecycle.ExclusiveActivatable
import com.android.systemui.lifecycle.Hydrator
import com.android.systemui.statusbar.policy.domain.interactor.ZenModeInteractor
import com.android.systemui.statusbar.policy.domain.model.ZenModeInfo
import com.android.systemui.statusbar.systemstatusicons.ui.viewmodel.SystemStatusIconViewModel
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject

/**
 * ViewModel for the Zen Mode system status icon. Observes the current Zen mode state and provides
 * an appropriate [Icon] model for display.
 */
class ZenModeIconViewModel
@AssistedInject
constructor(@Assisted private val context: Context, interactor: ZenModeInteractor) :
    SystemStatusIconViewModel.Default, ExclusiveActivatable() {

    private val hydrator: Hydrator = Hydrator("ZenModeIconViewModel.hydrator")

    override val slotName = context.getString(com.android.internal.R.string.status_bar_zen)

    private val zenModeState: ZenModeInfo? by
        hydrator.hydratedStateOf(
            traceName = "SystemStatus.zenModeState",
            initialValue = null,
            source = interactor.mainActiveMode,
        )

    override val visible: Boolean
        get() = zenModeState != null

    override val icon: Icon?
        get() = zenModeState?.icon

    override suspend fun onActivated(): Nothing {
        hydrator.activate()
    }

    @AssistedFactory
    interface Factory {
        fun create(context: Context): ZenModeIconViewModel
    }
}
