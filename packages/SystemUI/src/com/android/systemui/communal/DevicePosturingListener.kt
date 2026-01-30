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

package com.android.systemui.communal

import android.annotation.SuppressLint
import android.app.DreamManager
import android.os.PowerManager
import android.service.dreams.Flags.allowDreamWhenPostured
import com.android.app.tracing.coroutines.launchInTraced
import com.android.app.tracing.coroutines.launchTraced
import com.android.systemui.CoreStartable
import com.android.systemui.common.domain.interactor.BatteryInteractorDeprecated
import com.android.systemui.communal.posturing.domain.interactor.PosturingInteractor
import com.android.systemui.communal.posturing.domain.interactor.PosturingInteractor.Companion.SLIDING_WINDOW_DURATION
import com.android.systemui.communal.posturing.shared.model.PosturedState
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.dreams.domain.interactor.DreamSettingsInteractor
import com.android.systemui.dreams.shared.model.WhenToDream
import com.android.systemui.log.dagger.CommunalTableLog
import com.android.systemui.log.table.TableLogBuffer
import com.android.systemui.log.table.logDiffsForTable
import com.android.systemui.power.domain.interactor.PowerInteractor
import com.android.systemui.statusbar.commandline.Command
import com.android.systemui.statusbar.commandline.CommandRegistry
import com.android.systemui.statusbar.pipeline.battery.domain.interactor.BatteryInteractor
import com.android.systemui.statusbar.pipeline.battery.shared.StatusBarUniversalBatteryDataSource
import com.android.systemui.util.kotlin.BooleanFlowOperators.allOf
import com.android.systemui.util.wakelock.WakeLock
import com.android.systemui.utils.coroutines.flow.flatMapLatestConflated
import java.io.PrintWriter
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

@SysUISingleton
class DevicePosturingListener
@Inject
constructor(
    private val commandRegistry: CommandRegistry,
    private val dreamManager: DreamManager,
    private val posturingInteractor: PosturingInteractor,
    dreamSettingsInteractor: DreamSettingsInteractor,
    private val batteryInteractorDeprecated: BatteryInteractorDeprecated,
    private val batteryInteractor: BatteryInteractor,
    @Background private val bgScope: CoroutineScope,
    @CommunalTableLog private val tableLogBuffer: TableLogBuffer,
    private val wakeLockBuilder: WakeLock.Builder,
    private val powerInteractor: PowerInteractor,
) : CoreStartable {
    private val command = DevicePosturingCommand()

    private val wakeLock by lazy {
        wakeLockBuilder
            .setMaxTimeout(2 * SLIDING_WINDOW_DURATION.inWholeMilliseconds)
            .setTag(TAG)
            .setLevelsAndFlags(PowerManager.SCREEN_DIM_WAKE_LOCK)
            .build()
    }

    private val isDevicePluggedIn =
        if (StatusBarUniversalBatteryDataSource.isEnabled) {
            batteryInteractor.isPluggedIn
        } else {
            batteryInteractorDeprecated.isDevicePluggedIn
        }

    // Only subscribe to posturing if applicable to avoid running the posturing CHRE nanoapp
    // if posturing signal is not needed.
    private val preconditions =
        allOf(
            isDevicePluggedIn,
            dreamSettingsInteractor.whenToDream.map { it == WhenToDream.WHILE_POSTURED },
        )

    private val postured =
        preconditions.flatMapLatestConflated { shouldListen ->
            if (shouldListen) {
                posturingInteractor.postured
            } else {
                flowOf(false)
            }
        }

    private val mayBePosturedSoon =
        preconditions.flatMapLatestConflated { shouldListen ->
            if (shouldListen) {
                allOf(posturingInteractor.mayBePostured, powerInteractor.isAwake)
            } else {
                flowOf(false)
            }
        }

    @SuppressLint("MissingPermission")
    override fun start() {
        if (!allowDreamWhenPostured()) {
            return
        }

        isDevicePluggedIn
            .logDiffsForTable(
                tableLogBuffer = tableLogBuffer,
                columnName = "isDevicePluggedIn",
                initialValue = false,
            )
            .launchInTraced("$TAG#collectIsDevicePluggedIn", bgScope)

        postured
            .distinctUntilChanged()
            .onEach { postured -> dreamManager.setDevicePostured(postured) }
            .launchInTraced("$TAG#collectPostured", bgScope)

        bgScope.launchTraced("$TAG#collectMayBePosturedSoon") {
            mayBePosturedSoon
                .debounce { mayBePostured ->
                    // Wait to release the WakeLock so we have time to update the dream state.
                    if (!mayBePostured) {
                        500.milliseconds
                    } else {
                        0.milliseconds
                    }
                }
                .dropWhile { !it }
                .distinctUntilChanged()
                .collect { mayBePosturedSoon ->
                    if (mayBePosturedSoon) {
                        wakeLock.acquire(TAG)
                    } else {
                        wakeLock.release(TAG)
                    }
                }
        }

        commandRegistry.registerCommand(COMMAND_ROOT) { command }
    }

    internal inner class DevicePosturingCommand : Command {
        @SuppressLint("MissingPermission")
        override fun execute(pw: PrintWriter, args: List<String>) {
            val arg = args.getOrNull(0)
            if (arg == null || arg.lowercase() == "help") {
                help(pw)
                return
            }

            val state =
                when (arg.lowercase()) {
                    "true" -> PosturedState.Postured
                    "false" ->
                        PosturedState.NotPostured(isStationary = false, inOrientation = false)

                    "clear" -> PosturedState.Unknown
                    else -> {
                        pw.println("Invalid argument!")
                        help(pw)
                        null
                    }
                }
            state?.let { posturingInteractor.setValueForDebug(it) }
        }

        override fun help(pw: PrintWriter) {
            pw.println("Usage: $ adb shell cmd statusbar device-postured <true|false|clear>")
        }
    }

    private companion object {
        const val COMMAND_ROOT = "device-postured"
        const val TAG = "DevicePosturingListener"
    }
}
