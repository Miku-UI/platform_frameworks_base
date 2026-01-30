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

package com.android.systemui.shade

/**
 * No-op implementation of [QuickSettingsController], for SysUI variants that don't use it but
 * inject code that depends on it.s
 */
class NoOpQuickSettingsController : QuickSettingsController {
    override val expanded: Boolean
        get() = false

    override val isCustomizing: Boolean
        get() = false

    @Deprecated("specific to legacy touch handling")
    override fun shouldQuickSettingsIntercept(x: Float, y: Float, yDiff: Float): Boolean {
        return false
    }

    override fun closeQsCustomizer() {}

    @Deprecated("specific to legacy split shade") override fun closeQs() {}

    @Deprecated("specific to legacy DebugDrawable")
    override fun calculateNotificationsTopPadding(
        isShadeExpanding: Boolean,
        keyguardNotificationStaticPadding: Int,
        expandedFraction: Float,
    ): Float {
        return 0f
    }

    @Deprecated("specific to legacy DebugDrawable")
    override fun calculatePanelHeightExpanded(stackScrollerPadding: Int): Int {
        return 0
    }

    override fun setPanelExpanded(panelExpanded: Boolean) {
        return
    }
}
