/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.systemui.dreams.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import androidx.compose.runtime.getValue
import com.android.app.tracing.coroutines.withContextTraced
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.dreams.domain.interactor.DreamInteractor
import com.android.systemui.dreams.shared.model.toUiState
import com.android.systemui.dreams.ui.model.DreamItemUiModel
import com.android.systemui.lifecycle.HydratedActivatable
import com.android.systemui.plugins.ActivityStartOptions
import com.android.systemui.plugins.ActivityStarter
import com.android.systemui.settings.UserTracker
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.mapLatest

/** View model for the dream switcher dialog. */
class DreamSwitcherDialogViewModel
@AssistedInject
constructor(
    @param:Application private val context: Context,
    private val dreamInteractor: DreamInteractor,
    private val userTracker: UserTracker,
    @param:Background private val bgDispatcher: CoroutineDispatcher,
    private val activityStarter: ActivityStarter,
    @Assisted private val dialogController: DreamDialogController,
) : HydratedActivatable(enableEnqueuedActivations = true) {

    private companion object {
        const val TAG = "DreamSwitcherDialogViewModel"
    }

    /** The list of dream items to display in the switcher dialog. */
    val dreamItems: List<DreamItemUiModel> by
        dreamInteractor.dreamState
            .mapLatest {
                withContextTraced("$TAG#toUiState", bgDispatcher) {
                    it.toUiState { icon -> icon.loadDrawableAsUser(context, userTracker.userId) }
                }
            }
            .hydratedStateOf(initialValue = emptyList())

    /**
     * Called when a dream is selected by the user.
     *
     * @param dream The dream that was selected.
     */
    fun onDreamSelected(dream: DreamItemUiModel) {
        if (dream.active) {
            Log.d(TAG, "Dream is already active: $dream")
            return
        }
        enqueueOnActivatedScope {
            dreamInteractor.setActiveDream(dream.componentName, userTracker.userHandle)
        }
    }

    /**
     * Called when the edit button for a dream is clicked.
     *
     * @param dream The dream for which the edit button was clicked.
     */
    fun onEditDreamClicked(dream: DreamItemUiModel) {
        val intent =
            if (dream.settingsActivity != null) {
                Intent().setComponent(dream.settingsActivity)
            } else {
                dream.appInfo?.launchIntent
            }

        if (intent == null) {
            Log.e(TAG, "No launch intent found for dream: $dream")
            return
        }
        val options = ActivityStartOptions(intent)
        activityStarter.startActivityDismissingKeyguard(options)
        dialogController.dismissDialog()
    }

    /** Called when the "Open settings" button is clicked. */
    fun onOpenSettingsClicked() {
        val options = ActivityStartOptions(Intent(Settings.ACTION_DREAM_SETTINGS))
        activityStarter.startActivityDismissingKeyguard(options)
        dialogController.dismissDialog()
    }

    /** Factory for [DreamSwitcherDialogViewModel]. */
    @AssistedFactory
    fun interface Factory {
        /** Creates a new [DreamSwitcherDialogViewModel]. */
        fun create(dialogController: DreamDialogController): DreamSwitcherDialogViewModel
    }
}
