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

package com.android.systemui.lowlightclock

import android.view.View
import androidx.annotation.LayoutRes

/** Delegate for [android.service.dreams.DreamService] operations. */
interface DreamServiceDelegate {
    var isInteractive: Boolean
    var isFullscreen: Boolean

    fun setContentView(@LayoutRes id: Int)

    fun <T : View> findViewById(id: Int): T?

    fun setScreenBrightness(brightness: Float)

    fun onWakeUp()

    fun finish()
}
