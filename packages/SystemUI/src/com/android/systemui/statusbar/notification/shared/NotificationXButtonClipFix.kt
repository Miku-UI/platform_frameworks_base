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

import com.android.systemui.Flags
import com.android.systemui.flags.FlagToken
import com.android.systemui.flags.RefactorFlagUtils

/**
 * Helper for reading or using the flag that fixes a clipping issue for the notification dismiss
 * ('X') button on hover.
 */
@Suppress("NOTHING_TO_INLINE")
object NotificationXButtonClipFix {
    const val FLAG_NAME = Flags.FLAG_NOTIFICATION_X_BUTTON_CLIP_FIX

    val token: FlagToken
        get() = FlagToken(FLAG_NAME, isEnabled)

    @JvmStatic
    inline val isEnabled
        get() = Flags.notificationAddXOnHoverToDismiss() && Flags.notificationXButtonClipFix()

    @JvmStatic
    inline fun isUnexpectedlyInLegacyMode() =
        RefactorFlagUtils.isUnexpectedlyInLegacyMode(isEnabled, FLAG_NAME)

    @JvmStatic
    inline fun assertInLegacyMode() = RefactorFlagUtils.assertInLegacyMode(isEnabled, FLAG_NAME)
}
