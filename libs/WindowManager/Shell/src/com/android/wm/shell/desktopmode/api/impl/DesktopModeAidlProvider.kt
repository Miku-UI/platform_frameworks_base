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

package com.android.wm.shell.desktopmode.api.impl

import com.android.wm.shell.dagger.WMSingleton
import com.android.wm.shell.desktopmode.DesktopTasksController
import com.android.wm.shell.desktopmode.api.IDesktopMode
import com.android.wm.shell.sysui.ShellController
import com.android.wm.shell.sysui.ShellInit
import java.util.Optional
import javax.inject.Inject
import kotlin.jvm.optionals.getOrNull

/** Registers an implementation for the IDesktopMode interface. */
@WMSingleton
class DesktopModeAidlProvider
@Inject constructor(
    private val shellInit: ShellInit,
    private val shellController: ShellController,
    private val desktopTasksController: Optional<DesktopTasksController>,
) {

    init {
        if (desktopTasksController.isPresent()) {
            shellInit.addInitCallback({ onInit() }, this)
        }
    }

    private fun onInit() {
        shellController.addExternalInterface(
            IDesktopMode.DESCRIPTOR,
            { desktopTasksController.getOrNull()?.createExternalInterface() },
            this,
        )
    }
}
