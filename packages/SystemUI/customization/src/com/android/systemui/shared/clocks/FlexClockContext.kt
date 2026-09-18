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
package com.android.systemui.shared.clocks

import com.android.systemui.customization.clocks.ClockContext
import com.android.systemui.customization.clocks.ClockContextImpl
import com.android.systemui.customization.clocks.TypefaceCache
import com.android.systemui.log.core.MessageBuffer

class FlexClockContext(
    val typefaceCache: TypefaceCache<Unit>,
    private val innerCtx: ClockContextImpl,
) : ClockContext by innerCtx {
    fun copy(messageBuffer: MessageBuffer): FlexClockContext {
        return FlexClockContext(typefaceCache, innerCtx.copy(messageBuffer = messageBuffer))
    }
}
