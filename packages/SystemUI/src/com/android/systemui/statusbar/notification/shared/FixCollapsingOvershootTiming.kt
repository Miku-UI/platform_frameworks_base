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

package com.android.systemui.statusbar.notification.shared

import com.android.systemui.Flags
import com.android.systemui.flags.FlagToken

/** Helper for reading or using the fix collapsing overshoot timing flag state. */
@Suppress("NOTHING_TO_INLINE")
object FixCollapsingOvershootTiming {
    const val FLAG_NAME = Flags.FLAG_FIX_COLLAPSING_OVERSHOOT_TIMING

    /** A token used for dependency declaration */
    val token: FlagToken
        get() = FlagToken(FLAG_NAME, isEnabled)

    /** Is the fix collapsing overshoot timing enabled */
    @JvmStatic
    inline val isEnabled
        get() = Flags.fixCollapsingOvershootTiming()
}
