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

package com.android.server.wm.flicker.helpers

import android.app.Instrumentation
import android.tools.device.apphelpers.StandardAppHelper
import android.tools.traces.component.ComponentNameMatcher
import android.tools.traces.parsers.toFlickerComponent
import com.android.launcher3.tapl.AppIcon
import com.android.server.wm.flicker.testapp.ActivityOptions.StaticShortcutsActivity

/** App helper for the static shortcuts activity. */
class StaticShortcutsAppHelper @JvmOverloads constructor(
    instr: Instrumentation,
    launcherName: String = StaticShortcutsActivity.LABEL,
    component: ComponentNameMatcher = StaticShortcutsActivity.COMPONENT.toFlickerComponent(),
) : StandardAppHelper(instr, launcherName, component) {

    /**
     * Clicks the shortcut from the app icon menu to launches its activity.
     *
     * @param appIcon The app icon to open the menu from.
     * @param shortcutLabel The label of the shortcut to launch.
     */
    fun launchViaShortcut(
        appIcon: AppIcon,
        shortcutLabel: String = StaticShortcutsActivity.Shortcuts.LAUNCH_SSA,
    ) {
        val menu = appIcon.openMenu()
        menu.getMenuItem(shortcutLabel).launch(packageName)
    }
}
