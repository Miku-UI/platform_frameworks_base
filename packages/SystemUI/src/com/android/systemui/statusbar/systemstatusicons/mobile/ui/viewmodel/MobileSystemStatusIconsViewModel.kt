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

package com.android.systemui.statusbar.systemstatusicons.mobile.ui.viewmodel

import android.content.Context
import androidx.compose.runtime.getValue
import com.android.systemui.lifecycle.ExclusiveActivatable
import com.android.systemui.lifecycle.Hydrator
import com.android.systemui.statusbar.pipeline.mobile.ui.viewmodel.MobileIconsViewModel
import com.android.systemui.statusbar.pipeline.mobile.ui.viewmodel.StackedMobileIconViewModelImpl
import com.android.systemui.statusbar.systemstatusicons.SystemStatusIconsInCompose
import com.android.systemui.statusbar.systemstatusicons.ui.viewmodel.SystemStatusIconViewModel
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.map

/** View model to consolidate all mobile icons (signal bars with additional network information) */
class MobileSystemStatusIconsViewModel
@AssistedInject
constructor(
    @Assisted context: Context,
    // TODO(427984473): Refactor MobileIconsViewModel to be able to use a factory.
    override val mobileIconsViewModel: MobileIconsViewModel,
    stackedMobileIconViewModelFactory: StackedMobileIconViewModelImpl.Factory,
) : SystemStatusIconViewModel.MobileIcons, ExclusiveActivatable() {

    init {
        SystemStatusIconsInCompose.expectInNewMode()
    }

    private val hydrator = Hydrator("MobileSystemStatusIconViewModel.hydrator")

    override val slotName = context.getString(com.android.internal.R.string.status_bar_mobile)

    override val stackedMobileIconViewModel by lazy { stackedMobileIconViewModelFactory.create() }

    override val visible: Boolean by
        hydrator.hydratedStateOf(
            traceName = null,
            initialValue = false,
            source = mobileIconsViewModel.mobileSubViewModels.map { it.isNotEmpty() },
        )

    override suspend fun onActivated(): Nothing {
        hydrator.activate()
    }

    @AssistedFactory
    interface Factory {
        fun create(context: Context): MobileSystemStatusIconsViewModel
    }
}
