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

package com.android.systemui.minmode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Allows an app to handle minmode events. */
interface MinModeManager {

    /** Adds a minmode event listener into manager. */
    fun addListener(callback: MinModeEventListener) {}

    /** Removes the added listener from minmode manager */
    fun removeListener(callback: MinModeEventListener) {}

    /** Returns true if the device is in minmode state. */
    fun isMinModeEnabled(): Boolean = false

    val isMinModeInForegroundFlow: Flow<Boolean>
      get() = flowOf(false)


    /** Listens to minmode events. */
    fun interface MinModeEventListener {
        /** Override to handle minmode events. */
        fun onEvent(event: Int)
    }


    companion object {
        /** Uninitialized states. */
        const val STATE_NONE = 0
        /** The state for minmode enabled, but not running. */
        const val STATE_MINMODE_ENABLED = 1

        /** The state for minmode active. */
        const val STATE_MINMODE_ACTIVE = 2
    }
}