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

package com.android.systemui.globalactions.data.repository

import com.android.systemui.globalactions.shared.model.GlobalActionType
import kotlinx.coroutines.flow.MutableStateFlow

class FakeGlobalActionsRepository : GlobalActionsRepository {

    override val isVisible = MutableStateFlow(false)

    override fun setVisible(isVisible: Boolean) {
        this.isVisible.value = isVisible
    }

    /** The list of possible global actions. */
    override var possibleGlobalActions: List<GlobalActionType> = emptyList()

    /** Actions to block when the device is unprovisioned. */
    override var unprovisionedDeviceStateBlockList: List<GlobalActionType> = emptyList()

    /** Actions to block when the device is locked. */
    override var lockedDeviceStateBlockList: List<GlobalActionType> = emptyList()
}
