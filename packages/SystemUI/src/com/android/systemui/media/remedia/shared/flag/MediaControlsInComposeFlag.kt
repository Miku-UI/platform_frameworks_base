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

package com.android.systemui.media.remedia.shared.flag

import com.android.systemui.Flags.mediaControlsInCompose
import com.android.systemui.flags.RefactorFlagUtils
import com.android.systemui.scene.shared.flag.SceneContainerFlag

object MediaControlsInComposeFlag {
    /** The flag description -- not an aconfig flag name */
    const val DESCRIPTION = "MediaControlsInComposeFlag"

    @JvmStatic
    inline val isEnabled: Boolean
        get() = mediaControlsInCompose() || SceneContainerFlag.isEnabled

    /**
     * Called to ensure code is only run when the flag is disabled. This will throw an exception if
     * the flag is enabled to ensure that the refactor author catches issues in testing.
     */
    @JvmStatic
    inline fun assertInLegacyMode() = RefactorFlagUtils.assertInLegacyMode(isEnabled, DESCRIPTION)
}
