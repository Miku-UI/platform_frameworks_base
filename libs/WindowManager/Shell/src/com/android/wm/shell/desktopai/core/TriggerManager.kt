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

package com.android.wm.shell.desktopai.core

import com.android.internal.protolog.ProtoLog
import com.android.window.flags.Flags
import com.android.wm.shell.desktopai.api.ITriggerManager
import com.android.wm.shell.desktopai.api.ITriggerSource
import com.android.wm.shell.desktopai.api.TriggerEvent
import com.android.wm.shell.desktopai.api.TriggerEventType
import com.android.wm.shell.desktopai.api.config.TriggerStrategy
import com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_DESKTOP_AI
import com.android.wm.shell.sysui.ShellInit

/**
 * The default implementation of [ITriggerManager].
 *
 * This class coordinates multiple [ITriggerSource]s and dispatches events to registered callbacks
 * based on matching [TriggerStrategy] rules.
 *
 * @param sources A list of [ITriggerSource] implementations that this manager will subscribe to
 *   upon initialization.
 */
class TriggerManager(shellInit: ShellInit, private val sources: List<ITriggerSource>) :
    ITriggerManager {

    /**
     * A optimized registry mapping event types to a list of registrations. It allows quick lookup
     * of the event type, followed by linear scan of only relevant strategies.
     */
    private val registry = mutableMapOf<TriggerEventType, MutableList<Registration>>()

    /** Internal container linking a strategy to its callback. */
    private data class Registration(
        val strategy: TriggerStrategy,
        val callback: (TriggerEvent) -> Unit,
    )

    init {
        shellInit.addInitCallback(
            {
                if (Flags.desktopAiPlatform()) {
                    sources.forEach { source -> source.start { event -> fireEvent(event) } }
                }
            },
            this,
        )
    }

    /**
     * Registers a callback to be invoked when a system event matches the provided strategy.
     *
     * This implementation groups strategies by their [TriggerEventType] in an internal registry to
     * optimize the search process when events are fired.
     *
     * @param strategy The [TriggerStrategy] defining the rules for activation (e.g., specific keys,
     *   system events).
     * @param callback The function to execute when the trigger fires. It receives the full
     *   [TriggerEvent] containing context and payload data.
     */
    override fun registerTrigger(strategy: TriggerStrategy, callback: (TriggerEvent) -> Unit) {
        val type = getEventTypeForStrategy(strategy)
        val list = registry.getOrPut(type) { mutableListOf() }
        list.add(Registration(strategy, callback))

        ProtoLog.v(WM_SHELL_DESKTOP_AI, "$TAG: Registered strategy: %s", strategy)
    }

    private fun fireEvent(event: TriggerEvent) {
        ProtoLog.v(WM_SHELL_DESKTOP_AI, "$TAG: Processing event: %s", event)

        // 1. Efficient Lookup: Only look at strategies relevant to this Event Type
        val candidates = registry[event.type] ?: return

        // 2. Filter Matching: Let the strategy decide if the event payload matches its requirements
        candidates.forEach { registration ->
            if (registration.strategy.matches(event)) {
                registration.callback(event)
            }
        }
    }

    private fun getEventTypeForStrategy(s: TriggerStrategy): TriggerEventType =
        when (s) {
            is TriggerStrategy.SystemEvent -> TriggerEventType.SYSTEM
        // Add other TriggerStrategy in here.
        }

    companion object {
        private const val TAG = "TriggerManager"
    }
}
