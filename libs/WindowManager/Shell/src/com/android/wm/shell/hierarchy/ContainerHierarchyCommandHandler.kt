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
package com.android.wm.shell.hierarchy

import com.android.wm.shell.Flags
import com.android.wm.shell.hierarchy.modes.FormFactorModes
import com.android.wm.shell.hierarchy.utils.HierarchyDebugUtils
import com.android.wm.shell.sysui.ShellCommandHandler
import com.android.wm.shell.sysui.ShellInit
import java.io.PrintWriter

/**
 * Implements shell commands related to the container hierarchy.
 * Usage: adb shell wm shell hierarchy <command>
 */
class ContainerHierarchyCommandHandler(
    private val hierarchy: ContainerHierarchy,
    private val formFactorModes: FormFactorModes,
    shellInit: ShellInit,
    private val shellCommandHandler: ShellCommandHandler,
) : ShellCommandHandler.ShellCommandActionHandler {

    init {
        if (Flags.enableShellModes()) {
            shellInit.addInitCallback(this::onInit, this)
        }
    }

    private fun onInit() {
        shellCommandHandler.addCommandCallback("hierarchy", this, this)
    }

    override fun printShellCommandHelp(rawPw: PrintWriter?, prefix: String?) {
        val pw = rawPw!!
        pw.println("${prefix}modes")
        pw.println("$prefix   Lists all available modes for the focused display")
        pw.println("${prefix}mode <mode id> <action> <args>...")
        pw.println("$prefix   Calls the given mode on the focused display with the given arguments")
        pw.println("${prefix}dump")
        pw.println("$prefix   Dumps the container hierarchy")
    }

    override fun onShellCommand(
        rawArgs: Array<out String?>?,
        pw: PrintWriter?
    ): Boolean {
        if (rawArgs!!.isEmpty()) {
            return false
        }
        val args = rawArgs.filterNotNull()
        when (args[0]) {
            "modes" -> return listModes(pw!!)
            "mode" -> return callMode(args.drop(1).toMutableList(), pw!!)
            "dump" -> return dumpContainerHierarchy(pw!!)
        }
        return false
    }

    private fun listModes(pw: PrintWriter): Boolean {
        val rootProps = hierarchy.root.rootProps()
        val displayId = rootProps.focusState.globallyFocusedDisplayId
        val modes = formFactorModes.getAvailableModesForDisplay(displayId)
        pw.println("Modes:")
        for (mode in modes) {
            pw.println("  ${mode.getId()}")
        }
        return true
    }

    private fun callMode(args: MutableList<String>, pw: PrintWriter): Boolean {
        if (args.size < 2) {
            pw.println("Not enough args, need <mode id> and <action>")
            return false
        }
        val modeId = args.removeFirst()
        val rootProps = hierarchy.root.rootProps()
        val displayId = rootProps.focusState.globallyFocusedDisplayId
        val modes = formFactorModes.getAvailableModesForDisplay(displayId)
        val mode = modes.firstOrNull { mode -> mode.getId() == modeId }
        if (mode == null) {
            pw.println("No available mode on disp=$displayId with id=$modeId")
            return false
        }
        mode.onShellCommand(displayId, args, pw)
        return true
    }

    private fun dumpContainerHierarchy(pw: PrintWriter): Boolean {
        pw.println(HierarchyDebugUtils.getHierarchyDump(hierarchy))
        return true
    }
}