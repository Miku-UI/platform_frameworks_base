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

package com.android.systemui.globalactions.ui.viewmodel

import androidx.compose.runtime.getValue
import com.android.internal.R
import com.android.internal.logging.UiEventLogger
import com.android.systemui.authentication.domain.interactor.AuthenticationInteractor
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.deviceentry.domain.interactor.DeviceEntryInteractor
import com.android.systemui.globalactions.shared.model.GlobalActionType
import com.android.systemui.globalactions.shared.model.GlobalActionsEvent
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.map

/** ViewModel for the "Lock" power menu item. */
class LockGlobalActionViewModel
@AssistedInject
constructor(
    private val deviceEntryInteractor: DeviceEntryInteractor,
    authenticationInteractor: AuthenticationInteractor,
    private val uiEventLogger: UiEventLogger,
) : GlobalActionViewModel() {
    override val key = GlobalActionType.LOCK

    override val state by
        authenticationInteractor.authenticationMethod
            .map { it.isSecure.toUiState() }
            .hydratedStateOf(
                traceName = "LockGlobalActionViewModel.state",
                initialValue =
                    authenticationInteractor.authenticationMethod.value.isSecure.toUiState(),
            )

    private fun Boolean.toUiState(): GlobalActionUiState {
        return if (this) {
            GlobalActionUiState.Visible(
                key = key,
                icon =
                    Icon.Resource(
                        resId = com.android.systemui.res.R.drawable.ic_global_actions_lockdown,
                        contentDescription = null,
                    ),
                textResId = R.string.global_action_unrestricted_lock,
                onClick = {
                    uiEventLogger.log(GlobalActionsEvent.GA_LOCK_PRESS)
                    deviceEntryInteractor.lockNow(debuggingReason = "LockGlobalAction")
                },
            )
        } else {
            GlobalActionUiState.Hidden(key = key)
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(): LockGlobalActionViewModel
    }
}
