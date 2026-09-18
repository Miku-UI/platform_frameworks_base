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
import com.android.wm.shell.desktopai.api.ITriggerManager
import com.android.wm.shell.desktopai.api.TriggerEvent
import com.android.wm.shell.desktopai.api.config.CujConfiguration
import com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_DESKTOP_AI

/**
 * The central brain of the DesktopAI Layer. It manages the lifecycle of a request from Trigger to
 * Action execution.
 *
 * Key Responsibility: Act as a generic "Pipe" that processes specific "CujConfigurations". It does
 * not contain hardcoded logic for specific features (like "Coding Mode" vs "Research Mode").
 * Instead, it follows the instructions in the [CujConfiguration] to route data.
 */
class DesktopAiOrchestrator(
    private val triggerManager: ITriggerManager,
    private val cujHandlerRegistry: CujHandlerRegistry,
) {

    /**
     * Registers a Critical User Journey with the system. This uses the [TriggerStrategy] to set up
     * the appropriate listeners (Hotkeys, Events) in the Trigger Layer.
     */
    fun registerCuj(config: CujConfiguration) {
        triggerManager.registerTrigger(config.triggerStrategy) { triggerEvent ->
            onTriggerReceived(config, triggerEvent)
        }
    }

    /**
     * The main entry point for the DesktopAI flow. This method is called by the Trigger Layer (via
     * the callback registered above) when a signal is received.
     *
     * @param config The [CujConfiguration] defining the rules for this specific User Journey.
     * @param triggerEvent Optional payload from the trigger (e.g., voice command text, dropped
     *   file).
     */
    private fun onTriggerReceived(config: CujConfiguration, triggerEvent: TriggerEvent) {
        ProtoLog.v(WM_SHELL_DESKTOP_AI, "$TAG: Processing triggerEvent: %s", triggerEvent)
        // Iterate through all registered handlers for this CUJ and execute them.
        config.handlerIds.forEach { handlerId ->
            val handler = cujHandlerRegistry.get(handlerId)
            if (handler != null) {
                handler.handle(config, triggerEvent)
            } else {
                ProtoLog.w(WM_SHELL_DESKTOP_AI, "No CujHandler registered for ID: %s", handlerId.id)
            }
        }
    }

    companion object {
        private const val TAG = "DesktopAIOrchestrator"
    }
}
