/*
 * Copyright 2025 The Android Open Source Project
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

package com.android.systemui.ambientcue.domain.interactor

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Rect
import androidx.core.content.edit
import com.android.systemui.ambientcue.data.repository.AmbientCueRepository
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.domain.interactor.SharedPreferencesInteractor
import com.android.systemui.keyguard.domain.interactor.KeyguardInteractor
import com.android.systemui.plugins.cuebar.ActionModel
import com.android.systemui.shade.domain.interactor.ShadeInteractor
import com.android.systemui.util.kotlin.SharedPreferencesExt.observeBoolean
import com.android.systemui.util.kotlin.SharedPreferencesExt.observeLong
import com.android.systemui.util.time.SystemClock
import com.android.systemui.utils.coroutines.flow.flatMapLatestConflated
import com.google.common.annotations.VisibleForTesting
import java.time.Duration
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

class AmbientCueInteractor
@Inject
constructor(
    private val repository: AmbientCueRepository,
    private val clock: SystemClock,
    shadeInteractor: ShadeInteractor,
    keyguardInteractor: KeyguardInteractor,
    sharedPreferencesInteractor: SharedPreferencesInteractor,
    @Application scope: CoroutineScope,
) {
    val isRootViewAttached: StateFlow<Boolean> = repository.isRootViewAttached
    val actions: Flow<List<ActionModel>> =
        combine(repository.actions, repository.dismissedGroups) { actions, dismissedGroupsMap ->
            // Filter Dismissed Actions
            actions.filter { action: ActionModel ->
                val groupId =
                    action.dismissalGroupId.takeIf { !it.isNullOrEmpty() } ?: return@filter true
                val expiry = dismissedGroupsMap[groupId] ?: return@filter true
                // If current time is after dismissal expiry, keep the action
                clock.currentTime().isAfter(expiry)
            }
        }
    val isImeVisible: StateFlow<Boolean> = repository.isImeVisible
    val isOccludedBySystemUi: Flow<Boolean> =
        combine(shadeInteractor.isShadeFullyCollapsed, keyguardInteractor.isKeyguardVisible) {
            isShadeFullyCollapsed,
            isKeyguardVisible ->
            !isShadeFullyCollapsed || isKeyguardVisible
        }
    val isGestureNav: StateFlow<Boolean> = repository.isGestureNav
    val recentsButtonPosition: StateFlow<Rect?> = repository.recentsButtonPosition
    val isTaskBarVisible: StateFlow<Boolean> = repository.isTaskBarVisible
    val isAmbientCueEnabled: StateFlow<Boolean> = repository.isAmbientCueEnabled
    val ambientCueTimeoutMs: StateFlow<Int> = repository.ambientCueTimeoutMs

    private val sharedPreferences: StateFlow<SharedPreferences?> =
        sharedPreferencesInteractor
            .sharedPreferences(SHARED_PREFERENCES_FILE_NAME, Context.MODE_PRIVATE)
            .stateIn(scope, SharingStarted.WhileSubscribed(), null)

    val firstTimeEducationShownAt: Flow<Long?> =
        sharedPreferences
            .flatMapLatestConflated { prefs ->
                // If the shared preference is not initialized, set the default value to 0L to avoid
                // showing the first time education.
                prefs?.observeLong(KEY_FIRST_TIME_ONBOARDING_SHOWN_AT, -1L) ?: flowOf(0L)
            }
            .map { if (it == -1L) null else it }
            .distinctUntilChanged()
    val shouldShowLongPressEducation: Flow<Boolean> =
        sharedPreferences
            .flatMapLatestConflated { prefs ->
                prefs?.observeBoolean(KEY_SHOW_LONG_PRESS_ONBOARDING, true) ?: flowOf(false)
            }
            .distinctUntilChanged()

    fun setDeactivated(isDeactivated: Boolean) {
        repository.isDeactivated.update { isDeactivated }
    }

    fun setImeVisible(isVisible: Boolean) {
        repository.isImeVisible.update { isVisible }
    }

    fun dismissGroupIds(dismissalGroupIds: Set<String>) {
        val now = clock.currentTime()
        val expiry = now + DISMISSAL_TTL
        repository.addDismissedGroups(dismissalGroupIds, expiry)
    }

    fun putSharedPrefsLong(key: String, value: Long) {
        sharedPreferences.value?.edit { putLong(key, value) }
    }

    fun putSharedPrefsBoolean(key: String, value: Boolean) {
        sharedPreferences.value?.edit { putBoolean(key, value) }
    }

    companion object {
        const val SHARED_PREFERENCES_FILE_NAME = "ambientcue_pref"
        const val KEY_FIRST_TIME_ONBOARDING_SHOWN_AT = "show_first_time_onboarding"
        const val KEY_SHOW_LONG_PRESS_ONBOARDING = "show_long_press_onboarding"

        @VisibleForTesting val DISMISSAL_TTL: Duration = Duration.ofDays(1)
    }
}
