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

package com.android.systemui.desktop

import com.android.systemui.dagger.SysUISingleton
import com.android.wm.shell.desktopmode.DesktopMode
import com.android.wm.shell.shared.desktopmode.DesktopFirstListener
import java.util.Optional
import javax.inject.Inject

/**
 * Repository that tracks whether a display is in "Desktop First" mode.
 *
 * "Desktop First" mode for a display means that newly opened applications on that particular
 * display will launch in [WindowConfiguration.WINDOWING_MODE_FREEFORM].
 *
 * This repository listens to changes from [DesktopMode] and maintains the state for each display
 * ID.
 */
@SysUISingleton
class DesktopFirstRepository @Inject constructor(desktopMode: Optional<DesktopMode>) :
    DesktopFirstListener {

    private val _isDisplayDesktopFirst: MutableMap<Int, Boolean> = mutableMapOf()

    init {
        desktopMode.ifPresent { desktopMode.get().registerDesktopFirstListener(this) }
    }

    /**
     * Checks if the display with the given [displayId] is in "Desktop First" mode.
     *
     * In "Desktop First" mode, new apps on the display open in freeform mode by default. This
     * function retrieves the tracked state for the specified display.
     */
    fun isDisplayDesktopFirst(displayId: Int) = _isDisplayDesktopFirst[displayId] == true

    override fun onStateChanged(displayId: Int, isDesktopFirstEnabled: Boolean) {
        _isDisplayDesktopFirst[displayId] = isDesktopFirstEnabled
    }
}
