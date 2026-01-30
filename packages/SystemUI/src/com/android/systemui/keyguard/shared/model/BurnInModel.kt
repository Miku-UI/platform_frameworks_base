/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.systemui.keyguard.shared.model

import com.android.systemui.shared.Flags

/** Clock burn-in translation/scaling data */
data class BurnInModel(
    val translationX: Int = 0,
    val translationY: Int = 0,
    val scale: Float = MAX_LARGE_CLOCK_SCALE,
    val scaleClockOnly: Boolean = false,
) {
    companion object {
        /**
         * The maximum scale for the large clock.
         *
         * We use a custom getter here instead of static initialization to support test
         * environments. This ensures the value of the flag is read dynamically during each test
         * run, after test rules have had a chance to set the flag's state, preventing a
         * FlagSetException.
         */
        val MAX_LARGE_CLOCK_SCALE: Float
            get() {
                return if (Flags.clockReactiveSmartspaceLayout()) 0.9f else 1f
            }
    }
}
