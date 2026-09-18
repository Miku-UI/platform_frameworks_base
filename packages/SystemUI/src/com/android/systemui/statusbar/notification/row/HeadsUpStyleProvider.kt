/*
 * Copyright (C) 2024 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.systemui.statusbar.notification.row

import android.view.Display
import com.android.app.displaylib.PerDisplayRepository
import com.android.systemui.display.dagger.SystemUIDisplaySubcomponent
import javax.inject.Inject

/**
 * A class managing the heads up style to be applied based on user settings, immersive mode and
 * other factors.
 */
interface HeadsUpStyleProvider {
    fun shouldApplyCompactStyle(displayId: Int): Boolean
}

class HeadsUpStyleProviderImpl
@Inject
constructor(
    private val displaySubcomponentRepo: PerDisplayRepository<SystemUIDisplaySubcomponent>
) : HeadsUpStyleProvider {

    override fun shouldApplyCompactStyle(displayId: Int): Boolean {
        return android.app.Flags.alwaysShowMinimalHun() || isInImmersiveMode(displayId)
    }

    private fun isInImmersiveMode(displayId: Int): Boolean {
        val displaySubcomponent =
            displaySubcomponentRepo[displayId] ?: displaySubcomponentRepo[Display.DEFAULT_DISPLAY]!!
        return displaySubcomponent.statusBarModeRepo.isInFullscreenMode.value
    }
}
