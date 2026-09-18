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

package com.android.systemui.ui.viewmodel

import androidx.compose.runtime.getValue
import com.android.systemui.lifecycle.HydratedActivatable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf

class FakeSysUiViewModel(
    private val onActivation: () -> Unit = {},
    private val onDeactivation: () -> Unit = {},
    upstreamFlow: Flow<Boolean> = flowOf(true),
    upstreamStateFlow: StateFlow<Boolean> = MutableStateFlow(true).asStateFlow(),
) : HydratedActivatable() {

    var activationCount = 0
    var cancellationCount = 0

    val stateBackedByFlow: Boolean by
        upstreamFlow.hydratedStateOf(traceName = "test", initialValue = true)
    val stateBackedByStateFlow: Boolean by upstreamStateFlow.hydratedStateOf(traceName = "test")

    override suspend fun onActivated() {
        activationCount++
        onActivation()
    }

    override suspend fun onDeactivated() {
        cancellationCount++
        onDeactivation()
    }
}
