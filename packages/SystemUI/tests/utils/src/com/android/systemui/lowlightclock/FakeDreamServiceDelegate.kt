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

/** Fake implementation of [DreamServiceDelegate] for tests. */
class FakeDreamServiceDelegate : DreamServiceDelegate {
    override var isInteractive: Boolean = false
    override var isFullscreen: Boolean = false
    var setContentViewResId: Int? = null
        private set

    var screenBrightness: Float? = null
        private set

    var finished: Boolean = false
        private set

    private val views = mutableMapOf<Int, View>()

    override fun setContentView(@LayoutRes id: Int) {
        setContentViewResId = id
    }

    override fun <T : View> findViewById(id: Int): T? {
        @Suppress("UNCHECKED_CAST")
        return views[id] as? T
    }

    override fun setScreenBrightness(brightness: Float) {
        screenBrightness = brightness
    }

    override fun onWakeUp() {}

    override fun finish() {
        finished = true
    }

    fun setViewForId(id: Int, view: View) {
        views[id] = view
    }
}

val DreamServiceDelegate.fake
    get() = this as FakeDreamServiceDelegate
