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
package com.android.server.companion.datatransfer.crossdevicesync.common.fake

import com.android.server.companion.datatransfer.crossdevicesync.common.DeviceUtils

/** Fake implementation of [DeviceUtils]. */
class FakeDeviceUtils : DeviceUtils {
    private var isWatch: Boolean = false

    /** Whether the device is a watch or not. */
    override fun isWatch(): Boolean = isWatch

    fun setWatch(isWatch: Boolean) {
        this.isWatch = isWatch
    }

    /** Whether the device is a kids watch or not. */
    private var isKidsWatch: Boolean = false

    private val listeners = mutableListOf<DeviceUtils.Listener>()

    fun setKidsWatch(isKidsWatch: Boolean) {
        this.isKidsWatch = isKidsWatch
        listeners.forEach { it.onKidsWatchChanged(isKidsWatch) }
    }

    override fun isKidsWatch(): Boolean = isKidsWatch

    override fun registerKidsWatchChangeListener(listener: DeviceUtils.Listener) {
        listeners.add(listener)
    }

    override fun unregisterKidsWatchChangeListener(listener: DeviceUtils.Listener) {
        listeners.remove(listener)
    }
}
