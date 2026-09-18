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

@file:Suppress("NOTHING_TO_INLINE")

package com.android.systemui.statusbar.notification.shared

import com.android.systemui.Flags.nsslTouchDispatchFix
import com.android.systemui.flags.RefactorFlagUtils
import com.android.systemui.scene.shared.flag.SceneContainerFlag

/**
 * Helper for reading or using the flag that changes NSSL's touch dispatch logic to send complete,
 * well-formed streams with no gaps or duplicates.
 */
object NsslTouchDispatchFix {
    const val DESCRIPTION = "NSSLTouchDispatchFix"

    @JvmStatic
    inline val isEnabled
        get() = SceneContainerFlag.isEnabled && nsslTouchDispatchFix()

    @JvmStatic
    inline fun isUnexpectedlyInLegacyMode() =
        RefactorFlagUtils.isUnexpectedlyInLegacyMode(isEnabled, DESCRIPTION)

    @JvmStatic
    inline fun assertInLegacyMode() = RefactorFlagUtils.assertInLegacyMode(isEnabled, DESCRIPTION)
}
