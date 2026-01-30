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

package com.android.systemui.supervision.data.repository

import android.graphics.drawable.Drawable
import com.android.systemui.supervision.data.model.SupervisionModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeSupervisionRepository : SupervisionRepository {
    private var currentModel =
        SupervisionModel(
            isSupervisionEnabled = false,
            label = null,
            icon = null,
            footerText = null,
            disclaimerText = null,
        )
    private val supervisionStateFlow: MutableStateFlow<SupervisionModel> =
        MutableStateFlow(currentModel)

    override val supervision: Flow<SupervisionModel> = supervisionStateFlow

    fun updateState(newModel: SupervisionModel) {
        currentModel = newModel
        supervisionStateFlow.value = currentModel
    }

    // Helper methods for Java
    fun getSupervisionModel(): SupervisionModel = currentModel

    fun setIsSupervisionEnabled(value: Boolean) {
        updateState(currentModel.copy(isSupervisionEnabled = value))
    }

    fun setIcon(value: Drawable?) {
        updateState(currentModel.copy(icon = value))
    }

    fun setLabel(value: CharSequence?) {
        updateState(currentModel.copy(label = value))
    }

    fun setFooterText(value: CharSequence?) {
        updateState(currentModel.copy(footerText = value))
    }

    fun setDisclaimerText(value: CharSequence?) {
        updateState(currentModel.copy(disclaimerText = value))
    }
}
