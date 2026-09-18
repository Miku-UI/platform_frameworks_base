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

package com.android.compose.animation.scene.mechanics

import com.android.systemui.Flags as SysuiFlags

internal object UserActionGestureFlag {
    /** The aconfig flag name */
    const val FLAG_NAME = SysuiFlags.FLAG_STL_USER_ACTION_GESTURE

    /**
     * Enables support for the experimental [UserActionGesture] interface.
     *
     * NOTE: stl_user_action_gesture is rolled forward independently from the scene_container flag,
     * aiming to cleanup earlier than scene_container launch.
     */
    val isEnabled: Boolean
        get() = SysuiFlags.stlUserActionGesture()

    /**
     * Called to ensure code is only run when the flag is disabled. This will throw an exception if
     * the flag is enabled to ensure that the refactor author catches issues in testing.
     */
    fun assertInNewMode() {
        check(isEnabled) { "New code path not supported when $FLAG_NAME is disabled." }
    }

    /**
     * Called to ensure code is only run when the flag is disabled. This will throw an exception if
     * the flag is enabled to ensure that the refactor author catches issues in testing.
     */
    fun assertInLegacyMode() {
        check(!isEnabled) { "Legacy code path not supported when $FLAG_NAME is enabled." }
    }
}
