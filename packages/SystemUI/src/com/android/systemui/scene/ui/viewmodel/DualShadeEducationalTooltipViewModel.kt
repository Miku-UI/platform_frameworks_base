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

package com.android.systemui.scene.ui.viewmodel

import androidx.compose.runtime.Stable
import androidx.compose.ui.unit.IntRect

@Stable
interface DualShadeEducationalTooltipViewModel {
    val text: String
    /** Bounds of the UI element underneath which the tooltip should be anchored. */
    val anchorBounds: IntRect
    /** Notifies that the tooltip has been shown to the user. The UI should call this. */
    val onShown: () -> Unit
    /** Notifies that the tooltip has been dismissed by the user. The UI should call this. */
    val onDismissed: () -> Unit
}
