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

package com.android.systemui.statusbar.systemstatusicons.vpn.ui.viewmodel

import android.content.Context
import androidx.compose.runtime.getValue
import com.android.systemui.common.shared.model.ContentDescription
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.lifecycle.ExclusiveActivatable
import com.android.systemui.lifecycle.Hydrator
import com.android.systemui.res.R
import com.android.systemui.statusbar.policy.vpn.domain.interactor.VpnInteractor
import com.android.systemui.statusbar.policy.vpn.shared.model.VpnState
import com.android.systemui.statusbar.systemstatusicons.ui.viewmodel.SystemStatusIconViewModel
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject

class VpnIconViewModel
@AssistedInject
constructor(@Assisted private val context: Context, interactor: VpnInteractor) :
    SystemStatusIconViewModel.Default, ExclusiveActivatable() {
    val hydrator = Hydrator(traceName = "VpnIconViewModel.hydrator")

    private val vpnState by
        hydrator.hydratedStateOf(
            traceName = "SystemStatus.vpnState",
            initialValue = VpnState(),
            source = interactor.vpnState,
        )

    override val slotName = context.getString(com.android.internal.R.string.status_bar_vpn)

    override val visible
        get() = vpnState.isEnabled

    override val icon
        get() = vpnState.toUiState()

    private fun VpnState.toUiState(): Icon? {
        if (!isEnabled) {
            return null
        }
        val res =
            if (isBranded) {
                if (isValidated) {
                    R.drawable.stat_sys_branded_vpn
                } else {
                    R.drawable.stat_sys_no_internet_branded_vpn
                }
            } else {
                if (isValidated) {
                    R.drawable.stat_sys_vpn_ic
                } else {
                    R.drawable.stat_sys_no_internet_vpn_ic
                }
            }

        return Icon.Resource(
            resId = res,
            contentDescription = ContentDescription.Resource(R.string.accessibility_vpn_on),
        )
    }

    override suspend fun onActivated(): Nothing {
        hydrator.activate()
    }

    @AssistedFactory
    interface Factory {
        fun create(context: Context): VpnIconViewModel
    }
}
