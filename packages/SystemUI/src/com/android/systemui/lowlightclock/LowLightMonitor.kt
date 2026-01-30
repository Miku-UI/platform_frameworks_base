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
package com.android.systemui.lowlightclock

import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Flags
import android.os.UserHandle
import com.android.dream.lowlight.LowLightDreamManager
import com.android.internal.logging.UiEventLogger
import com.android.systemui.CoreStartable
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.display.domain.interactor.DisplayStateInteractor
import com.android.systemui.dreams.dagger.DreamModule
import com.android.systemui.dreams.domain.interactor.DreamSettingsInteractor
import com.android.systemui.dreams.shared.model.WhenToDream
import com.android.systemui.keyguard.domain.interactor.KeyguardInteractor
import com.android.systemui.keyguard.shared.model.DozeStateModel.Companion.isDozeOff
import com.android.systemui.lowlight.AmbientLightModeMonitor
import com.android.systemui.lowlight.domain.interactor.AmbientLowLightMonitorInteractor
import com.android.systemui.power.domain.interactor.PowerInteractor
import com.android.systemui.statusbar.commandline.Command
import com.android.systemui.statusbar.commandline.CommandRegistry
import com.android.systemui.user.domain.interactor.UserLockedInteractor
import com.android.systemui.util.kotlin.BooleanFlowOperators.allOf
import com.android.systemui.util.kotlin.BooleanFlowOperators.anyOf
import com.android.systemui.util.kotlin.BooleanFlowOperators.not
import com.android.systemui.utils.coroutines.flow.conflatedCallbackFlow
import com.android.systemui.utils.coroutines.flow.flatMapLatestConflated
import dagger.Lazy
import java.io.PrintWriter
import javax.inject.Inject
import javax.inject.Named
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Tracks environment (low-light or not) in order to correctly show or hide a low-light clock while
 * dreaming.
 */
class LowLightMonitor
@Inject
constructor(
    private val lowLightDreamManager: Lazy<LowLightDreamManager>,
    dreamSettingsInteractor: DreamSettingsInteractor,
    displayStateInteractor: DisplayStateInteractor,
    private val logger: LowLightLogger,
    @param:Named(DreamModule.LOW_LIGHT_DREAM_SERVICE)
    private val lowLightDreamService: ComponentName?,
    private val packageManager: PackageManager,
    @Background private val scope: CoroutineScope,
    private val commandRegistry: CommandRegistry,
    private val userLockedInteractor: UserLockedInteractor,
    keyguardInteractor: KeyguardInteractor,
    powerInteractor: PowerInteractor,
    ambientLightModeMonitorInteractor: AmbientLowLightMonitorInteractor,
    private val uiEventLogger: UiEventLogger,
) : CoreStartable {

    /** Whether the screen is currently on. */
    private val isScreenOn = not(displayStateInteractor.isDefaultDisplayOff).distinctUntilChanged()

    /** Whether dreams are enabled by the user. */
    private val dreamEnabled: Flow<Boolean> =
        dreamSettingsInteractor.whenToDream.map { it != WhenToDream.NEVER }

    /** Whether lowlight state is being forced to a specific value. */
    private val isLowLightForced: StateFlow<Boolean?> =
        if (Flags.lowLightDreamBehavior()) {
            MutableStateFlow(null)
        } else {
            conflatedCallbackFlow {
                    commandRegistry.registerCommand(COMMAND_ROOT) {
                        LowLightCommand { trySend(it) }
                    }
                    awaitClose { commandRegistry.unregisterCommand(COMMAND_ROOT) }
                }
                .stateIn(scope = scope, started = SharingStarted.Eagerly, initialValue = null)
        }

    /** Whether the device is currently in a low-light environment. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val isLowLightFromSensor: Flow<Boolean> =
        if (Flags.lowLightDreamBehavior()) {
            MutableStateFlow(false)
        } else {
            ambientLightModeMonitorInteractor.currentMonitor.flatMapLatest { ambientLightModeMonitor
                ->
                ambientLightModeMonitor?.let { monitor ->
                    conflatedCallbackFlow {
                            monitor.start { lowLightMode: Int -> trySend(lowLightMode) }
                            awaitClose { monitor.stop() }
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
        }

    private val isLowLight: Flow<Boolean> =
        combine(isLowLightForced, isLowLightFromSensor) { forcedValue, sensorValue ->
            forcedValue ?: sensorValue
        }

    private val anyDoze: Flow<Boolean> =
        keyguardInteractor.dozeTransitionModel.map { !isDozeOff(it.to) }

    /**
     * Whether the device is idle (lockscreen showing or dreaming or asleep) and not in doze/AOD, as
     * we do not want to override doze/AOD with lowlight dream.
     */
    private val isDeviceIdleAndNotDozing: Flow<Boolean> =
        allOf(
            not(anyDoze),
            anyOf(
                keyguardInteractor.isDreaming,
                keyguardInteractor.isKeyguardShowing,
                powerInteractor.isAsleep,
            ),
        )

    override fun start() {
        if (Flags.lowLightDreamBehavior()) {
            return
        }

        scope.launch {
            if (lowLightDreamService != null) {
                // Note that the dream service is disabled by default. This prevents the dream from
                // appearing in settings on devices that don't have it explicitly excluded (done in
                // the settings overlay). Therefore, the component is enabled if it is to be used
                // here.
                packageManager.setComponentEnabledSetting(
                    lowLightDreamService,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP,
                )
            } else {
                // If there is no low light dream service, do not observe conditions.
                return@launch
            }

            allOf(isScreenOn, dreamEnabled)
                .flatMapLatestConflated { conditionsMet ->
                    if (conditionsMet) {
                        // TODO(b/411522943): Re-evaluate if should be shown in direct-boot
                        // Force lowlight only if idle and in either direct-boot mode or in
                        // a lowlight environment.
                        allOf(
                            isDeviceIdleAndNotDozing,
                            anyOf(
                                isLowLight,
                                not(userLockedInteractor.isUserUnlocked(UserHandle.CURRENT)),
                            ),
                        )
                    } else {
                        flowOf(false)
                    }
                }
                .distinctUntilChanged()
                .collect {
                    logger.d(TAG, "Low light enabled: $it")
                    lowLightDreamManager
                        .get()
                        .setAmbientLightMode(
                            if (it) LowLightDreamManager.AMBIENT_LIGHT_MODE_LOW_LIGHT
                            else LowLightDreamManager.AMBIENT_LIGHT_MODE_REGULAR
                        )
                }
        }
    }

    private class LowLightCommand(private val update: (Boolean?) -> Unit) : Command {
        override fun execute(pw: PrintWriter, args: List<String>) {
            val arg = args.getOrNull(0)
            if (arg == null || arg.lowercase() == "help") {
                help(pw)
                return
            }

            when (arg.lowercase()) {
                "enable" -> update(true)
                "disable" -> update(false)
                "clear" -> update(null)
                else -> {
                    pw.println("Invalid argument!")
                    help(pw)
                }
            }
        }

        override fun help(pw: PrintWriter) {
            pw.println("Usage: adb shell cmd statusbar $COMMAND_ROOT <cmd>")
            pw.println("Supported commands:")
            pw.println("  - enable")
            pw.println("    forces device into low-light")
            pw.println("  - disable")
            pw.println("    forces device to not enter low-light")
            pw.println("  - clear")
            pw.println("    clears any previously forced state")
        }
    }

    companion object {
        private const val TAG = "LowLightMonitor"
        const val COMMAND_ROOT: String = "low-light"
    }
}
