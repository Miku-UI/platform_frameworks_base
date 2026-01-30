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

package com.android.systemui.statusbar.notification.stack.domain.interactor

import android.app.INotificationManager
import android.service.notification.Adjustment.KEY_SUMMARIZATION
import android.util.Log
import androidx.core.content.edit
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.statusbar.notification.data.repository.ActiveNotificationListRepository
import com.android.systemui.statusbar.notification.shared.NotifStyle
import com.android.systemui.user.domain.interactor.SelectedUserInteractor
import com.android.systemui.util.kotlin.BooleanFlowOperators.allOf
import com.android.systemui.util.kotlin.SharedPreferencesExt.observeBoolean
import com.android.systemui.utils.coroutines.flow.flatMapLatestConflated
import com.android.systemui.utils.coroutines.flow.mapLatestConflated
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class SummarizationOnboardingInteractor
@Inject
constructor(
    notifListRepo: ActiveNotificationListRepository,
    private val sharedPreferencesInteractor: NotificationsSharedPreferencesInteractor,
    private val notificationManager: INotificationManager,
    userInteractor: SelectedUserInteractor,
    @Background private val bgDispatcher: CoroutineDispatcher,
) {
    private val notifsPresent: Flow<Boolean> =
        notifListRepo.activeNotifications
            .map { store ->
                store.renderList.isNotEmpty() &&
                    store.individuals.any { (_, notif) -> notif.style is NotifStyle.Messaging }
            }
            .distinctUntilChanged()

    private val onboardingUnseen: Flow<Boolean> =
        sharedPreferencesInteractor.sharedPreferences
            .flatMapLatestConflated { prefs ->
                prefs?.observeBoolean(KEY_SHOW_SUMMARIZATION_ONBOARDING, true) ?: flowOf(false)
            }
            .distinctUntilChanged()

    private val summarizationAvailableAndDisabled: Flow<Boolean> =
        userInteractor.selectedUser.mapLatestConflated { userId -> isAvailableAndDisabled(userId) }

    val onboardingNeeded: Flow<Boolean> =
        allOf(onboardingUnseen, summarizationAvailableAndDisabled)
            .distinctUntilChanged()
            .flatMapLatestConflated { if (it) notifsPresent else flowOf(false) }
            .distinctUntilChanged()
            .flowOn(bgDispatcher)

    fun markOnboardingDismissed() {
        Log.i(TAG, "dismissing onboarding")
        sharedPreferencesInteractor.sharedPreferences.value?.edit {
            putBoolean(KEY_SHOW_SUMMARIZATION_ONBOARDING, false)
        } ?: Log.e(TAG, "Could not write to shared preferences")
    }

    fun resurrectOnboarding() {
        Log.i(TAG, "reviving onboarding")
        sharedPreferencesInteractor.sharedPreferences.value?.edit {
            putBoolean(KEY_SHOW_SUMMARIZATION_ONBOARDING, true)
        } ?: Log.e(TAG, "Could not write to shared preferences")
    }

    private suspend fun isAvailableAndDisabled(userId: Int): Boolean =
        withContext(bgDispatcher) {
            KEY_SUMMARIZATION !in notificationManager.unsupportedAdjustmentTypes &&
                KEY_SUMMARIZATION !in
                    notificationManager.getAllowedAssistantAdjustmentsForUser(userId)
        }
}

private const val TAG = "NotifSummaries"
private const val KEY_SHOW_SUMMARIZATION_ONBOARDING = "show_summarization_onboarding"
