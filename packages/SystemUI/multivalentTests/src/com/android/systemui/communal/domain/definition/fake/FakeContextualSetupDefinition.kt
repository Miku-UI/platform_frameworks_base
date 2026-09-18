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

package com.android.systemui.communal.domain.definition.fake

import android.content.ComponentName
import com.android.systemui.communal.domain.definition.ContextualSetupDefinition
import com.android.systemui.communal.domain.definition.SetupTarget
import java.io.PrintWriter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeContextualSetupDefinition(override val id: String) : ContextualSetupDefinition {
    private val _isReady = MutableStateFlow(false)
    override val isReady: Flow<Boolean> = _isReady

    override var target: SetupTarget? = null

    override var priority: Int = 0

    override fun dump(pw: PrintWriter, args: Array<String>) {}

    fun setIsReady(value: Boolean) {
        _isReady.value = value
    }
}

val ContextualSetupDefinition.fake: FakeContextualSetupDefinition
    get() = this as FakeContextualSetupDefinition
