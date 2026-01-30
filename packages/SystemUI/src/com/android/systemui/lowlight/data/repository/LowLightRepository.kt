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
import com.android.systemui.lowlight.shared.model.LowLightActionEntry
import com.android.systemui.lowlight.shared.model.LowLightDisplayBehavior
import javax.inject.Inject

/**
 * The {@link LowLightRepository} is responsible for handling non-settings related data for
 * low-light. This includes the available actions that can fulfill the user chosen behavior.
 */
interface LowLightRepository {
    /** Returns the action entry for the associated behavior, {@code null} otherwise. */
    fun getEntry(behavior: LowLightDisplayBehavior): LowLightActionEntry?
}

@SysUISingleton
class LowLightRepositoryImpl @Inject constructor(lowLightActionEntries: Set<LowLightActionEntry>) :
    LowLightRepository {
    private val actionMapping: Map<LowLightDisplayBehavior, LowLightActionEntry> =
        lowLightActionEntries.associateBy { it.behavior }

    override fun getEntry(behavior: LowLightDisplayBehavior): LowLightActionEntry? {
        return actionMapping[behavior]
    }
}
