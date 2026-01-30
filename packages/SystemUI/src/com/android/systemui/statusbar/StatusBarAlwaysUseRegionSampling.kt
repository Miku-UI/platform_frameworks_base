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

package com.android.systemui.statusbar

import com.android.systemui.Flags
import com.android.systemui.flags.FlagToken

/**
 * Helper for reading or using the status bar always use region sampling flag state.
 *
 * This flag enables region sampling for *all* users, regardless of accessibility settings. See
 * also: [StatusBarRegionSampling].
 */
@Suppress("NOTHING_TO_INLINE")
object StatusBarAlwaysUseRegionSampling {
    /** The aconfig flag name */
    const val FLAG_NAME = Flags.FLAG_STATUS_BAR_ALWAYS_USE_REGION_SAMPLING

    /** A token used for dependency declaration */
    val token: FlagToken
        get() = FlagToken(FLAG_NAME, isEnabled)

    /** Is the refactor enabled */
    @JvmStatic
    inline val isEnabled
        get() = Flags.statusBarAlwaysUseRegionSampling()

    /** Returns true if any kind of region sampling is enabled. */
    inline val isAnyRegionSamplingEnabled
        get() = isEnabled || StatusBarRegionSampling.isEnabled
}
