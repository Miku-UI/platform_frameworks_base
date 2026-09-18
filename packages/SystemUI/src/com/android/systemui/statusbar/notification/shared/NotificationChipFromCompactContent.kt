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

package com.android.systemui.statusbar.notification.shared

import com.android.systemui.flags.RefactorFlagUtils

/** Helper for reading or using the "notification chip from compact content" flag. */
@Suppress("NOTHING_TO_INLINE")
object NotificationChipFromCompactContent {
    /** The aconfig flag name */
    const val MULTIPLE_FLAG_NAMES =
        "${com.android.systemui.Flags.FLAG_NOTIFICATION_CHIP_FROM_COMPACT_CONTENT} and ${android.app.Flags.FLAG_API_METRIC_STYLE} and ${android.app.Flags.FLAG_API_NOTIFICATION_SEMANTIC_STYLE}"

    /** Is the refactor enabled */
    @JvmStatic
    inline val isEnabled
        get() =
            com.android.systemui.Flags.notificationChipFromCompactContent() &&
                android.app.Flags.apiMetricStyle() &&
                android.app.Flags.apiNotificationSemanticStyle()

    /**
     * Called to ensure code is only run when the flag is enabled. This can be used to protect users
     * from the unintended behaviors caused by accidentally running new logic, while also crashing
     * on an eng build to ensure that the refactor author catches issues in testing.
     */
    @JvmStatic
    inline fun isUnexpectedlyInLegacyMode() =
        RefactorFlagUtils.isUnexpectedlyInLegacyMode(isEnabled, MULTIPLE_FLAG_NAMES)

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
    inline fun expectInNewMode() {
        RefactorFlagUtils.isUnexpectedlyInLegacyMode(isEnabled, MULTIPLE_FLAG_NAMES)
    }

    /**
     * Called to ensure code is only run when the flag is disabled. This will throw an exception if
     * the flag is enabled to ensure that the refactor author catches issues in testing.
     */
    @JvmStatic
    inline fun assertInLegacyMode() =
        RefactorFlagUtils.assertInLegacyMode(isEnabled, MULTIPLE_FLAG_NAMES)
}
