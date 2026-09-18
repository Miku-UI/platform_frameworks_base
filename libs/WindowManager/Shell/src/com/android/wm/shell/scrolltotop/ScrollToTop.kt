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

package com.android.wm.shell.scrolltotop

import com.android.wm.shell.shared.annotations.ExternalThread

/** Interface for the Scroll To Top feature. */
@ExternalThread
interface ScrollToTop {
    /**
     * Called when a scroll to top event is detected (e.g. status bar tap).
     *
     * @param displayId The ID of the display where the event occurred.
     * @param x The x-coordinate of the event in display coordinates.
     */
    fun onScrollToTop(displayId: Int, x: Int)
}
