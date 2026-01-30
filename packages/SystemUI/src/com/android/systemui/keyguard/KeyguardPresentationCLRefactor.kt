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

package com.android.systemui.keyguard

import android.window.DesktopExperienceFlags
import com.android.systemui.Flags
import com.android.systemui.flags.RefactorFlagUtils
import com.android.systemui.shade.shared.flag.ShadeWindowGoesAround

/** Helper for reading or using the keyguard presentation refactor class. */
@Suppress("NOTHING_TO_INLINE")
object KeyguardPresentationCLRefactor {

    private const val FLAG_NAME = Flags.FLAG_ENABLE_CONSTRAINT_LAYOUT_LOCKSCREEN_ON_EXTERNAL_DISPLAY

    val FLAG =
        DesktopExperienceFlags.DesktopExperienceFlag(
            Flags::enableConstraintLayoutLockscreenOnExternalDisplay,
            /* shouldOverrideByDevOption= */ true,
            FLAG_NAME,
        )

    /** Is the refactor enabled */
    @JvmStatic
    inline val isEnabled: Boolean
        get() = FLAG.isTrue

    /**
     * Called to ensure code is only run when the flag is enabled. This protects users from the
     * unintended behaviors caused by accidentally running new logic, while also crashing on an eng
     * build to ensure that the refactor author catches issues in testing.
     */
    @JvmStatic
    inline fun isUnexpectedlyInLegacyMode() =
        RefactorFlagUtils.isUnexpectedlyInLegacyMode(isEnabled, ShadeWindowGoesAround.FLAG_NAME)

    /**
     * Called to ensure code is only run when the flag is disabled. This will throw an exception if
     * the flag is enabled to ensure that the refactor author catches issues in testing.
     */
    @JvmStatic
    inline fun assertInLegacyMode() =
        RefactorFlagUtils.assertInLegacyMode(isEnabled, ShadeWindowGoesAround.FLAG_NAME)
}
