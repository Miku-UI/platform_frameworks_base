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

import com.android.internal.R
import com.android.internal.logging.UiEventLogger
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.globalactions.domain.interactor.GlobalActionsInteractor
import com.android.systemui.globalactions.shared.model.GlobalActionType
import com.android.systemui.globalactions.shared.model.GlobalActionsEvent
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.receiveAsFlow

/** ViewModel for the "Restart" global action. */
class RestartGlobalActionViewModel
@AssistedInject
constructor(
    private val interactor: GlobalActionsInteractor,
    private val uiEventLogger: UiEventLogger,
) : GlobalActionViewModel() {

    override val key = GlobalActionType.RESTART
    private val intents = Channel<suspend () -> Unit>(Channel.BUFFERED)

    override val state =
        GlobalActionUiState.Visible(
            key = key,
            icon =
                Icon.Resource(
                    resId = com.android.systemui.res.R.drawable.ic_global_actions_restart,
                    contentDescription = null,
                ),
            textResId = R.string.global_action_restart,
            onClick = {
                uiEventLogger.log(GlobalActionsEvent.GA_REBOOT_PRESS)
                intents.trySend { interactor.reboot(safeMode = false) }
            },
            onLongClick = {
                uiEventLogger.log(GlobalActionsEvent.GA_REBOOT_LONG_PRESS)
                intents.trySend { interactor.reboot(safeMode = true) }
            },
        )

    override suspend fun onActivated() {
        coroutineScope { intents.receiveAsFlow().collect { action -> action() } }
    }

    @AssistedFactory
    interface Factory {
        fun create(): RestartGlobalActionViewModel
    }
}
