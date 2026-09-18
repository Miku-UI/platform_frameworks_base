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

package com.android.wm.shell.shared.desktopmode

/** A listener that will receive callbacks about status bar appearance changes. */
fun interface DesktopScrimListener {
    /**
     * Called when the opacity of the status bar should change to apply a light out effect. When the
     * user maximize a window or put windows in tiled mode, a darker surrounding area will make the
     * user focus on the window.
     */
    fun onDesktopScrimEffectChanged(displayId: Int, applyLightOutEffect: Boolean)
}
