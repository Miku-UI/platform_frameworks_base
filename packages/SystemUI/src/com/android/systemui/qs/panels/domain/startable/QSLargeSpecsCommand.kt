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

package com.android.systemui.qs.panels.domain.startable

import com.android.systemui.CoreStartable
import com.android.systemui.qs.panels.domain.interactor.IconTilesInteractor
import com.android.systemui.qs.pipeline.shared.TileSpec
import com.android.systemui.statusbar.commandline.Command
import com.android.systemui.statusbar.commandline.CommandRegistry
import java.io.PrintWriter
import javax.inject.Inject

class QSLargeSpecsCommand
@Inject
constructor(
    private val commandRegistry: CommandRegistry,
    private val iconTilesInteractor: IconTilesInteractor,
) : CoreStartable {
    override fun start() {
        commandRegistry.registerCommand(SetLargeTilesCommand.COMMAND) {
            SetLargeTilesCommand(iconTilesInteractor)
        }
        commandRegistry.registerCommand(RestoreLargeTilesCommand.COMMAND) {
            RestoreLargeTilesCommand(iconTilesInteractor)
        }
    }
}

class SetLargeTilesCommand(private val iconTilesInteractor: IconTilesInteractor) : Command {
    override fun execute(pw: PrintWriter, args: List<String>) {
        val specs =
            if (args.isEmpty()) {
                emptySet()
            } else {
                args[0].split(",").map(TileSpec::create).toSet()
            }

        pw.println("Setting large specs: $specs")
        iconTilesInteractor.setLargeTiles(specs)
    }

    override fun help(pw: PrintWriter) {
        pw.println("Usage: $COMMAND [comma separated list of tiles]")
        pw.println("    Sets the list of large tiles to use in Quick Settings tiles")
    }

    companion object {
        const val COMMAND: String = "set-large-tiles"
    }
}

class RestoreLargeTilesCommand(private val iconTilesInteractor: IconTilesInteractor) : Command {
    override fun execute(pw: PrintWriter, args: List<String>) {
        pw.println("Setting large specs to default")
        iconTilesInteractor.resetToDefault()
    }

    override fun help(pw: PrintWriter) {
        pw.println("Usage: $COMMAND")
        pw.println("    Restore the default list of large tiles to use in Quick Settings tiles")
    }

    companion object {
        const val COMMAND: String = "restore-large-tiles"
    }
}
