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
import com.android.wm.shell.dagger.WMSingleton
import com.android.wm.shell.desktopai.api.CujHandler
import com.android.wm.shell.desktopai.api.CujHandlerId
import com.android.wm.shell.desktopai.extensions.ace.ContextEngineCujHandler
import com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_DESKTOP_AI
import com.android.wm.shell.repository.GenericRepository
import com.android.wm.shell.repository.MemoryRepositoryImpl
import javax.inject.Inject

/**
 * A [GenericRepository] that maps a unique, type-safe ID to a concrete [CujHandler] implementation.
 * This allows for decoupling the static CUJ configuration from the runtime handler instances,
 * enabling flexible and extensible event handling.
 */
@WMSingleton
class CujHandlerRegistry
@Inject
constructor(
    shellCujHandler: ShellCujHandler,
    personalContextEngineCujHandler: ContextEngineCujHandler,
) :
    GenericRepository<CujHandlerId, CujHandler> by MemoryRepositoryImpl(
        logger = { msg -> ProtoLog.v(WM_SHELL_DESKTOP_AI, "$TAG: CujHandlerRegistry: %s", msg) }
    ) {

    init {
        register(CujHandlerId.ShellCujHandler, shellCujHandler)
        register(CujHandlerId.PersonalContextCujHandler, personalContextEngineCujHandler)
    }

    /**
     * Registers a handler with a unique ID. If a handler for the given ID already exists, it will
     * be overwritten.
     *
     * @param id The unique, type-safe identifier of the handler.
     * @param handler The concrete [CujHandler] implementation.
     */
    fun register(id: CujHandlerId, handler: CujHandler) {
        insert(key = id, handler)
    }

    /**
     * Retrieves a handler by its ID.
     *
     * @param id The unique, type-safe identifier of the handler to retrieve.
     * @return The [CujHandler] instance, or null if no handler is registered for the given ID.
     */
    fun get(id: CujHandlerId): CujHandler? = find(key = id)

    companion object {
        private const val TAG = "CujHandlerRegistry"
    }
}
