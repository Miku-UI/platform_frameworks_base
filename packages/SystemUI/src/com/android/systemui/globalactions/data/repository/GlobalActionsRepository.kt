/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.systemui.globalactions.data.repository

import com.android.systemui.globalactions.shared.model.GlobalActionType
import kotlinx.coroutines.flow.StateFlow

/** Encapsulates application state for global actions. */
interface GlobalActionsRepository {
    /** Is the global actions dialog visible. */
    val isVisible: StateFlow<Boolean>

    /** Sets whether the global actions dialog is visible. */
    fun setVisible(isVisible: Boolean)

    /**
     * The list of all possible global actions. This list is used to determine what actions can be
     * displayed but it does not guarantee that they will be displayed. The actions that are finally
     * displayed are determined by device state.
     */
    val possibleGlobalActions: List<GlobalActionType>

    /** The list of global actions that should be blocked when the device is not yet provisioned. */
    val unprovisionedDeviceStateBlockList: List<GlobalActionType>

    /** The list of global actions that should be blocked when the device is locked. */
    val lockedDeviceStateBlockList: List<GlobalActionType>
}
