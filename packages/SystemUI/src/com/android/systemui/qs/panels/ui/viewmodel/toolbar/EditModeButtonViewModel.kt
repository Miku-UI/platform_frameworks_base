/*
 * Copyright (C) 2024 The Android Open Source Project
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

import android.app.ActivityManager
import androidx.compose.runtime.getValue
import com.android.systemui.Flags.hsuQsChanges
import com.android.systemui.classifier.domain.interactor.FalsingInteractor
import com.android.systemui.lifecycle.ExclusiveActivatable
import com.android.systemui.lifecycle.Hydrator
import com.android.systemui.plugins.ActivityStarter
import com.android.systemui.plugins.FalsingManager
import com.android.systemui.qs.flags.QSEditModeTooltip
import com.android.systemui.qs.panels.domain.interactor.QSPreferencesInteractor
import com.android.systemui.qs.panels.ui.viewmodel.EditModeViewModel
import com.android.systemui.user.domain.interactor.HeadlessSystemUserMode
import com.android.systemui.user.domain.interactor.SelectedUserInteractor
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class EditModeButtonViewModel
@AssistedInject
constructor(
    private val editModeViewModel: EditModeViewModel,
    private val falsingInteractor: FalsingInteractor,
    private val activityStarter: ActivityStarter,
    private val hsum: HeadlessSystemUserMode,
    private val qsPreferencesInteractor: QSPreferencesInteractor,
    selectedUserInteractor: SelectedUserInteractor,
    @Assisted private val ignoreTestHarness: Boolean,
) : ExclusiveActivatable() {

    private val hydrator = Hydrator("editModeButtonViewModel.hydrator")

    /**
     * Avoid showing the tooltip when the shade is opened in test harness, as the tooltip will block
     * the first user input after being displayed.
     */
    private val runningInTestHarness =
        !ignoreTestHarness && ActivityManager.isRunningInUserTestHarness()

    val isEditButtonVisible: Boolean by
        hydrator.hydratedStateOf(
            traceName = "isEditButtonVisible",
            initialValue = false,
            source =
                selectedUserInteractor.selectedUser.map { selectedUserId ->
                    !hsuQsChanges() || !hsum.isHeadlessSystemUser(selectedUserId)
                },
        )

    val showTooltip by
        hydrator.hydratedStateOf(
            traceName = "showTooltip",
            source =
                if (QSEditModeTooltip.isEnabled && !runningInTestHarness) {
                    qsPreferencesInteractor.editTooltipShown.map {
                        // Show the tooltip if it wasn't shown before
                        !it
                    }
                } else {
                    flowOf(false)
                },
            initialValue = false,
        )

    fun onButtonClick() {
        if (!falsingInteractor.isFalseTap(FalsingManager.LOW_PENALTY)) {
            activityStarter.postQSRunnableDismissingKeyguard { editModeViewModel.startEditing() }
        }
    }

    fun onTooltipDisposed() {
        qsPreferencesInteractor.setEditTooltipShown(true)
    }

    override suspend fun onActivated(): Nothing {
        hydrator.activate()
    }

    @AssistedFactory
    interface Factory {
        fun create(ignoreTestHarness: Boolean = false): EditModeButtonViewModel
    }
}
