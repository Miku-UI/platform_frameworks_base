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

package com.android.systemui.screencapture.common.shared.model

import android.graphics.Rect

/** The target to be captured. */
sealed interface ScreenCaptureTarget {
    /** A full display. */
    data class Fullscreen(val displayId: Int) : ScreenCaptureTarget

    /** A region on a display. */
    data class Region(val displayId: Int, val rect: Rect) : ScreenCaptureTarget

    /** A full app. */
    data class App(val displayId: Int, val taskId: Int) : ScreenCaptureTarget

    /** Content within an app. */
    data class AppContent(val contentId: Int) : ScreenCaptureTarget
}
