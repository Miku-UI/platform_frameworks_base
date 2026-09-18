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

package com.android.systemui.qs.panels.ui.viewmodel.toolbar

import com.android.systemui.globalactions.domain.interactor.GlobalActionsInteractor
import com.android.systemui.globalactions.ui.viewmodel.GlobalActionUiState
import com.android.systemui.globalactions.ui.viewmodel.GlobalActionViewModel
import com.android.systemui.globalactions.ui.viewmodel.LockGlobalActionViewModel
import com.android.systemui.globalactions.ui.viewmodel.LogoutGlobalActionViewModel
import com.android.systemui.globalactions.ui.viewmodel.RestartGlobalActionViewModel
import com.android.systemui.globalactions.ui.viewmodel.ShutdownGlobalActionViewModel
import com.android.systemui.lifecycle.HydratedActivatable
import com.android.systemui.log.LogBuffer
import com.android.systemui.log.core.Logger
import com.android.systemui.log.dagger.PowerMenuLog
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Aggregator ViewModel for the power menu shown when the power button in the QuickSettings panel
 * toolbar is clicked on large screens.
 */
class PowerMenuViewModel
@AssistedInject
constructor(
    lockFactory: LockGlobalActionViewModel.Factory,
    logoutFactory: LogoutGlobalActionViewModel.Factory,
    restartFactory: RestartGlobalActionViewModel.Factory,
    shutdownFactory: ShutdownGlobalActionViewModel.Factory,
    interactor: GlobalActionsInteractor,
    @PowerMenuLog logBuffer: LogBuffer,
) : HydratedActivatable() {

    private val logger = Logger(logBuffer, "PowerMenuViewModel")

    /**
     * The definitive list of all global actions supported by this menu, in their fixed display
     * order.. The Power Menu has a fixed ordering of actions that should not be changed.
     */
    private val actions: List<GlobalActionViewModel> =
        listOf(
            lockFactory.create(),
            logoutFactory.create(),
            restartFactory.create(),
            shutdownFactory.create(),
        )

    /**
     * The list of global actions that are currently available to be displayed to the user. The
     * ordering in [actions] is preserved while filtering out actions that are not available.
     */
    val visibleActions: List<GlobalActionUiState.Visible> by
        interactor.availableGlobalActions
            .map { available ->
                actions
                    .filter { it.key in available }
                    .map { it.state }
                    .filterIsInstance<GlobalActionUiState.Visible>()
            }
            .onEach { visibleActions ->
                logger.d(
                    "Visible actions: ${visibleActions.map { it.key }} " +
                        "Available actions ${actions.map {it.key} }"
                )
            }
            .hydratedStateOf(initialValue = emptyList())

    override suspend fun onActivated(): Unit = coroutineScope {
        actions.forEach { child -> launch { child.activate() } }
    }

    @AssistedFactory
    interface Factory {
        fun create(): PowerMenuViewModel
    }
}
