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

import android.service.dreams.DreamService
import android.view.LayoutInflater
import android.view.View
import androidx.annotation.LayoutRes

/** Implementation of [DreamServiceDelegate] that delegates to a [DreamService]. */
class DreamServiceDelegateImpl(
    private val dreamService: DreamService,
    private val layoutInflater: LayoutInflater,
) : DreamServiceDelegate {
    override var isInteractive: Boolean
        get() = dreamService.isInteractive
        set(value) {
            dreamService.isInteractive = value
        }

    override var isFullscreen: Boolean
        get() = dreamService.isFullscreen
        set(value) {
            dreamService.isFullscreen = value
        }

    override fun setContentView(@LayoutRes id: Int) {
        dreamService.setContentView(layoutInflater.inflate(id, null))
    }

    override fun <T : View> findViewById(id: Int): T? {
        return dreamService.findViewById(id)
    }

    override fun setScreenBrightness(brightness: Float) {
        dreamService.setScreenBrightness(brightness)
    }

    override fun onWakeUp() {
        dreamService.onWakeUp()
    }

    override fun finish() {
        dreamService.finish()
    }
}
