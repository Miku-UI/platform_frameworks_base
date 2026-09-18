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
import com.android.internal.protolog.ProtoLog
import com.android.wm.shell.dagger.WMSingleton
import com.android.wm.shell.desktopai.api.TriggerEvent
import com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_DESKTOP_AI
import com.android.wm.shell.repository.GenericRepository
import com.android.wm.shell.repository.MemoryRepositoryImpl
import javax.inject.Inject

/**
 * A central registry that holds and manages different implementations of [TriggerEventHintMapper].
 *
 * This class provides a flexible way to convert a [TriggerEvent] into a corresponding [ContextHint]
 * by delegating the conversion to a mapper that has been registered for the event's specific ID.
 */
@WMSingleton
class HintMapperRegistry @Inject constructor() :
    TriggerEventHintMapper,
    GenericRepository<String, TriggerEventHintMapper> by MemoryRepositoryImpl(
        logger = { msg -> ProtoLog.v(WM_SHELL_DESKTOP_AI, "HintMapperRegistry: %s", msg) }
    ) {
    /**
     * Registers a mapper for a specific event ID. If a mapper for the given ID already exists, it
     * will be overwritten.
     *
     * @param eventId The unique identifier of the event (e.g., "overview_shown").
     * @param mapper The mapper implementation that can handle this event.
     */
    fun register(eventId: String, mapper: TriggerEventHintMapper) = insert(eventId, mapper)

    /**
     * Maps a TriggerEvent to a ContextHint by finding the appropriate registered mapper.
     *
     * @param event The [TriggerEvent] to map.
     * @return The resulting [ContextHint] if a suitable mapper is found, otherwise null.
     */
    override fun map(event: TriggerEvent): ContextHint? {
        return find(event.id)?.map(event)
    }
}
