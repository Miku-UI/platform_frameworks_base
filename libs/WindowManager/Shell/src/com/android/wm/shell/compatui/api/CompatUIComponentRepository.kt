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

package com.android.wm.shell.compatui.api

import com.android.internal.protolog.ProtoLog
import com.android.wm.shell.dagger.WMSingleton
import com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_APP_COMPAT
import com.android.wm.shell.repository.GenericRepository
import com.android.wm.shell.repository.MemoryRepositoryImpl
import javax.inject.Inject

/** Aggregates active [CompatUIComponent] and related optional [CompatUIComponentState]. */
data class UIComponentItem(
    val component: CompatUIComponent,
    val componentState: CompatUIComponentState? = null,
)

/** Repository of the current [CompatUIComponent] and related [CompatUIComponentState] */
@WMSingleton
class CompatUIComponentRepository @Inject constructor() :
    GenericRepository<String, UIComponentItem> by MemoryRepositoryImpl(
        logger = { msg -> ProtoLog.v(WM_SHELL_APP_COMPAT, "CompatUIComponentRepository: %s", msg) }
    ) {

    /**
     * Registers a component for a given componentId along with its optional state.
     *
     * <p/>
     *
     * @param componentId The identifier for the component to register.
     * @param comp The {@link CompatUIComponent} instance to register.
     * @param componentState The optional state specific of the component. Not all components have a
     *   specific state so it can be null.
     */
    fun registerUIComponent(
        componentId: String,
        comp: CompatUIComponent,
        componentState: CompatUIComponentState?,
    ) {
        insert(componentId, UIComponentItem(comp, componentState))
    }

    /**
     * Unregister a component for a given componentId.
     *
     * <p/>
     *
     * @param componentId The identifier for the component to register.
     */
    fun unregisterUIComponent(componentId: String) {
        delete(componentId)
    }

    /**
     * Get access to the specific {@link CompatUIComponentState} for a {@link CompatUIComponent}
     * with a given identifier.
     *
     * <p/>
     *
     * @param componentId The identifier of the {@link CompatUIComponent}.
     * @return The optional state for the component of the provided id.
     */
    inline fun <reified T : CompatUIComponentState> stateForComponent(componentId: String) =
        find(componentId)?.componentState as? T
}
