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

package com.android.systemui.lowlight

class FakeAmbientLightModeMonitor : AmbientLightModeMonitor {
    @AmbientLightModeMonitor.AmbientLightMode private var ambientLightMode: Int? = null
    private var callback: AmbientLightModeMonitor.Callback? = null
    private var _started: Boolean = false

    val started: Boolean
        get() = _started

    override fun start(callback: AmbientLightModeMonitor.Callback) {
        _started = true
        this.callback = callback
        ambientLightMode?.let { callback.onChange(it) }
    }

    override fun stop() {
        _started = false
        this.callback = null
    }

    fun setAmbientLightMode(@AmbientLightModeMonitor.AmbientLightMode mode: Int) {
        ambientLightMode = mode
        callback?.onChange(mode)
    }
}

val AmbientLightModeMonitor.fake
    get() = this as FakeAmbientLightModeMonitor
