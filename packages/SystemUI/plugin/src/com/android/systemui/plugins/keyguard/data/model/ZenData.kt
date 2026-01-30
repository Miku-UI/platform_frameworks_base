/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.android.systemui.plugins.keyguard.data.model

import android.provider.Settings.Global.ZEN_MODE_ALARMS
import android.provider.Settings.Global.ZEN_MODE_IMPORTANT_INTERRUPTIONS
import android.provider.Settings.Global.ZEN_MODE_NO_INTERRUPTIONS
import android.provider.Settings.Global.ZEN_MODE_OFF

data class ZenData(val zenMode: ZenMode, val descriptionId: String?) {
    enum class ZenMode(val zenMode: Int) {
        OFF(ZEN_MODE_OFF),
        IMPORTANT_INTERRUPTIONS(ZEN_MODE_IMPORTANT_INTERRUPTIONS),
        NO_INTERRUPTIONS(ZEN_MODE_NO_INTERRUPTIONS),
        ALARMS(ZEN_MODE_ALARMS);

        companion object {
            fun fromInt(zenMode: Int) = values().firstOrNull { it.zenMode == zenMode }
        }
    }
}
