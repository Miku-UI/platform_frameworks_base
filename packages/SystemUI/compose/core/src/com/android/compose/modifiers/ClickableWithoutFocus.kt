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

package com.android.compose.modifiers

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

/**
 * A custom modifier that handles click gestures without making the component focusable.
 *
 * This is a low-level replacement for the standard `clickable` modifier, used
 * specifically when you need to capture a tap event but want to prevent the
 * component from gaining focus, which is the default behavior of `clickable`.
 *
 * @param onClick The lambda to be executed when a tap gesture is detected.
 */
fun Modifier.clickableWithoutFocus(onClick: () -> Unit): Modifier = this.pointerInput(onClick) {
    detectTapGestures(
        onTap = { onClick() }
    )
}