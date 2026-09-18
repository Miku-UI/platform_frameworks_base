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

package com.android.systemui.topwindoweffects.data.repository

/**
 * Allows the invocation effect to receive gesture callbacks for corner swipe invocation of
 * assistant.
 */
interface InvocationEffectGestureController {
    /** Returns true if this controller is enabled and can receive callback, false otherwise. */
    fun isGestureEffectEnabled(): Boolean

    /**
     * Updates the invocation gesture progress.
     *
     * @param progress a float between 0 and 1 inclusive. 0 represents the beginning of the gesture;
     *   1 represents the end.
     */
    fun onGestureProgress(progress: Float)

    /** Called when an invocation gesture completes. */
    fun onGestureCompletion()

    /** Hides any SysUI for the assistant gesture effect. */
    fun hideGestureEffect()
}
