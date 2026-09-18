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

package com.android.systemui.statusbar.systemstatusicons.alarm.ui.viewmodel

import android.content.Context
import androidx.compose.runtime.getValue
import com.android.systemui.common.shared.model.ContentDescription
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.lifecycle.HydratedActivatable
import com.android.systemui.res.R
import com.android.systemui.statusbar.policy.domain.interactor.NextAlarmInteractor
import com.android.systemui.statusbar.systemstatusicons.SystemStatusIconsInCompose
import com.android.systemui.statusbar.systemstatusicons.ui.viewmodel.SystemStatusIconViewModel
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject

/**
 * View model for the next alarm system status icon. Emits an alarm clock icon when an alarm is set
 * and null icon otherwise.
 */
class NextAlarmIconViewModel
@AssistedInject
constructor(@Assisted context: Context, interactor: NextAlarmInteractor) :
    SystemStatusIconViewModel.Default, HydratedActivatable() {
    init {
        SystemStatusIconsInCompose.expectInNewMode()
    }

    override val slotName = context.getString(com.android.internal.R.string.status_bar_alarm_clock)

    override val visible: Boolean by
        interactor.isAlarmSet.hydratedStateOf(traceName = null, initialValue = false)

    override val icon: Icon?
        get() = visible.toUiState()

    private fun Boolean.toUiState(): Icon? =
        if (this) {
            Icon.Resource(
                resId = R.drawable.stat_sys_alarm,
                contentDescription = ContentDescription.Resource(R.string.status_bar_alarm),
            )
        } else {
            null
        }

    @AssistedFactory
    interface Factory {
        fun create(context: Context): NextAlarmIconViewModel
    }
}
