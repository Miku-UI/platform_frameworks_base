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
import com.android.systemui.lifecycle.ExclusiveActivatable
import com.android.systemui.statusbar.pipeline.mobile.ui.viewmodel.MobileIconsState
import com.android.systemui.statusbar.pipeline.mobile.ui.viewmodel.StackedMobileIconViewModel
import com.android.systemui.statusbar.systemstatusicons.SystemStatusIconsInCompose
import com.android.systemui.statusbar.systemstatusicons.ui.viewmodel.SystemStatusIconViewModel
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** View model to consolidate all mobile icons (signal bars with additional network information) */
class MobileSystemStatusIconsViewModel
@AssistedInject
constructor(
    @Assisted context: Context,
    mobileIconsStateFactory: MobileIconsState.Factory,
    stackedMobileIconViewModelFactory: StackedMobileIconViewModel.Factory,
) : SystemStatusIconViewModel.MobileIcons, ExclusiveActivatable() {

    init {
        SystemStatusIconsInCompose.expectInNewMode()
    }

    override val slotName = context.getString(com.android.internal.R.string.status_bar_mobile)

    override val stackedMobileIconViewModel by lazy { stackedMobileIconViewModelFactory.create() }

    override val mobileIcons by lazy { mobileIconsStateFactory.create() }

    override val visible: Boolean
        get() = mobileIcons.mobileSubViewModels.isNotEmpty()

    override suspend fun onActivated() {
        coroutineScope {
            launch { mobileIcons.activate() }
            launch { stackedMobileIconViewModel.activate() }
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(context: Context): MobileSystemStatusIconsViewModel
    }
}
