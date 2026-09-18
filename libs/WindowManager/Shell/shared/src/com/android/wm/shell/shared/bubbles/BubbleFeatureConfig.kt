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

package com.android.wm.shell.shared.bubbles

/** Some features of bubbles aren't available on all devices. This allows easy querying. */
interface BubbleFeatureConfig {

    /**
     * Returns {@code true} if the user is permitted to convert any app into a Bubble (the app
     * bubble feature). This is not permitted on low-ram devices (to preserve memory) or on displays
     * that support desktop windowing (more effort required for the feature to be supported with
     * desktop windowing, such as polishing transitions between bubbles and other windowing modes).
     */
    fun areAppBubblesSupported(): Boolean

    /** Returns {@code true} if the scrim can be shown. */
    fun isScrimEnabled(displayId: Int): Boolean
}
