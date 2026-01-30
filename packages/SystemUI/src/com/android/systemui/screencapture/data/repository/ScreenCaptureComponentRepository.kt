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

package com.android.systemui.screencapture.data.repository

import com.android.app.tracing.coroutines.flow.asStateFlowTraced
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.screencapture.common.ScreenCaptureComponent
import com.android.systemui.screencapture.common.shared.model.ScreenCaptureType
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@SysUISingleton
class ScreenCaptureComponentRepository @Inject constructor() {

    private val components: Map<ScreenCaptureType, ComponentContext> =
        ScreenCaptureType.entries.associateWith { ComponentContext() }

    fun screenCaptureComponent(type: ScreenCaptureType): StateFlow<ScreenCaptureComponent?> =
        components.getValue(type).state.asStateFlowTraced("ScreenCaptureComponent_$type")

    suspend fun update(
        type: ScreenCaptureType,
        update: (ScreenCaptureComponent?) -> ScreenCaptureComponent?,
    ) {
        with(components.getValue(type)) { mutex.withLock { state.value = update(state.value) } }
    }
}

private data class ComponentContext(
    val mutex: Mutex = Mutex(),
    val state: MutableStateFlow<ScreenCaptureComponent?> = MutableStateFlow(null),
)
