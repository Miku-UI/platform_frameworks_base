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

package com.android.systemui.display.flags

import android.window.DesktopExperienceFlags
import com.android.systemui.flags.RefactorFlagUtils
import com.android.window.flags.Flags

/** Helper for reading or using the enable wm callback for system decor changes flag state. */
object WmCallbackForSysDecorFlag {
    /** The aconfig flag name */
    const val FLAG_NAME = Flags.FLAG_ENABLE_SYS_DECORS_CALLBACKS_VIA_WM

    /**
     * When true, this is defined as [DesktopExperienceFlags] to make it possible to enable it
     * together with all the other desktop experience flags from the dev settings.
     *
     * Alternatively, using adb:
     * ```bash
     * adb shell setprop persist.wm.debug.desktop_experience_devopts 1
     * ```
     */
    private const val ENABLED_BY_DESKTOP_EXPERIENCE_DEV_OPTION = true

    private val FLAG =
        DesktopExperienceFlags.DesktopExperienceFlag(
            Flags::enableSysDecorsCallbacksViaWm,
            /* shouldOverrideByDevOption= */ ENABLED_BY_DESKTOP_EXPERIENCE_DEV_OPTION,
            Flags.FLAG_ENABLE_SYS_DECORS_CALLBACKS_VIA_WM,
        )

    /** Is the refactor enabled */
    @JvmStatic
    val isEnabled: Boolean
        get() =
            FLAG.isTrue && DesktopExperienceFlags.ENABLE_DISPLAY_CONTENT_MODE_MANAGEMENT.isTrue()

    /**
     * Called to ensure code is only run when the flag is enabled. This protects users from the
     * unintended behaviors caused by accidentally running new logic, while also crashing on an eng
     * build to ensure that the refactor author catches issues in testing.
     */
    @JvmStatic
    inline fun isUnexpectedlyInLegacyMode() =
        RefactorFlagUtils.isUnexpectedlyInLegacyMode(isEnabled, FLAG_NAME)

    /**
     * Called to ensure code is only run when the flag is disabled. This will throw an exception if
     * the flag is enabled to ensure that the refactor author catches issues in testing.
     */
    @JvmStatic
    inline fun assertInLegacyMode() = RefactorFlagUtils.assertInLegacyMode(isEnabled, FLAG_NAME)
}
