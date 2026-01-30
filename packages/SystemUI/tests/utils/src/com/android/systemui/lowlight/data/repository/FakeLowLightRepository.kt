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

package com.android.systemui.lowlight.data.repository

import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.lifecycle.ExclusiveActivatable
import com.android.systemui.lowlight.shared.model.LowLightActionEntry
import com.android.systemui.lowlight.shared.model.LowLightDisplayBehavior

@SysUISingleton
class FakeLowLightRepository : LowLightRepository {
    private val actions = mutableMapOf<LowLightDisplayBehavior, ExclusiveActivatable>()

    override fun getEntry(behavior: LowLightDisplayBehavior): LowLightActionEntry? {
        return actions[behavior]?.let { LowLightActionEntry(behavior) { it } }
    }

    fun addAction(behavior: LowLightDisplayBehavior, action: ExclusiveActivatable) {
        actions[behavior] = action
    }
}
