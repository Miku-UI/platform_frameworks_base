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

package com.android.systemui.statusbar.quickactions.assistant.data.repository

import android.content.ComponentName
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeAssistantRepository : AssistantRepository {
    override fun startAssistant(context: Context) {}

    private val _assistInfoFlow = MutableStateFlow<ComponentName?>(null)
    override val assistInfo: StateFlow<ComponentName?> = _assistInfoFlow.asStateFlow()

    fun setAssistInfo(componentName: ComponentName?) {
        _assistInfoFlow.value = componentName
    }
}
