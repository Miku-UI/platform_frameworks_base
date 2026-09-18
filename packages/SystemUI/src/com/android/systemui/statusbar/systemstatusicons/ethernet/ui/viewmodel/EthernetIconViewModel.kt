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

package com.android.systemui.statusbar.systemstatusicons.ethernet.ui.viewmodel

import android.content.Context
import androidx.compose.runtime.getValue
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.lifecycle.HydratedActivatable
import com.android.systemui.statusbar.pipeline.ethernet.domain.EthernetInteractor
import com.android.systemui.statusbar.systemstatusicons.SystemStatusIconsInCompose
import com.android.systemui.statusbar.systemstatusicons.ui.viewmodel.SystemStatusIconViewModel
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject

/**
 * View model for the ethernet system status icon. Emits an icon when ethernet is connected and the
 * default connection. Null icon otherwise.
 */
class EthernetIconViewModel
@AssistedInject
constructor(@Assisted context: Context, interactor: EthernetInteractor) :
    SystemStatusIconViewModel.Default, HydratedActivatable() {

    init {
        /* check if */ SystemStatusIconsInCompose.isUnexpectedlyInLegacyMode()
    }

    override val slotName = context.getString(com.android.internal.R.string.status_bar_ethernet)

    override val icon: Icon? by
        interactor.icon.hydratedStateOf(traceName = null, initialValue = null)

    override val visible: Boolean
        get() = icon != null

    @AssistedFactory
    interface Factory {
        fun create(context: Context): EthernetIconViewModel
    }
}
