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
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.globalactions.shared.model.GlobalActionType
import com.android.systemui.user.domain.interactor.UserLogoutInteractor
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.map

/**
 * ViewModel for the "Logout/Sign out" global action that is displayed in a Global Actions dialog or
 * the QS Panel Power Menu.
 */
class LogoutGlobalActionViewModel
@AssistedInject
constructor(private val userLogoutInteractor: UserLogoutInteractor) : GlobalActionViewModel() {

    override val key = GlobalActionType.LOGOUT

    override val state: GlobalActionUiState by
        userLogoutInteractor.isLogoutEnabled
            .map { it.toUiState() }
            .hydratedStateOf(
                traceName = "sign_out_action_state",
                initialValue = userLogoutInteractor.isLogoutEnabled.value.toUiState(),
            )

    private fun Boolean.toUiState(): GlobalActionUiState {
        return if (this) {
            GlobalActionUiState.Visible(
                key = key,
                icon =
                    Icon.Resource(
                        resId = com.android.systemui.res.R.drawable.ic_global_actions_logout,
                        contentDescription = null,
                    ),
                textResId = R.string.global_action_logout,
                onClick = { userLogoutInteractor.logOut() },
            )
        } else {
            GlobalActionUiState.Hidden(key)
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(): LogoutGlobalActionViewModel
    }
}
