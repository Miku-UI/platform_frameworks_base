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

/**
 * Represents a discrete event occurring within the system that might trigger an action.
 *
 * @property type The broad category of the event (e.g., [TriggerEventType.HOTKEY],
 *   [TriggerEventType.SYSTEM]).
 * @property id A unique identifier within the [type]. For system events, this might be
 *   "OVERVIEW_SHOWN"; for hotkeys, "F10".
 * @property payload A map of contextual data associated with the event (e.g., `{"displayId": 1}`).
 *   Used for filtering strategies.
 */
data class TriggerEvent(
    val type: TriggerEventType,
    val id: String,
    val payload: Map<String, Any> = emptyMap(),
)

/** Categorizes the origin or nature of a [TriggerEvent]. */
enum class TriggerEventType {
    /** Events originating from physical keyboard interactions. */
    HOTKEY,
    /**
     * Events originating from internal system state changes (e.g., window management, power state).
     */
    SYSTEM,
}
