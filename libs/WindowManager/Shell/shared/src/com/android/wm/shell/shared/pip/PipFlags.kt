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

package com.android.wm.shell.shared.pip

import android.app.AppGlobals
import android.content.pm.PackageManager
import com.android.wm.shell.Flags

class PipFlags {
    companion object {
        /**
         * @return {@code true} if PiP2 implementation should be used.
         *
         * Note: PiP2 is now enabled by default on all non-TV devices.
         */
        @JvmStatic
        val isPip2ExperimentEnabled: Boolean by lazy {
            val isTv =
                AppGlobals.getPackageManager().hasSystemFeature(PackageManager.FEATURE_LEANBACK, 0)
            Flags.enablePip2() && (!isTv || Flags.enablePip2OnTv())
        }

        @JvmStatic val isPipUmoExperienceEnabled: Boolean by lazy { Flags.enablePipUmoExperience() }
    }
}
