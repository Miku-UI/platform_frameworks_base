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

package com.android.wm.shell.desktopai.extensions.ace

import android.service.personalcontext.hint.ContextHint
import com.android.wm.shell.desktopai.api.TriggerEvent

/** Interface for a component that can map a [TriggerEvent] to a specific [ContextHint]. */
interface TriggerEventHintMapper {
    /**
     * Maps a TriggerEvent to a ContextHint.
     *
     * @param event The TriggerEvent to map.
     * @return The corresponding ContextHint, or null if the event cannot be mapped or the payload
     *   is invalid.
     */
    fun map(event: TriggerEvent): ContextHint?
}
