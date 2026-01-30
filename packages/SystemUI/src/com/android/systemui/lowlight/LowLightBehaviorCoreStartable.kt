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
package com.android.systemui.lowlight

import android.os.Flags
import android.os.UserHandle
import androidx.annotation.VisibleForTesting
import com.android.internal.logging.UiEventLogger
import com.android.systemui.CoreStartable
import com.android.systemui.common.domain.interactor.BatteryInteractorDeprecated
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.display.domain.interactor.DisplayStateInteractor
import com.android.systemui.dreams.domain.interactor.DreamSettingsInteractor
import com.android.systemui.keyguard.domain.interactor.KeyguardInteractor
import com.android.systemui.keyguard.shared.model.DozeStateModel.Companion.isDozeOff
import com.android.systemui.lowlight.domain.interactor.AmbientLowLightMonitorInteractor
import com.android.systemui.lowlight.domain.interactor.LowLightInteractor
import com.android.systemui.lowlight.domain.interactor.LowLightSettingsInteractor
import com.android.systemui.lowlight.shared.model.LowLightActionEntry
import com.android.systemui.lowlight.shared.model.LowLightDisplayBehavior
import com.android.systemui.lowlight.shared.model.ScreenState
import com.android.systemui.lowlight.shared.model.allowedInScreenState
import com.android.systemui.lowlight.shell.LowLightBehaviorShellCommand
import com.android.systemui.lowlight.shell.LowLightShellCommand
import com.android.systemui.lowlightclock.LowLightDockEvent
import com.android.systemui.lowlightclock.LowLightLogger
import com.android.systemui.power.domain.interactor.PowerInteractor
import com.android.systemui.statusbar.pipeline.battery.domain.interactor.BatteryInteractor
import com.android.systemui.statusbar.pipeline.battery.shared.StatusBarUniversalBatteryDataSource
import com.android.systemui.user.domain.interactor.UserLockedInteractor
import com.android.systemui.util.kotlin.BooleanFlowOperators.allOf
import com.android.systemui.util.kotlin.BooleanFlowOperators.anyOf
import com.android.systemui.util.kotlin.BooleanFlowOperators.not
import com.android.systemui.utils.coroutines.flow.conflatedCallbackFlow
import com.android.systemui.utils.coroutines.flow.flatMapLatestConflated
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Tracks environment (low-light or not) to trigger the proper action during low-light. */
class LowLightBehaviorCoreStartable
@Inject
constructor(
    private val dreamSettingsInteractor: DreamSettingsInteractor,
    displayStateInteractor: DisplayStateInteractor,
    private val lowLightInteractor: LowLightInteractor,
    private val lowLightSettingsInteractor: LowLightSettingsInteractor,
    private val logger: LowLightLogger,
    @Background private val scope: CoroutineScope,
    private val userLockedInteractor: UserLockedInteractor,
    keyguardInteractor: KeyguardInteractor,
    powerInteractor: PowerInteractor,
    ambientLightModeMonitorInteractor: AmbientLowLightMonitorInteractor,
    private val uiEventLogger: UiEventLogger,
    private val lowLightBehaviorShellCommand: LowLightBehaviorShellCommand,
    private val lowLightShellCommand: LowLightShellCommand,
    batteryInteractorDeprecated: BatteryInteractorDeprecated,
    batteryInteractor: BatteryInteractor,
) : CoreStartable {

    /** Whether the screen is currently on. */
    private val isScreenOn = not(displayStateInteractor.isDefaultDisplayOff).distinctUntilChanged()

    /** Whether device is plugged in */
    private val isPluggedIn =
        if (StatusBarUniversalBatteryDataSource.isEnabled) {
                batteryInteractor.isPluggedIn
            } else {
                batteryInteractorDeprecated.isDevicePluggedIn
            }
            .distinctUntilChanged()

    /** Whether the device is currently in a low-light environment. */
    private val isLowLightFromSensor =
        if (Flags.lowLightDreamBehavior()) {
            ambientLightModeMonitorInteractor.currentMonitor.flatMapLatestConflated { monitor ->
                monitor?.let {
                    conflatedCallbackFlow {
                            it.start { lowLightMode: Int -> trySend(lowLightMode) }
                            awaitClose { it.stop() }
                        }
                        .filterNot { it == AmbientLightModeMonitor.AMBIENT_LIGHT_MODE_UNDECIDED }
                        .map { it == AmbientLightModeMonitor.AMBIENT_LIGHT_MODE_DARK }
                        .distinctUntilChanged()
                        .onEach { isLowLight ->
                            uiEventLogger.log(
                                if (isLowLight) LowLightDockEvent.AMBIENT_LIGHT_TO_DARK
                                else LowLightDockEvent.AMBIENT_LIGHT_TO_LIGHT
                            )
                        }
                        // AmbientLightModeMonitor only supports a single callback, so ensure this
                        // is re-used if there are multiple subscribers.
                        .stateIn(
                            scope,
                            started = SharingStarted.WhileSubscribed(replayExpirationMillis = 0),
                            initialValue = false,
                        )
                } ?: MutableStateFlow(false)
            }
        } else {
            MutableStateFlow(false)
        }

    private val isLowLight: Flow<Boolean> =
        combine(lowLightShellCommand.forcedLowLight, isLowLightFromSensor) {
            forcedValue,
            sensorValue ->
            forcedValue ?: sensorValue
        }

    /**
     * Flow for tracking if a low light action is active due to a forced {@link
     * LowLightDisplayBehavior}.
     */
    private val forcedLowLightAction: Flow<LowLightActionEntry?> =
        lowLightBehaviorShellCommand.forcedBehavior.map { behavior ->
            behavior?.let { lowLightInteractor.getLowLightActionEntry(it) }
        }

    /** The currently active low light action with consideration of forced behavior. */
    private val activeLowLightAction: Flow<LowLightActionEntry?> =
        combine(forcedLowLightAction, lowLightInteractor.getActiveLowLightActionEntry()) {
                forcedAction,
                activeAction ->
                forcedAction ?: activeAction
            }
            .distinctUntilChanged()

    private val anyDoze: Flow<Boolean> =
        keyguardInteractor.dozeTransitionModel.map { !isDozeOff(it.to) }

    /**
     * Whether the device is idle (lockscreen showing or dreaming or asleep) and not in doze/AOD, as
     * we do not want to override doze/AOD with lowlight dream.
     */
    @OptIn(FlowPreview::class)
    private val isDeviceIdleAndNotDozing: Flow<Boolean> =
        allOf(
            not(anyDoze),
            anyOf(
                keyguardInteractor.isDreaming.debounce(
                    DREAM_STATE_DEBOUNCE_DURATION_MS.milliseconds
                ),
                keyguardInteractor.isKeyguardShowing,
                powerInteractor.isAsleep,
            ),
        )

    private fun shouldTrackLowLight(behavior: LowLightDisplayBehavior): Flow<Boolean> {
        return allOf(
                dreamSettingsInteractor.dreamingEnabled,
                isPluggedIn,
                anyOf(
                    allOf(isScreenOn, isDeviceIdleAndNotDozing),
                    allOf(not(isScreenOn), flowOf(behavior.allowedInScreenState(ScreenState.OFF))),
                ),
            )
            .flatMapLatestConflated {
                // The second set of conditions are separated from the above allOf flow combination
                // to prevent isLowLight flow from activating while we're not in the correct
                // conditions.

                // TODO(b/411522943): Re-evaluate if desired in direct-boot.

                // Force lowlight only if idle and in either direct-boot
                // mode or in a lowlight environment.
                if (it) {
                    anyOf(
                        isLowLight,
                        if (lowLightSettingsInteractor.allowLowLightBehaviorWhenLocked) {
                            not(userLockedInteractor.isUserUnlocked(UserHandle.CURRENT))
                        } else {
                            flowOf(false)
                        },
                    )
                } else {
                    flowOf(false)
                }
            }
            .distinctUntilChanged()
    }

    override fun start() {
        if (!Flags.lowLightDreamBehavior()) {
            return
        }

        lowLightShellCommand.setEnabled(true)
        lowLightBehaviorShellCommand.setEnabled(true)

        scope.launch {
            activeLowLightAction
                .flatMapLatestConflated { activeAction ->
                    activeAction?.let {
                        shouldTrackLowLight(it.behavior).map { enabled ->
                            if (enabled) it.action.get() else null
                        }
                    } ?: flowOf(null)
                }
                .collectLatest { action -> action?.activate() }
        }
    }

    companion object {
        private const val TAG = "LowLightBehaviorCoreStartable"

        @VisibleForTesting const val DREAM_STATE_DEBOUNCE_DURATION_MS = 100
    }
}
