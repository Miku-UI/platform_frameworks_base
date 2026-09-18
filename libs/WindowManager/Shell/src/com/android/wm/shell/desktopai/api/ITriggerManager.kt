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

package com.android.wm.shell.desktopai.api

import com.android.wm.shell.desktopai.api.config.TriggerStrategy

/**
 * Defines the contract for a component that manages system triggers and notifies registered
 * listeners.
 */
interface ITriggerManager {
    /**
     * Registers a callback to be invoked when a system event matches the provided strategy.
     *
     * @param strategy The [TriggerStrategy] defining the rules for activation (e.g., specific keys,
     *   system events).
     * @param callback The function to execute when the trigger fires. It receives the full
     *   [TriggerEvent] containing context and payload data.
     */
    fun registerTrigger(strategy: TriggerStrategy, callback: (TriggerEvent) -> Unit)
}
