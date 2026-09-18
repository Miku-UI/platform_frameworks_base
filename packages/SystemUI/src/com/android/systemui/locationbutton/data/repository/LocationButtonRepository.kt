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
package com.android.systemui.locationbutton.data.repository

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateMap
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.locationbutton.shared.model.ButtonModel
import javax.inject.Inject

@SysUISingleton
class LocationButtonRepository @Inject constructor() {
    private val buttonStatesMap: SnapshotStateMap<Int, ButtonModel> = mutableStateMapOf()

    fun getButtonState(sessionId: Int): ButtonModel? = buttonStatesMap[sessionId]

    fun setButtonState(sessionId: Int, buttonModel: ButtonModel) {
        buttonStatesMap[sessionId] = buttonModel
    }

    fun removeButtonState(sessionId: Int) {
        buttonStatesMap.remove(sessionId)
    }

    fun updateButtonState(sessionId: Int, update: (ButtonModel) -> ButtonModel) {
        val currentModel = buttonStatesMap[sessionId]
        checkNotNull(currentModel) {
            "Failed to update ButtonModel: No state found for session ID $sessionId"
        }
        val newModel = update(currentModel)
        buttonStatesMap[sessionId] = newModel
    }

    fun clear() = buttonStatesMap.clear()
}
