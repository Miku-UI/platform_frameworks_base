/*
 * Copyright 2023 The Android Open Source Project
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

@file:Suppress("NOTHING_TO_INLINE")

package com.android.systemui.scene.shared.flag

import com.android.systemui.Flags.sceneContainer
import com.android.systemui.flags.RefactorFlagUtils

/** Helper for reading or using the scene container flag state. */
object SceneContainerFlag {
    /** The flag description -- not an aconfig flag name */
    const val DESCRIPTION = "SceneContainerFlag"

    /**
     * Whether the flag is enabled on the current variant. If this is set to `false`, then it the
     * value of the actual aconfig flag doesn't matter and [isEnabled] will always return `false`.
     *
     * Some variants of System UI, for example Automotive OS, do not support the scene container
     * framework. In order for that variant to be able to force it off, this property must be set to
     * `false` as early as possible in the runtime of the app (ideally, in the constructor the
     * Application class).
     */
    @JvmField var isEnabledOnVariant: Boolean = true

    @JvmStatic
    inline val isEnabled
        // NOTE: Changes should also be made in @EnableSceneContainer
        get() = sceneContainer() && isEnabledOnVariant

    /**
     * Called to ensure code is only run when the flag is enabled. This protects users from the
     * unintended behaviors caused by accidentally running new logic, while also crashing on an eng
     * build to ensure that the refactor author catches issues in testing.
     */
    @JvmStatic
    inline fun isUnexpectedlyInLegacyMode() =
        RefactorFlagUtils.isUnexpectedlyInLegacyMode(isEnabled, DESCRIPTION)

    /**
     * Called to ensure code is only run when the flag is disabled. This will throw an exception if
     * the flag is enabled to ensure that the refactor author catches issues in testing.
     */
    @JvmStatic
    inline fun assertInLegacyMode() = RefactorFlagUtils.assertInLegacyMode(isEnabled, DESCRIPTION)

    /**
     * Called to ensure the new code is only run when the flag is enabled. This will throw an
     * exception if the flag is disabled to ensure that the refactor author catches issues in
     * testing.
     */
    @JvmStatic
    @Deprecated("Avoid crashing.", ReplaceWith("if (this.isUnexpectedlyInLegacyMode()) return"))
    inline fun unsafeAssertInNewMode() =
        RefactorFlagUtils.unsafeAssertInNewMode(isEnabled, DESCRIPTION)
}
