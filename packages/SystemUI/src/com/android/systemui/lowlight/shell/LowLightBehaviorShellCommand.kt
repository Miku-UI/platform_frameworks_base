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

package com.android.systemui.lowlight.shell

import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.lowlight.shared.model.LowLightDisplayBehavior
import com.android.systemui.statusbar.commandline.Command
import com.android.systemui.statusbar.commandline.CommandRegistry
import java.io.PrintWriter
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

@SysUISingleton
class LowLightBehaviorShellCommand
@Inject
constructor(private val commandRegistry: CommandRegistry) {
    private var enabled = false
    private val _forcedBehavior = MutableStateFlow<LowLightDisplayBehavior?>(null)

    val forcedBehavior: Flow<LowLightDisplayBehavior?> = _forcedBehavior

    fun setEnabled(enabled: Boolean) {
        if (this.enabled == enabled) {
            return
        }

        this.enabled = enabled

        if (enabled) {
            commandRegistry.registerCommand(COMMAND_ROOT) {
                LowLightBehaviorCommand { _forcedBehavior.value = it }
            }
        } else {
            commandRegistry.unregisterCommand(COMMAND_ROOT)
            _forcedBehavior.value = null
        }
    }

    private class LowLightBehaviorCommand(private val update: (LowLightDisplayBehavior?) -> Unit) :
        Command {
        override fun execute(pw: PrintWriter, args: List<String>) {
            val arg = args.getOrNull(0)
            if (arg == null || arg.lowercase() == "help") {
                help(pw)
                return
            }

            when (arg.lowercase()) {
                "screen-off" -> update(LowLightDisplayBehavior.SCREEN_OFF)
                "low-light-dream" -> update(LowLightDisplayBehavior.LOW_LIGHT_DREAM)
                "no-dream" -> update(LowLightDisplayBehavior.NO_DREAM)
                "disable" -> update(null)
                else -> {
                    pw.println("Invalid argument!")
                    help(pw)
                }
            }
        }

        override fun help(pw: PrintWriter) {
            pw.println("Usage: adb shell cmd statusbar $COMMAND_ROOT <cmd>")
            pw.println("Supported commands:")
            pw.println("  - screen-off")
            pw.println("    nothing will be shown on screen")
            pw.println("  - low-light-dream")
            pw.println("    device will use low light dream")
            pw.println("  - no-dream")
            pw.println("    device will not enter dream")
            pw.println("  - disable")
            pw.println("    disables low-light behavior")
        }
    }

    companion object {
        const val COMMAND_ROOT: String = "low-light-behavior"
    }
}
