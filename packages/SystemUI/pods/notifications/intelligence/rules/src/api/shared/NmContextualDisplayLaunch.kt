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

package com.android.systemui.notifications.intelligence.rules.shared

import android.app.Flags
import com.android.systemui.flags.FlagToken
import com.android.systemui.flags.RefactorFlagUtils

/** Helper for reading or using the nm contextual display launch flag state. */
@Suppress("NOTHING_TO_INLINE")
public object NmContextualDisplayLaunch {
    /** The aconfig flag name */
    public const val FLAG_NAME: String = Flags.FLAG_NM_CONTEXTUAL_DISPLAY_LAUNCH

    /** A token used for dependency declaration */
    public val token: FlagToken
        get() = FlagToken(FLAG_NAME, isEnabled)

    /** Is the refactor enabled */
    @JvmStatic
    public inline val isEnabled: Boolean
        get() = Flags.nmContextualDisplayLaunch()

    /**
     * Called to ensure code is only run when the flag is enabled. This can be used to protect users
     * from the unintended behaviors caused by accidentally running new logic, while also crashing
     * on an eng build to ensure that the refactor author catches issues in testing.
     */
    @JvmStatic
    public inline fun isUnexpectedlyInLegacyMode(): Boolean =
        RefactorFlagUtils.isUnexpectedlyInLegacyMode(isEnabled, FLAG_NAME)

    /**
     * Called to ensure code is only run when the flag is enabled. This will call Log.wtf if the
     * flag is not enabled to ensure that the refactor author catches issues in testing.
     *
     * NOTE: This can be useful for simple methods, but does not return the flag state, so it cannot
     * be used to implement a safe exit, and as such it does not support code stripping. If the
     * calling code will do work that is unsafe when the flag is off, it is recommended to write an
     * early return with `if (isUnexpectedlyInLegacyMode()) return`.
     */
    @JvmStatic
    public inline fun expectInNewMode() {
        RefactorFlagUtils.isUnexpectedlyInLegacyMode(isEnabled, FLAG_NAME)
    }

    /**
     * Called to ensure code is only run when the flag is disabled. This will throw an exception if
     * the flag is enabled to ensure that the refactor author catches issues in testing.
     */
    @JvmStatic
    public inline fun assertInLegacyMode(): Unit =
        RefactorFlagUtils.assertInLegacyMode(isEnabled, FLAG_NAME)
}
