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

@file:OptIn(InternalComposeTracingApi::class)

package com.android.systemui.compose

import android.util.Log
import androidx.compose.runtime.InternalComposeTracingApi
import com.android.systemui.CoreStartable
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.flags.SystemPropertiesHelper
import com.android.systemui.statusbar.commandline.Command
import com.android.systemui.statusbar.commandline.CommandRegistry
import com.android.systemui.statusbar.commandline.ParseableCommand
import java.io.PrintWriter
import javax.inject.Inject

private const val TAG = "ComposeTracingStartable"
private const val COMMAND_NAME = "composition-tracing"
private const val SUBCOMMAND_ENABLE = "enable"
private const val SUBCOMMAND_DISABLE = "disable"
private const val VERBOSE_FLAG = "verbose"

/**
 * When sysui boots it checks this sysprop. If enabled, it also enables compose tracing (verbose).
 *
 * ```
 * adb shell setprop persist.debug.enable_verbose_compose_tracing_in_sysui_on_startup true
 * ```
 */
private const val ENABLE_COMPOSE_TRACING_ON_STARTUP =
    "persist.debug.enable_verbose_compose_tracing_in_sysui_on_startup"

/**
 * Sets up a [Command] to enable or disable Composition tracing.
 *
 * Usage:
 * ```
 * # Enables just recomposition slices
 * adb shell cmd statusbar composition-tracing [enable|disable] [--verbose]
 *
 * ${ANDROID_BUILD_TOP}/external/perfetto/tools/record_android_trace -c ${ANDROID_BUILD_TOP}/prebuilts/tools/linux-x86_64/perfetto/configs/trace_config_detailed.textproto
 * ```
 *
 * Note: you need to add the following bits to your perfetto config to get detailed recomposition
 * tracing (enabled with `--verbose`):
 * ```
 * data_sources {
 *   config {
 *     name: "track_event"
 *     track_event_config {
 *       disabled_categories:"*"
 *       enabled_categories: "cc"
 *     }
 *    }
 *     producer_name_regex_filter: "com.android.systemui"
 * }
 * ```
 */
@SysUISingleton
class ComposeTracingStartable
@Inject
constructor(
    private val commandRegistry: CommandRegistry,
    private val recompositionCauseTracing: RecompositionCauseTracing,
    private val systemPropertiesHelper: SystemPropertiesHelper,
) : CoreStartable {

    override fun start() {
        Log.i(TAG, "Set up Compose tracing command")
        commandRegistry.registerCommand(COMMAND_NAME) {
            CompositionTracingCommand(recompositionCauseTracing)
        }

        if (isInitiallyEnabled()) {
            CompositionSlicesTracing.enable()
            recompositionCauseTracing.enable()
        }
    }

    private fun isInitiallyEnabled(): Boolean {
        return systemPropertiesHelper.getBoolean(ENABLE_COMPOSE_TRACING_ON_STARTUP, false)
    }
}

private class CompositionTracingCommand(recompositionCauseTracing: RecompositionCauseTracing) :
    ParseableCommand(COMMAND_NAME) {
    val enable by subCommand(EnableCommand(recompositionCauseTracing))
    val disable by subCommand(DisableCommand(recompositionCauseTracing))

    override fun execute(pw: PrintWriter) {
        if ((enable != null) xor (disable != null)) {
            enable?.execute(pw)
            disable?.execute(pw)
        } else {
            help(pw)
        }
    }
}

private class EnableCommand(private val recompositionCauseTracing: RecompositionCauseTracing) :
    ParseableCommand(SUBCOMMAND_ENABLE) {

    private val verbose by flag(shortName = "v", longName = VERBOSE_FLAG)

    override fun execute(pw: PrintWriter) {
        val msg = "Enabling Composition tracing"
        Log.i(TAG, msg)
        pw.println(msg)
        CompositionSlicesTracing.enable()
        if (verbose) {
            val msg = "Enabling recomposition cause tracing"
            Log.i(TAG, msg)
            pw.println(msg)
            recompositionCauseTracing.enable()
        }
    }
}

private class DisableCommand(private val recompositionCauseTracing: RecompositionCauseTracing) :
    ParseableCommand(SUBCOMMAND_DISABLE) {
    override fun execute(pw: PrintWriter) {
        val msg = "Disabling Composition tracing"
        Log.i(TAG, msg)
        pw.println(msg)
        CompositionSlicesTracing.disable()
        recompositionCauseTracing.disable()
    }
}
