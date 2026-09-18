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
package com.android.wm.shell.hierarchy.modes.handheld

import com.android.wm.shell.Flags
import com.android.wm.shell.hierarchy.ContainerHierarchy
import com.android.wm.shell.hierarchy.experimental.AlwaysOnTopMode
import com.android.wm.shell.hierarchy.experimental.HandheldRootMode
import com.android.wm.shell.hierarchy.experimental.MultiContainerMode
import com.android.wm.shell.hierarchy.experimental.testsplit.PipMode
import com.android.wm.shell.hierarchy.experimental.testsplit.SplitMode
import com.android.wm.shell.hierarchy.modes.FormFactorModes
import com.android.wm.shell.hierarchy.modes.Mode
import com.android.wm.shell.sysui.ShellInit

/** Manages modes that are handheld-specific. */
class HandheldModes(
    private val hierarchy: ContainerHierarchy,
    private val rootMode: HandheldRootMode,
    private val alwaysOnTopMode: AlwaysOnTopMode,
    private val multiContainerMode: MultiContainerMode,
    private val splitMode: SplitMode,
    private val pipMode: PipMode,
    shellInit: ShellInit,
) : FormFactorModes {

    init {
        if (Flags.enableShellModes()) {
            shellInit.addInitCallback(this::onInit, this)
        }
    }

    private fun onInit() {
        // Bind the root mode
        hierarchy.root.mode = rootMode
    }

    /** @see FormFactorModes.getAvailableModesForDisplay */
    override fun getAvailableModesForDisplay(displayId: Int): List<Mode> {
        return listOf(rootMode, alwaysOnTopMode, multiContainerMode, splitMode, pipMode)
    }
}
