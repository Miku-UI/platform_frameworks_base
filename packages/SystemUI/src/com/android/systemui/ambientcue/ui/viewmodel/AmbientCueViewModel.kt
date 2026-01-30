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

package com.android.systemui.ambientcue.ui.viewmodel

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toComposeRect
import androidx.core.content.edit
import com.android.app.tracing.coroutines.coroutineScopeTraced
import com.android.systemui.Dumpable
import com.android.systemui.ambientcue.domain.interactor.AmbientCueInteractor
import com.android.systemui.ambientcue.shared.logger.AmbientCueLogger
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.domain.interactor.SharedPreferencesInteractor
import com.android.systemui.dump.DumpManager
import com.android.systemui.lifecycle.ExclusiveActivatable
import com.android.systemui.lifecycle.Hydrator
import com.android.systemui.util.kotlin.SharedPreferencesExt.observeBoolean
import com.android.systemui.util.kotlin.SharedPreferencesExt.observeLong
import com.android.systemui.util.kotlin.launchAndDispose
import com.android.systemui.util.time.SystemClock
import com.android.systemui.utils.coroutines.flow.flatMapLatestConflated
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import java.io.PrintWriter
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AmbientCueViewModel
@AssistedInject
constructor(
    private val ambientCueInteractor: AmbientCueInteractor,
    private val systemClock: SystemClock,
    private val dumpManager: DumpManager,
    private val sharedPreferencesInteractor: SharedPreferencesInteractor,
    private val ambientCueLogger: AmbientCueLogger,
    @Application scope: CoroutineScope,
) : ExclusiveActivatable(), Dumpable {

    private val hydrator = Hydrator("AmbientCueViewModel.hydrator")

    private val isRootViewAttached: Boolean by
        hydrator.hydratedStateOf(
            traceName = "isRootViewAttached",
            initialValue = false,
            source = ambientCueInteractor.isRootViewAttached,
        )

    private val isImeVisible: Boolean by
        hydrator.hydratedStateOf(
            traceName = "isImeVisible",
            initialValue = false,
            source = ambientCueInteractor.isImeVisible,
        )

    private val isOccludedBySystemUi: Boolean by
        hydrator.hydratedStateOf(
            traceName = "isOccludedBySystemUi",
            initialValue = false,
            source = ambientCueInteractor.isOccludedBySystemUi,
        )

    private val ambientCueTimeoutMs: Int by
        hydrator.hydratedStateOf(
            traceName = "ambientCueTimeoutMs",
            initialValue = AMBIENT_CUE_TIMEOUT_MS,
            source = ambientCueInteractor.ambientCueTimeoutMs,
        )

    val isVisible: Boolean
        get() = isRootViewAttached && !isImeVisible && !isOccludedBySystemUi

    var isExpanded: Boolean by mutableStateOf(false)
        private set

    private val sharedPreferences: StateFlow<SharedPreferences?> =
        sharedPreferencesInteractor
            .sharedPreferences(SHARED_PREFERENCES_FILE_NAME, Context.MODE_PRIVATE)
            .stateIn(scope, SharingStarted.WhileSubscribed(), null)

    private val firstTimeEducationShownAt: Flow<Long?> =
        sharedPreferences
            .flatMapLatestConflated { prefs ->
                // If the shared preference is not initialized, set the default value to 0L to avoid
                // showing the first time education.
                prefs?.observeLong(KEY_FIRST_TIME_ONBOARDING_SHOWN_AT, -1L) ?: flowOf(0L)
            }
            .map { if (it == -1L) null else it }
            .distinctUntilChanged()
    private val shouldShowLongPressEducation: Flow<Boolean> =
        sharedPreferences
            .flatMapLatestConflated { prefs ->
                prefs?.observeBoolean(KEY_SHOW_LONG_PRESS_ONBOARDING, true) ?: flowOf(false)
            }
            .distinctUntilChanged()

    val showFirstTimeEducation: Boolean by
        hydrator.hydratedStateOf(
            traceName = "showFirstTimeEducation",
            source = firstTimeEducationShownAt.map { it == null },
            initialValue = false,
        )

    val showLongPressEducation: Boolean by
        hydrator.hydratedStateOf(
            traceName = "showLongPressEducation",
            initialValue = false,
            source =
                combine(
                    shouldShowLongPressEducation,
                    firstTimeEducationShownAt,
                    ambientCueInteractor.isRootViewAttached,
                ) { shouldShowLongPressEducation, firstTimeEducationShownAt, _ ->
                    Log.i(
                        TAG,
                        "showLongPressEducation: $shouldShowLongPressEducation " +
                            "$firstTimeEducationShownAt",
                    )
                    val firstTimeSeenAtMs =
                        (firstTimeEducationShownAt ?: systemClock.currentTimeMillis()).milliseconds
                    firstTimeSeenAtMs + ONBOARDING_DELAY <
                        systemClock.currentTimeMillis().milliseconds && shouldShowLongPressEducation
                },
        )

    val pillStyle: PillStyleViewModel by
        hydrator.hydratedStateOf(
            traceName = "pillStyle",
            initialValue = PillStyleViewModel.Uninitialized,
            source =
                combine(
                    ambientCueInteractor.isGestureNav,
                    ambientCueInteractor.isTaskBarVisible,
                    ambientCueInteractor.recentsButtonPosition,
                ) { isGestureNav, isTaskBarVisible, recentsButtonPosition ->
                    if (isGestureNav && !isTaskBarVisible) {
                        PillStyleViewModel.NavBarPillStyle
                    } else {
                        val position =
                            if (isGestureNav) {
                                null
                            } else {
                                recentsButtonPosition
                            }
                        PillStyleViewModel.ShortPillStyle(position?.toComposeRect())
                    }
                },
        )

    @OptIn(FlowPreview::class)
    val actions: List<ActionViewModel> by
        hydrator.hydratedStateOf(
            traceName = "actions",
            initialValue = listOf(),
            source =
                ambientCueInteractor.actions
                    .debounce { actions -> if (actions.isEmpty()) ACTIONS_DEBOUNCE_MS else 0L }
                    .map { actions ->
                        actions.map { action ->
                            ActionViewModel(
                                icon =
                                    IconViewModel(
                                        large = action.icon.large,
                                        small = action.icon.small,
                                        iconId = action.icon.iconId,
                                        repeatCount = 0,
                                    ),
                                label = action.label,
                                attribution = action.attribution,
                                onClick = {
                                    action.onPerformAction()
                                    collapse()
                                },
                                onLongClick = {
                                    action.onPerformLongClick()
                                    // Long press onboarding only triggers 7 days after the initial
                                    // onboarding. That said, we'd like to suppress it in case the
                                    // user discovers the gesture on their own. For this reason, we
                                    // don't check if the tooltip is visible before updating the
                                    // shared preference.
                                    sharedPreferences.value?.edit {
                                        putBoolean(KEY_SHOW_LONG_PRESS_ONBOARDING, false)
                                    }
                                },
                                actionType =
                                    when (action.actionType) {
                                        "ma" -> ActionType.MA
                                        "mr" -> ActionType.MR
                                        else -> ActionType.Unknown
                                    },
                                oneTapEnabled = action.oneTapEnabled,
                                oneTapDelayMs = action.oneTapDelayMs,
                            )
                        }
                    },
        )

    fun expand() {
        isExpanded = true
        disableFirstTimeHint()
    }

    fun collapse() {
        if (isExpanded) {
            isExpanded = false
            disableLongPressHint()
        }
    }

    fun hide() {
        ambientCueInteractor.setDeactivated(true)
        isExpanded = false
        disableFirstTimeHint()
        ambientCueLogger.setClickedCloseButtonStatus()
    }

    private var deactivateCueBarJob: Job? = null

    fun cancelDeactivation() {
        deactivateCueBarJob?.cancel()
    }

    suspend fun delayAndDeactivateCueBar() {
        deactivateCueBarJob?.cancel()

        coroutineScopeTraced("AmbientCueViewModel") {
            deactivateCueBarJob = launch {
                delay(ambientCueTimeoutMs.milliseconds)
                ambientCueInteractor.setDeactivated(true)
                ambientCueLogger.setReachedTimeoutStatus()
            }
        }
    }

    override suspend fun onActivated(): Nothing {
        coroutineScopeTraced("AmbientCueViewModel") {
            launch { hydrator.activate() }
            launch {
                // Hide the UI if the user doesn't interact with it after N seconds
                ambientCueInteractor.isRootViewAttached.collectLatest { isAttached ->
                    if (!isAttached) {
                        cancelDeactivation()
                        return@collectLatest
                    }
                    delayAndDeactivateCueBar()
                }
            }
            launchAndDispose {
                dumpManager.registerNormalDumpable(TAG, this@AmbientCueViewModel)
                DisposableHandle { dumpManager.unregisterDumpable(TAG) }
            }
            awaitCancellation()
        }
    }

    fun disableFirstTimeHint() {
        if (showFirstTimeEducation) {
            sharedPreferences.value?.edit {
                Log.i(TAG, "suppressing first time tooltip")
                putLong(KEY_FIRST_TIME_ONBOARDING_SHOWN_AT, systemClock.currentTimeMillis())
            }
        }
    }

    private fun disableLongPressHint() {
        if (showLongPressEducation) {
            sharedPreferences.value?.edit {
                Log.i(TAG, "suppressing long press tooltip")
                putBoolean(KEY_SHOW_LONG_PRESS_ONBOARDING, false)
            }
        }
    }

    override fun dump(pw: PrintWriter, args: Array<out String>) {
        pw.println("isRootViewAttached: $isRootViewAttached")
        pw.println("isImeVisible: $isImeVisible")
        pw.println("isVisible: $isVisible")
        pw.println("isExpanded: $isExpanded")
        pw.println("pillStyle: $pillStyle")
        pw.println("deactivateCueBarJob: $deactivateCueBarJob")
        pw.println("actions: $actions")
        pw.println("ambientCueTimeoutMs: $ambientCueTimeoutMs")
    }

    @AssistedFactory
    interface Factory {
        fun create(): AmbientCueViewModel
    }

    companion object {
        private const val TAG = "AmbientCueViewModel"
        @VisibleForTesting const val AMBIENT_CUE_TIMEOUT_MS = 30_000
        // For how long we should wait until we can show the long press hint
        private val ONBOARDING_DELAY = 7.days
        private const val SHARED_PREFERENCES_FILE_NAME = "ambientcue_pref"
        private const val KEY_FIRST_TIME_ONBOARDING_SHOWN_AT = "show_first_time_onboarding"
        private const val KEY_SHOW_LONG_PRESS_ONBOARDING = "show_long_press_onboarding"
        private const val ACTIONS_DEBOUNCE_MS = 300L
    }
}
