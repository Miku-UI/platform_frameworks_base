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
import javax.inject.Inject

/** A dark themed text clock dream to be shown when the device is in a low light environment. */
class LowLightClockDreamService
@Inject
constructor(private val implFactory: LowLightClockDreamServiceImpl.Factory) : DreamService() {
    private val dreamServiceDelegate by lazy {
        DreamServiceDelegateImpl(
            dreamService = this,
            layoutInflater = LayoutInflater.from(applicationContext),
        )
    }

    private val impl: LowLightClockDreamServiceImpl by lazy {
        implFactory.create(dreamServiceDelegate)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        impl.onAttachedToWindow()
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        impl.onDreamingStarted()
    }

    override fun onDreamingStopped() {
        super.onDreamingStopped()
        impl.onDreamingStopped()
    }

    override fun onWakeUp() {
        // Don't call super.onWakeUp(), which would just finish the dream. The
        // impl will handle waking up when it's finished.
        impl.onWakeUp()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        impl.onDetachedFromWindow()
    }
}
