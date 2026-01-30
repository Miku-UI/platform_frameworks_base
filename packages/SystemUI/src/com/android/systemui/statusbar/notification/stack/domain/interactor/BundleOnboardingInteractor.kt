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

import android.util.Log
import androidx.core.content.edit
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.statusbar.notification.data.repository.ActiveNotificationListRepository
import com.android.systemui.util.kotlin.BooleanFlowOperators.allOf
import com.android.systemui.util.kotlin.SharedPreferencesExt.observeBoolean
import com.android.systemui.utils.coroutines.flow.flatMapLatestConflated
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

class BundleOnboardingInteractor
@Inject
constructor(
    notifListRepo: ActiveNotificationListRepository,
    private val sharedPreferencesInteractor: NotificationsSharedPreferencesInteractor,
    @Background bgDispatcher: CoroutineDispatcher,
) {
    private val bundlesPresent: Flow<Boolean> =
        notifListRepo.activeNotifications
            .map { store -> store.bundles.isNotEmpty() }
            .distinctUntilChanged()

    private val onboardingUnseen: Flow<Boolean> =
        sharedPreferencesInteractor.sharedPreferences
            .flatMapLatestConflated { prefs ->
                prefs?.observeBoolean(KEY_SHOW_BUNDLE_ONBOARDING, true) ?: flowOf(false)
            }
            .distinctUntilChanged()

    val onboardingNeeded: Flow<Boolean> =
        allOf(onboardingUnseen, bundlesPresent).distinctUntilChanged().flowOn(bgDispatcher)

    fun markOnboardingDismissed() {
        Log.i(TAG, "dismissing onboarding")
        sharedPreferencesInteractor.sharedPreferences.value?.edit {
            putBoolean(KEY_SHOW_BUNDLE_ONBOARDING, false)
        } ?: Log.e(TAG, "Could not write to shared preferences")
    }

    fun resurrectOnboarding() {
        Log.i(TAG, "reviving onboarding")
        sharedPreferencesInteractor.sharedPreferences.value?.edit {
            putBoolean(KEY_SHOW_BUNDLE_ONBOARDING, true)
        } ?: Log.e(TAG, "Could not write to shared preferences")
    }
}

private const val TAG = "NotifBundles"
private const val KEY_SHOW_BUNDLE_ONBOARDING = "show_bundle_onboarding"
