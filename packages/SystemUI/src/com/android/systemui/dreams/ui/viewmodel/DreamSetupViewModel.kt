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

import android.content.Intent
import android.content.res.Resources
import android.provider.Settings
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.android.internal.logging.UiEventLogger
import com.android.systemui.communal.data.repository.ContextualSetupRepository
import com.android.systemui.communal.domain.definition.UprightChargingSetupDefinition
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.dreams.ui.metrics.DreamSetupUiEvent
import com.android.systemui.plugins.ActivityStarter
import com.android.systemui.res.R
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration
import kotlinx.coroutines.launch

/** Events that can be triggered from the dream setup screen. */
sealed interface DreamSetupEvent {
    /** Called when the setup flow is dismissed without taking action. */
    data object Dismiss : DreamSetupEvent

    /** Called when the user chooses not to set up the dream at this time. */
    data object NotNow : DreamSetupEvent

    /** Called when the user chooses to proceed with setting up the dream. */
    data object SetUp : DreamSetupEvent
}

class DreamSetupViewModel(
    private val activityStarter: ActivityStarter,
    private val contextualSetupRepository: ContextualSetupRepository,
    @Main private val resources: Resources,
    private val uiEventLogger: UiEventLogger,
) : ViewModel() {

    class Factory
    @Inject
    constructor(
        private val activityStarter: ActivityStarter,
        private val contextualSetupRepository: ContextualSetupRepository,
        @Main private val resources: Resources,
        private val uiEventLogger: UiEventLogger,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(DreamSetupViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return DreamSetupViewModel(
                    activityStarter,
                    contextualSetupRepository,
                    resources,
                    uiEventLogger,
                )
                    as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: $modelClass")
        }
    }

    fun onEvent(event: DreamSetupEvent) {
        when (event) {
            is DreamSetupEvent.Dismiss -> handleDismiss()
            is DreamSetupEvent.NotNow -> handleNotNow()
            is DreamSetupEvent.SetUp -> handleSetUp()
        }
    }

    private fun handleDismiss() {
        Log.d(TAG, "User dismissed the setup flow.")
        uiEventLogger.log(DreamSetupUiEvent.DREAM_SETUP_DISMISSED)
        viewModelScope.launch {
            val count =
                contextualSetupRepository.incrementFailureCount(
                    UprightChargingSetupDefinition.FLOW_ID
                )
            if (count >= maxDismissCount) {
                contextualSetupRepository.dismiss(UprightChargingSetupDefinition.FLOW_ID)
            }
        }
    }

    private fun handleNotNow() {
        Log.d(TAG, "User clicked 'Not now'.")
        uiEventLogger.log(DreamSetupUiEvent.DREAM_SETUP_SNOOZED)
        viewModelScope.launch {
            contextualSetupRepository.snooze(UprightChargingSetupDefinition.FLOW_ID, snoozeDuration)
        }
    }

    private val maxDismissCount: Int by lazy {
        resources.getInteger(R.integer.config_dream_setup_max_dismiss_count)
    }

    private val snoozeDuration: Duration by lazy {
        resources
            .getInteger(R.integer.config_dream_setup_snooze_duration_minutes)
            .toDuration(DurationUnit.MINUTES)
    }

    private fun handleSetUp() {
        Log.d(TAG, "User clicked 'Set up'.")
        uiEventLogger.log(DreamSetupUiEvent.DREAM_SETUP_TRIGGERED)
        activityStarter.postStartActivityDismissingKeyguard(
            Intent(Settings.ACTION_DREAM_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            /* delay */ 0,
            /* animationController */ null,
        )
    }

    companion object {
        private const val TAG = "DreamSetupViewModel"
    }
}
