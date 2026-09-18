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

package com.android.wm.shell.bubbles.util

import android.os.RemoteException
import com.android.wm.shell.bubbles.BubbleController
import com.android.wm.shell.bubbles.Bubbles
import com.android.wm.shell.sysui.ShellCommandHandler
import java.io.PrintWriter

/**
 * Handles adb shell commands for bubbles, typically for testing.
 *
 * Example usage to remove all bubbles:
 * - adb shell dumpsys activity service SystemUIService WMShell bubbles removeAll
 */
class BubbleShellCommandHandler(private val bubbleController: BubbleController) :
    ShellCommandHandler.ShellCommandActionHandler {

    override fun onShellCommand(args: Array<out String>, pw: PrintWriter): Boolean {
        when (args[0]) {
            "removeAll" -> return runRemoveAll(pw)
            else -> {
                pw.println("Invalid command: " + args[0])
                return false
            }
        }
    }

    override fun printShellCommandHelp(pw: PrintWriter, prefix: String) {
        pw.println("${prefix}removeAll")
        pw.println("$prefix  Removes all active bubbles")
    }

    private fun runRemoveAll(pw: PrintWriter): Boolean {
        try {
            bubbleController.mainExecutor.run {
                bubbleController.removeAllBubbles(Bubbles.DISMISS_NO_LONGER_BUBBLE)
            }
        } catch (e: RemoteException) {
            pw.println("Exception while removing all bubbles")
            e.printStackTrace(pw)
            return false
        }
        return true
    }
}
