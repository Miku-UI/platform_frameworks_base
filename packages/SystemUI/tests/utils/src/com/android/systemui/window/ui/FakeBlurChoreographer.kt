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

package com.android.systemui.window.ui

import com.android.systemui.window.shared.model.BlurEffect

class FakeBlurChoreographer : BlurChoreographer {
    var lastAppliedBlurEffect: BlurEffect? = null
        private set

    var persistentEarlyWakeup: Boolean = false
        private set

    override fun applyBlur(blurEffect: BlurEffect) {
        lastAppliedBlurEffect = blurEffect
    }

    override fun setPersistentEarlyWakeup(persistentEarlyWakeup: Boolean) {
        this.persistentEarlyWakeup = persistentEarlyWakeup
    }

    override fun registerOnBlurAppliedListener(listener: BlurAppliedListener) = Unit

    override fun clearOnBlurAppliedListener() = Unit
}
