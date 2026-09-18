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

package com.android.systemui.dreams.data.repository

import android.content.ComponentName
import android.os.UserHandle
import com.android.systemui.dreams.shared.model.DreamPlaylistModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** Repository for retrieving and modifying the state of the dream system. */
interface DreamRepository {
    /**
     * A flow of the current dream configuration, including the active dream and available options.
     */
    val dreamState: Flow<DreamPlaylistModel>

    /** Whether the dream switcher dialog is currently showing. */
    val dreamSwitcherDialogShowing: StateFlow<Boolean>

    /** Whether the dream switcher feature is supported and enabled. */
    val isDreamSwitcherEnabled: Boolean

    /**
     * Sets the active dream to the component identified by [componentName].
     *
     * If the device is currently dreaming, this will cause the device to swap to the new dream. If
     * the device is not currently dreaming, this will save the dream as active for the next time
     * the device enters the dreaming state.
     *
     * @param componentName The unique component name of the dream service.
     * @param user The user to set the active dream for.
     * @return true if the dream was successfully set, false otherwise.
     */
    suspend fun setActiveDream(componentName: ComponentName, user: UserHandle): Boolean

    /** Updates whether the dream dialog is showing. */
    fun setSwitcherDialogShowing(showing: Boolean)
}
