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

package com.android.wm.shell.desktopmode

import android.content.Context
import com.android.wm.shell.dagger.WMSingleton
import com.android.wm.shell.shared.R
import javax.inject.Inject

/** Encapsulate Desktop Windowing animation configurations. */
@WMSingleton
class DesktopAnimationConfiguration @Inject constructor(context: Context) {

    val toDesktopAnimationDurationMs =
        context.resources.getInteger(R.integer.to_desktop_animation_duration_ms)
}
