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
import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import com.android.systemui.Flags.hsuQsChanges
import com.android.systemui.classifier.domain.interactor.FalsingInteractor
import com.android.systemui.lifecycle.HydratedActivatable
import com.android.systemui.plugins.ActivityStarter
import com.android.systemui.plugins.FalsingManager
import com.android.systemui.qs.panels.domain.interactor.QSPreferencesInteractor
import com.android.systemui.qs.panels.ui.viewmodel.EditModeViewModel
import com.android.systemui.scene.domain.interactor.DualShadeEducationInteractor
import com.android.systemui.shade.domain.interactor.ShadeModeInteractor
import com.android.systemui.shade.shared.model.ShadeMode
import com.android.systemui.user.domain.interactor.SelectedUserInteractor
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class EditModeButtonViewModel
@AssistedInject
constructor(
    private val editModeViewModel: EditModeViewModel,
    private val falsingInteractor: FalsingInteractor,
    private val activityStarter: ActivityStarter,
    private val qsPreferencesInteractor: QSPreferencesInteractor,
    selectedUserInteractor: SelectedUserInteractor,
    private val dualShadeEducationInteractor: DualShadeEducationInteractor,
    private val shadeModeInteractor: ShadeModeInteractor,
    @Assisted private val ignoreTestHarness: Boolean,
) : HydratedActivatable() {

    /**
     * Avoid showing the tooltip when the shade is opened in test harness, as the tooltip will block
     * the first user input after being displayed.
     */
    private val runningInTestHarness =
        !ignoreTestHarness && ActivityManager.isRunningInUserTestHarness()

    val isEditButtonVisible: Boolean by
        selectedUserInteractor.isCurrentUserHeadlessSystemUser
            .map { isHeadlessSystemUser -> !hsuQsChanges() || !isHeadlessSystemUser }
            .hydratedStateOf(initialValue = false)

    /** Whether or not the edit mode tooltip should be displayed. */
    var showTooltip by mutableStateOf(false)
        private set

    fun onButtonClick() {
        if (!falsingInteractor.isFalseTap(FalsingManager.LOW_PENALTY)) {
            activityStarter.postQSRunnableDismissingKeyguard { editModeViewModel.startEditing() }
        }
    }

    fun onTooltipDisposed() {
        showTooltip = false
        qsPreferencesInteractor.setEditTooltipShown(true)
    }

    override suspend fun onActivated() {
        coroutineScope { launch { showTooltipsAsNeeded() } }
    }

    private suspend fun showTooltipsAsNeeded() {
        if (!runningInTestHarness) {
            repeatWhenSpaceIsAvailable {
                repeatWhenTooltipStillNeedsToBeShown {
                    try {
                        delay(TOOLTIP_APPEARANCE_DELAY_MS)
                        showTooltip = true
                    } catch (e: CancellationException) {
                        showTooltip = false
                    }
                }
            }
        }
    }

    /** Executes [cancellable] whenever it is appropriate for the tooltip to appear. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun repeatWhenSpaceIsAvailable(cancellable: suspend () -> Unit) {
        shadeModeInteractor.shadeMode
            .map { it == ShadeMode.Dual }
            .distinctUntilChanged()
            .flatMapLatest { isDualShade ->
                if (isDualShade) {
                    // In dual shade, we need to check for the notification shade tooltip to make
                    // sure we don't try to show both tooltips at the same time.
                    snapshotFlow { !dualShadeEducationInteractor.isEducationInProgress }
                } else {
                    flowOf(true)
                }
            }
            .collectLatest { hasSpace ->
                if (hasSpace) {
                    cancellable()
                }
            }
    }

    private suspend fun repeatWhenTooltipStillNeedsToBeShown(cancellable: suspend () -> Unit) {
        qsPreferencesInteractor.editTooltipShown.distinctUntilChanged().collectLatest {
            tooltipWasShown ->
            if (!tooltipWasShown) {
                cancellable()
            }
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(ignoreTestHarness: Boolean = false): EditModeButtonViewModel
    }

    companion object {
        @VisibleForTesting const val TOOLTIP_APPEARANCE_DELAY_MS = 2000L
    }
}
