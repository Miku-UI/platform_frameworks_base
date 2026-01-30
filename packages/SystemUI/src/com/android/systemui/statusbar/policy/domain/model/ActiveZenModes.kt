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

package com.android.systemui.statusbar.policy.domain.model

import com.android.settingslib.notification.modes.ZenMode

/**
 * Represents the list of [ZenMode] instances that are currently active.
 *
 * @property names Names of all the active modes, sorted by their priority.
 * @property main The most prioritized active mode, if any modes active. Guaranteed to be non-null
 *   if [isAnyActive] is `true`.
 */
data class ActiveZenModes(val names: List<String>, val main: ZenModeInfo?) {
    init {
        require(names.isEmpty() || main != null) {
            "If names is not empty, main mode must be non-null"
        }
    }

    val count: Int = names.size

    fun isAnyActive(): Boolean = names.isNotEmpty()
}
