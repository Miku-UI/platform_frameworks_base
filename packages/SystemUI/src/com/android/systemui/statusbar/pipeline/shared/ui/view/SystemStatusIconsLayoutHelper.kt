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

package com.android.systemui.statusbar.pipeline.shared.ui.view

import com.android.systemui.res.R
import com.android.systemui.statusbar.core.NewStatusBarIcons

/**
 * Icons defined with [NewStatusBarIcons] want to have a simple, uniform margin before/after their
 * views, because they don't have any whitespace around the vector itself.
 *
 * This object defines a single method to configure 3sp of padding after the systemIcons layout.
 * Along with the 3sp of padding that the new icons embed, this total 6sp of space between the
 * most-right icon and the battery.
 *
 * Useful for the keyguard, home screen, and shade header status bars.
 */
object SystemStatusIconsLayoutHelper {
    /** Set the paddingEnd to 3sp for the system icons container */
    @JvmStatic
    fun configurePaddingForNewStatusBarIcons(systemIcons: android.widget.LinearLayout) {
        // Once the flag rolls out, move these values into the layout xml
        /* check if */ NewStatusBarIcons.isUnexpectedlyInLegacyMode()
        systemIcons.apply {
            setPaddingRelative(
                /* start = */ paddingStart,
                /* top = */ paddingTop,
                /* end = */ context.resources.getDimensionPixelSize(
                    R.dimen.signal_cluster_battery_padding_new
                ),
                /* bottom = */ paddingBottom,
            )
        }
    }
}
