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

import android.window.DisplayAreaInfo
import android.window.WindowContainerTransaction
import com.android.wm.shell.Flags
import com.android.wm.shell.common.DisplayChangeController
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.hierarchy.utils.HierarchyDebugUtils
import com.android.wm.shell.sysui.ShellCommandHandler
import com.android.wm.shell.sysui.ShellInit
import java.io.PrintWriter

/**
 * The bridge between the container hierarchy and the rest of the Shell (ie. TaskOrganizer,
 * Transitions, initialization, shell commands, etc).
 */
class ContainerHierarchyController(
    shellInit: ShellInit,
    private val shellCommandHandler: ShellCommandHandler,
    private val displayController: DisplayController,
    private val hierarchy: ContainerHierarchy,
) : DisplayChangeController.OnDisplayChangingListener {

    init {
        if (Flags.enableShellModes()) {
            shellInit.addInitCallback(this::onInit, this)
        }
    }

    private fun onInit() {
        shellCommandHandler.addDumpCallback(this::dump, this)
        displayController.addDisplayChangingController(this)
    }

    /** @see DisplayChangeController.OnDisplayChangingListener.onDisplayChange */
    override fun onDisplayChange(
        displayId: Int,
        fromRotation: Int,
        toRotation: Int,
        newDisplayAreaInfo: DisplayAreaInfo?,
        outWct: WindowContainerTransaction?
    ) {
        hierarchy.update.updateDisplay(displayId, outWct!!)
    }

    private fun dump(pw: PrintWriter, prefix: String) {
        pw.println("${prefix}ContainerHierarchy")
        pw.println(HierarchyDebugUtils.getHierarchyDump(hierarchy))
    }
}