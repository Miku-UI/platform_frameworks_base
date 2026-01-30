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

package com.android.systemui.flashlight.shared.logger

import com.android.systemui.flashlight.shared.model.FlashlightModel
import com.android.systemui.log.LogBuffer
import com.android.systemui.log.core.LogLevel.DEBUG
import com.android.systemui.log.core.LogLevel.WARNING
import com.android.systemui.log.core.LogLevel.WTF
import com.android.systemui.log.dagger.FlashlightLog
import javax.inject.Inject

class FlashlightLogger @Inject constructor(@FlashlightLog private val buffer: LogBuffer) {

    fun logStateChanged(state: FlashlightModel) {
        buffer.log(TAG, DEBUG, { str1 = state.toString() }, { "Flashlight state=$str1" })
    }

    fun dialogW(msg: String) {
        buffer.log(TAG, WARNING, { str1 = msg }, { "Flashlight Dialog: $str1" })
    }

    fun d(msg: String) {
        buffer.log(TAG, DEBUG, { str1 = msg }, { "$str1" })
    }

    fun w(msg: String) {
        buffer.log(TAG, WARNING, { str1 = msg }, { "$str1" })
    }

    fun wtf(msg: String) {
        buffer.log(TAG, WTF, { str1 = msg }, { "$str1" })
    }
}

private const val TAG = "Flashlight"
