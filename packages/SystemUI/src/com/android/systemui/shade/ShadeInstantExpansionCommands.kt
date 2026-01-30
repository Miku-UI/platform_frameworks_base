/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.systemui.shade

import com.android.systemui.CoreStartable
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.scene.shared.flag.SceneContainerFlag
import com.android.systemui.scene.shared.model.TransitionKeys
import com.android.systemui.shade.domain.interactor.ShadeInteractor
import com.android.systemui.statusbar.commandline.Command
import com.android.systemui.statusbar.commandline.CommandRegistry
import java.io.PrintWriter
import javax.inject.Inject
import javax.inject.Provider

@SysUISingleton
class ShadeInstantExpansionCommands
@Inject
constructor(
    private val commandRegistry: CommandRegistry,
    private val instantExpandNotificationsCommand: Provider<InstantExpandNotificationsCommand>,
    private val instantExpandQsCommand: Provider<InstantExpandQsCommand>,
    private val instantCollapseShadeCommand: Provider<InstantCollapseShadeCommand>,
) : CoreStartable {

    override fun start() {
        if (SceneContainerFlag.isEnabled) {
            commandRegistry.registerCommand("expand-notifications-instant") {
                instantExpandNotificationsCommand.get()
            }
            commandRegistry.registerCommand("expand-settings-instant") {
                instantExpandQsCommand.get()
            }
            commandRegistry.registerCommand("collapse-instant") {
                instantCollapseShadeCommand.get()
            }
        }
    }
}

class InstantExpandNotificationsCommand
@Inject
constructor(private val shadeInteractor: ShadeInteractor) : Command {
    override fun execute(pw: PrintWriter, args: List<String>) {
        shadeInteractor.expandNotificationsShade("adb command", TransitionKeys.Instant)
        pw.println("Showing Notifications shade")
    }

    override fun help(pw: PrintWriter) {
        pw.println("expand-notifications-instant")
        pw.println("expands the Notifications shade without animating")
        pw.println()
    }
}

class InstantExpandQsCommand
@Inject
constructor(private val shadeInteractor: ShadeInteractor) : Command {
    override fun execute(pw: PrintWriter, args: List<String>) {
        shadeInteractor.expandQuickSettingsShade("adb command", TransitionKeys.Instant)
        pw.println("Showing Quick Settings shade")
    }

    override fun help(pw: PrintWriter) {
        pw.println("expand-settings-instant")
        pw.println("expands the Quick Settings shade without animating")
        pw.println()
    }
}

class InstantCollapseShadeCommand
@Inject
constructor(private val shadeInteractor: ShadeInteractor) : Command {
    override fun execute(pw: PrintWriter, args: List<String>) {
        shadeInteractor.collapseEitherShade("adb command", TransitionKeys.Instant)
        pw.println("hiding any expanded shade")
    }

    override fun help(pw: PrintWriter) {
        pw.println("collapse-instant")
        pw.println("collapses any expanded shade without animating")
        pw.println()
    }
}
