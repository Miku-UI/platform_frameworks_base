/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.wm.shell.apptoweb

import com.android.window.flags.Flags
import com.android.wm.shell.sysui.ShellCommandHandler
import com.android.wm.shell.sysui.ShellController
import java.io.PrintWriter

/** Handles the shell commands for the app-to-web features. */
class AppToWebShellCommandHandler(
    private val appToWebRepository: AppToWebRepository,
    private val shellController: ShellController,
) : ShellCommandHandler.ShellCommandActionHandler {

    override fun onShellCommand(args: Array<String>, pw: PrintWriter): Boolean =
        when (args[0]) {
            "clearAppToWebFirstRunPromptAcked" -> runClearAppToWebFirstRunPromptAcked(args, pw)
            else -> {
                pw.println("Invalid command: ${args[0]}")
                false
            }
        }

    private fun runClearAppToWebFirstRunPromptAcked(args: Array<String>, pw: PrintWriter): Boolean {
        if (!Flags.enableEnhancedAppToWebTransition()) {
            pw.println("Not supported.")
            return false
        }
        if (args.size < 2) {
            pw.println("Error: package name should be provided as arguments")
            return false
        }
        val packageName = args[1]
        if (packageName.isEmpty()) {
            pw.println("Error: package name cannot be empty")
            return false
        }
        if (packageName == "all") {
            appToWebRepository.clearAllFirstRunPromptAckedPackages(shellController.currentUserId)
        } else {
            appToWebRepository.clearFirstRunPromptAckedPackages(
                shellController.currentUserId,
                packageName,
            )
        }
        return true
    }

    override fun printShellCommandHelp(pw: PrintWriter, prefix: String) {
        if (Flags.enableEnhancedAppToWebTransition()) {
            pw.println("$prefix clearAppToWebFirstRunPromptAcked <packageName|all>")
            pw.println(
                "$prefix  Clears the remembered first run prompt acked packages for the current" +
                    " user."
            )
        }
    }
}
