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

import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toComposeRect
import com.android.app.tracing.coroutines.coroutineScopeTraced
import com.android.systemui.Dumpable
import com.android.systemui.ambientcue.domain.interactor.AmbientCueInteractor
import com.android.systemui.ambientcue.domain.interactor.AmbientCueInteractor.Companion.KEY_FIRST_TIME_ONBOARDING_SHOWN_AT
import com.android.systemui.ambientcue.domain.interactor.AmbientCueInteractor.Companion.KEY_SHOW_LONG_PRESS_ONBOARDING
import com.android.systemui.ambientcue.shared.flag.AmbientCueFlag
import com.android.systemui.ambientcue.shared.logger.AmbientCueLogger
import com.android.systemui.dump.DumpManager
import com.android.systemui.lifecycle.HydratedActivatable
import com.android.systemui.util.kotlin.launchAndDispose
import com.android.systemui.util.time.SystemClock
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import java.io.PrintWriter
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class AmbientCueViewModel
@AssistedInject
constructor(
    private val ambientCueInteractor: AmbientCueInteractor,
    private val systemClock: SystemClock,
    private val dumpManager: DumpManager,
    private val ambientCueLogger: AmbientCueLogger,
) : HydratedActivatable(), Dumpable {

    private val isRootViewAttached: Boolean by
        ambientCueInteractor.isRootViewAttached.hydratedStateOf(initialValue = false)

    val isImeVisible: Boolean by
        ambientCueInteractor.isImeVisible.hydratedStateOf(initialValue = false)

    private val isOccludedBySystemUi: Boolean by
        ambientCueInteractor.isOccludedBySystemUi.hydratedStateOf(initialValue = false)

    private val ambientCueTimeoutMs: Int by
        ambientCueInteractor.ambientCueTimeoutMs.hydratedStateOf(
            initialValue = AMBIENT_CUE_TIMEOUT_MS
        )

    val isVisible: Boolean
        get() =
            isRootViewAttached &&
                (!isImeVisible || AmbientCueFlag.isAmbientCueWithImeVisibleEnabled) &&
                actions.isNotEmpty() &&
                !isOccludedBySystemUi

    var isExpanded: Boolean by mutableStateOf(false)
        private set

    val showFirstTimeEducation: Boolean by
        ambientCueInteractor.firstTimeEducationShownAt
            .map { it == null }
            .hydratedStateOf(initialValue = false)

    val showLongPressEducation: Boolean by
        combine(
                ambientCueInteractor.shouldShowLongPressEducation,
                ambientCueInteractor.firstTimeEducationShownAt,
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
            }
            .hydratedStateOf(initialValue = false)

    val pillStyle: PillStyleViewModel by
        combine(
                ambientCueInteractor.isGestureNav,
                ambientCueInteractor.isTaskBarVisible,
                ambientCueInteractor.recentsButtonPosition,
            ) { isGestureNav, isTaskBarVisible, recentsButtonPosition ->
                if (isGestureNav) {
                    if (isTaskBarVisible) {
                        PillStyleViewModel.NoPillStyle
                    } else {
                        PillStyleViewModel.NavBarPillStyle
                    }
                } else {
                    val position = recentsButtonPosition
                    PillStyleViewModel.ShortPillStyle(position?.toComposeRect())
                }
            }
            .hydratedStateOf(initialValue = PillStyleViewModel.Uninitialized)

    @OptIn(FlowPreview::class)
    val actions: List<ActionViewModel> by
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
                            ambientCueInteractor.putSharedPrefsBoolean(
                                KEY_SHOW_LONG_PRESS_ONBOARDING,
                                false,
                            )
                        },
                        actionType =
                            when (action.actionType) {
                                "ma" -> ActionType.MA
                                "mr" -> ActionType.MR
                                else -> ActionType.Unknown
                            },
                        oneTapEnabled = action.oneTapEnabled,
                        oneTapDelayMs = action.oneTapDelayMs,
                        dismissalGroupId = action.dismissalGroupId,
                    )
                }
            }
            .hydratedStateOf(initialValue = listOf())

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

        actions
            .mapNotNull { it.dismissalGroupId }
            .filter { it.isNotEmpty() }
            .toSet()
            .takeIf { it.isNotEmpty() }
            ?.let { idsToDismiss: Set<String> ->
                ambientCueInteractor.dismissGroupIds(idsToDismiss)
            }

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

    override suspend fun onActivated() {
        coroutineScopeTraced("AmbientCueViewModel") {
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
        }
    }

    fun disableFirstTimeHint() {
        if (showFirstTimeEducation) {
            Log.i(TAG, "suppressing first time tooltip")
            ambientCueInteractor.putSharedPrefsLong(
                KEY_FIRST_TIME_ONBOARDING_SHOWN_AT,
                systemClock.currentTimeMillis(),
            )
        }
    }

    private fun disableLongPressHint() {
        if (showLongPressEducation) {
            Log.i(TAG, "suppressing long press tooltip")
            ambientCueInteractor.putSharedPrefsBoolean(KEY_SHOW_LONG_PRESS_ONBOARDING, false)
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
        @VisibleForTesting const val ACTIONS_DEBOUNCE_MS = 300L
        // For how long we should wait until we can show the long press hint
        private val ONBOARDING_DELAY = 7.days
        private const val SHARED_PREFERENCES_FILE_NAME = "ambientcue_pref"
        private const val KEY_FIRST_TIME_ONBOARDING_SHOWN_AT = "show_first_time_onboarding"
        private const val KEY_SHOW_LONG_PRESS_ONBOARDING = "show_long_press_onboarding"
    }
}
