/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.wm.shell.desktopmode

import android.app.ActivityTaskManager.INVALID_TASK_ID
import com.android.window.flags.Flags
import com.android.wm.shell.desktopmode.DesktopModeEventLogger.Companion.EnterReason
import com.android.wm.shell.desktopmode.DesktopModeEventLogger.Companion.ExitReason
import com.android.wm.shell.desktopmode.DesktopModeEventLogger.Companion.UnminimizeReason
import com.android.wm.shell.desktopmode.homescreenpeeking.DesktopHomeScreenPeekController
import com.android.wm.shell.shared.desktopmode.DesktopModeTransitionSource.ADB_COMMAND
import com.android.wm.shell.sysui.ShellCommandHandler
import com.android.wm.shell.sysui.ShellController
import com.android.wm.shell.sysui.ShellInit
import com.android.wm.shell.transition.FocusTransitionObserver
import java.io.PrintWriter
import java.util.Optional

/** Handles the shell commands for the DesktopTasksController. */
class DesktopModeShellCommandHandler(
    private val controller: Optional<DesktopTasksController>,
    private val focusTransitionObserver: FocusTransitionObserver,
    private val userRepositories: DesktopUserRepositories,
    private val shellController: ShellController,
    desktopState: ShellDesktopState,
    private val shellCommandHandler: ShellCommandHandler,
    shellInit: ShellInit,
    private val desktopHomeScreenPeekController: DesktopHomeScreenPeekController,
) : ShellCommandHandler.ShellCommandActionHandler {

    init {
        if (desktopState.canEnterDesktopMode) {
            shellInit.addInitCallback({ onInit() }, this)
        }
    }

    private fun onInit() {
        shellCommandHandler.addCommandCallback("desktopmode", this, this)
    }

    override fun onShellCommand(args: Array<String>, pw: PrintWriter): Boolean {
        if (args.isEmpty()) {
            return printInvalidCommandAndShowHelp(args, pw)
        }
        return when (args[0]) {
            "moveTaskToDesk" -> runMoveTaskToDesk(args, pw)
            "moveToNextDisplay" -> runMoveToNextDisplay(args, pw)
            "createDesk" -> runCreateDesk(args, pw)
            "activateDesk" -> runActivateDesk(args, pw)
            "removeDesk" -> runRemoveDesk(args, pw)
            "removeAllDesks" -> runRemoveAllDesks(args, pw)
            "moveTaskToFront" -> runMoveTaskToFront(args, pw)
            "moveTaskOutOfDesk" -> runMoveTaskOutOfDesk(args, pw)
            "canCreateDesk" -> runCanCreateDesk(args, pw)
            "getActiveDeskId" -> runGetActiveDeskId(args, pw)
            "clearRememberedBounds" -> runClearRememberedBounds(args, pw)
            "clearAllRememberedBounds" -> runClearAllRememberedBounds(args, pw)
            "peek" -> runPeekHomeScreen(args, pw)
            else -> printInvalidCommandAndShowHelp(args, pw)
        }
    }

    private fun runMoveTaskToDesk(args: Array<String>, pw: PrintWriter): Boolean {
        if (args.size < 2) {
            // First argument is the action name.
            pw.println("Error: task id should be provided as arguments")
            return false
        }
        var taskId =
            try {
                args[1].toInt()
            } catch (e: NumberFormatException) {
                pw.println("Error: task id should be an integer")
                return false
            }

        if (taskId == 0) {
            taskId = focusTransitionObserver.globallyFocusedTaskId
        }

        if (taskId == INVALID_TASK_ID) {
            pw.println("Error: no appropriate task found")
            return false
        }

        if (args.size < 3) {
            pw.println("Error: desk id should be provided as arguments")
            return false
        }
        val deskId =
            try {
                args[2].toInt()
            } catch (e: NumberFormatException) {
                pw.println("Error: desk id should be an integer")
                return false
            }
        controller
            .get()
            .moveTaskToDesk(taskId = taskId, deskId = deskId, transitionSource = ADB_COMMAND)
        return true
    }

    private fun runMoveToNextDisplay(args: Array<String>, pw: PrintWriter): Boolean {
        var taskId = INVALID_TASK_ID
        if (args.size < 2) {
            taskId = focusTransitionObserver.globallyFocusedTaskId
        } else {
            try {
                taskId = args[1].toInt()
            } catch (e: NumberFormatException) {
                pw.println("Error: task id should be an integer")
                return false
            }
        }
        if (taskId == INVALID_TASK_ID) {
            pw.println("Error: no appropriate task found")
            return false
        }
        controller.get().moveToNextDisplay(taskId, enterReason = EnterReason.ADB_COMMAND)
        return true
    }

    private fun runCreateDesk(args: Array<String>, pw: PrintWriter): Boolean {
        if (args.size < 2) {
            // First argument is the action name.
            pw.println("Error: desk id should be provided as arguments")
            return false
        }
        val displayId =
            try {
                args[1].toInt()
            } catch (e: NumberFormatException) {
                pw.println("Error: display id should be an integer")
                return false
            }
        controller.get().createDesk(displayId)
        return true
    }

    private fun runActivateDesk(args: Array<String>, pw: PrintWriter): Boolean {
        if (args.size < 2) {
            // First argument is the action name.
            pw.println("Error: desk id should be provided as arguments")
            return false
        }
        val deskId =
            try {
                args[1].toInt()
            } catch (e: NumberFormatException) {
                pw.println("Error: desk id should be an integer")
                return false
            }
        controller.get().activateDesk(deskId = deskId, enterReason = EnterReason.ADB_COMMAND)
        return true
    }

    private fun runRemoveDesk(args: Array<String>, pw: PrintWriter): Boolean {
        if (args.size < 2) {
            // First argument is the action name.
            pw.println("Error: desk id should be provided as arguments")
            return false
        }
        val deskId =
            try {
                args[1].toInt()
            } catch (e: NumberFormatException) {
                pw.println("Error: desk id should be an integer")
                return false
            }
        controller
            .get()
            .removeDesk(
                deskId = deskId,
                exitReason = ExitReason.ADB_COMMAND_EXIT,
                shouldEndUpAtHome = true,
                skipWallpaperAndHomeOrdering = false,
            )
        return true
    }

    private fun runRemoveAllDesks(args: Array<String>, pw: PrintWriter): Boolean {
        controller
            .get()
            .removeAllDesks(
                exitReason = ExitReason.ADB_COMMAND_EXIT,
                shouldEndUpAtHome = true,
                skipWallpaperAndHomeOrdering = false,
            )
        return false
    }

    private fun runMoveTaskToFront(args: Array<String>, pw: PrintWriter): Boolean {
        if (args.size < 2) {
            // First argument is the action name.
            pw.println("Error: task id should be provided as arguments")
            return false
        }
        val taskId =
            try {
                args[1].toInt()
            } catch (e: NumberFormatException) {
                pw.println("Error: task id should be an integer")
                return false
            }
        controller
            .get()
            .moveTaskToFront(
                taskId = taskId,
                remoteTransition = null,
                unminimizeReason = UnminimizeReason.UNKNOWN,
            )
        return true
    }

    private fun runMoveTaskOutOfDesk(args: Array<String>, pw: PrintWriter): Boolean {
        if (args.size < 2) {
            // First argument is the action name.
            pw.println("Error: task id should be provided as arguments")
            return false
        }
        val taskId =
            try {
                args[1].toInt()
            } catch (e: NumberFormatException) {
                pw.println("Error: task id should be an integer")
                return false
            }
        controller.get().moveToFullscreen(taskId, transitionSource = ADB_COMMAND)
        return true
    }

    private fun runCanCreateDesk(args: Array<String>, pw: PrintWriter): Boolean {
        if (args.size < 2) {
            // First argument is the displayId.
            pw.println("Error: displayId should be provided as an argument")
            return false
        }
        val displayId =
            try {
                args[1].toInt()
            } catch (e: NumberFormatException) {
                pw.println("Error: display id should be an integer")
                return false
            }
        pw.println("Not implemented.")
        return false
    }

    private fun runGetActiveDeskId(args: Array<String>, pw: PrintWriter): Boolean {
        if (args.size < 2) {
            // First argument is the action name.
            pw.println("Error: task id should be provided as arguments")
            return false
        }
        val displayId =
            try {
                args[1].toInt()
            } catch (e: NumberFormatException) {
                pw.println("Error: display id should be an integer")
                return false
            }
        pw.println("Not implemented.")
        return false
    }

    private fun runClearRememberedBounds(args: Array<String>, pw: PrintWriter): Boolean {
        if (!Flags.enableRememberedBounds()) {
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
        val repository = userRepositories.getProfile(shellController.currentUserId)
        repository.clearRememberedBoundsRatio(packageName)
        return true
    }

    private fun runClearAllRememberedBounds(args: Array<String>, pw: PrintWriter): Boolean {
        if (!Flags.enableRememberedBounds()) {
            pw.println("Not supported.")
            return false
        }
        userRepositories.getProfile(shellController.currentUserId).clearAllRememberedBoundsRatio()
        return true
    }

    private fun runPeekHomeScreen(args: Array<String>, pw: PrintWriter): Boolean {
        if (!Flags.enableHomeScreenPeeking()) {
            pw.println("Not supported.")
            return false
        }
        if (desktopHomeScreenPeekController.isPeeking) {
            desktopHomeScreenPeekController.unpeek()
        } else {
            desktopHomeScreenPeekController.peek()
        }
        return true
    }

    private fun printInvalidCommandAndShowHelp(args: Array<String>, pw: PrintWriter): Boolean {
        if (args.isNotEmpty()) {
            pw.println("Invalid command: ${args[0]}")
        } else {
            pw.println("No command provided.")
        }
        printShellCommandHelp(pw, "    ")
        return false
    }

    override fun printShellCommandHelp(pw: PrintWriter, prefix: String) {
        pw.println("$prefix moveTaskToDesk <taskId|0> <deskId>")
        pw.println(
            "$prefix  Move a task with given id to the given desk and activate it. " +
                "TaskId 0 means focused task on the default display."
        )
        pw.println("$prefix moveToNextDisplay <taskId>")
        pw.println("$prefix  Move a task with given id to next display.")
        pw.println("$prefix createDesk <displayId>")
        pw.println("$prefix  Creates a desk on the given display.")
        pw.println("$prefix activateDesk <deskId>")
        pw.println("$prefix  Activates the given desk.")
        pw.println("$prefix removeDesk <deskId> ")
        pw.println("$prefix  Removes the given desk and all of its windows.")
        pw.println("$prefix removeAllDesks")
        pw.println("$prefix  Removes all the desks and their windows across all displays")
        pw.println("$prefix moveTaskToFront <taskId>")
        pw.println("$prefix  Moves a task in front of its siblings.")
        pw.println("$prefix moveTaskOutOfDesk <taskId>")
        pw.println("$prefix  Moves the given desktop task out of the desk into fullscreen mode.")
        pw.println("$prefix canCreateDesk <displayId>")
        pw.println("$prefix  Whether creating a new desk in the given display is allowed.")
        pw.println("$prefix getActivateDeskId <displayId>")
        pw.println("$prefix  Print the id of the active desk in the given display.")
        pw.println("$prefix clearRememberedBounds <packageName>")
        pw.println("$prefix  Clears the remembered bounds for the given package.")
        pw.println("$prefix clearAllRememberedBounds")
        pw.println("$prefix  Clears the remembered bounds for all packages.")
        pw.println("$prefix peek")
        pw.println("$prefix  Peeks the home screen by moving desktop tasks to sides of the screen.")
    }
}
