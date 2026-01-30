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

import com.android.systemui.statusbar.commandline.Command
import com.android.systemui.statusbar.commandline.CommandRegistry
import java.io.PrintWriter
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class LowLightShellCommand @Inject constructor(private val commandRegistry: CommandRegistry) {
    private val _forcedLowLight = MutableStateFlow<Boolean?>(null)
    private var enabled = false
    val forcedLowLight: Flow<Boolean?> = _forcedLowLight

    fun setEnabled(enabled: Boolean) {
        if (this.enabled == enabled) {
            return
        }

        this.enabled = enabled

        if (this.enabled) {
            commandRegistry.registerCommand(COMMAND_ROOT) {
                LowLightCommand { _forcedLowLight.value = it }
            }
        } else {
            commandRegistry.unregisterCommand(COMMAND_ROOT)
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
