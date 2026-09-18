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

package com.android.wm.shell.desktopai.api.config

import com.android.wm.shell.desktopai.api.TriggerEvent
import com.android.wm.shell.desktopai.api.TriggerEventType

/**
 * Defines a rule or condition that must be met for a [TriggerEvent] to activate a registered
 * callback.
 */
sealed class TriggerStrategy {

    /**
     * Determines if a specific runtime [event] satisfies the conditions of this strategy.
     *
     * @param event The event that occurred in the system.
     * @return `true` if the event matches this strategy's criteria; `false` otherwise.
     */
    abstract fun matches(event: TriggerEvent): Boolean

    /**
     * A strategy that matches system events with optional property matching filters.
     *
     * @property eventId The specific system event ID to listen for (e.g., "OVERVIEW_SHOWN").
     * @property filters A map of key-value pairs. For a match to occur, the [TriggerEvent.payload]
     *   must contain every key in this map, and the values must match exactly.
     */
    data class SystemEvent(val eventId: String, val filters: Map<String, Any> = emptyMap()) :
        TriggerStrategy() {
        override fun matches(event: TriggerEvent): Boolean {
            if (event.type != TriggerEventType.SYSTEM) return false
            if (event.id != eventId) return false

            // Filter Logic: The event payload must contain all key-values defined in filters
            return filters.all { (k, v) -> event.payload[k] == v }
        }
    }
}
