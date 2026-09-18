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

package com.android.systemui.notetask.shortcut

import android.app.Activity
import android.app.WindowConfiguration.WindowingMode

/** An interface for windowing mode utility methods. */
interface WindowingModeFetcher {
    /** Returns the windowing mode of the given [Activity]. */
    @WindowingMode fun getWindowingMode(activity: Activity): Int

    /** Returns true if the given [Activity] is in the multi window mode. */
    fun isInMultiWindowMode(activity: Activity): Boolean
}
