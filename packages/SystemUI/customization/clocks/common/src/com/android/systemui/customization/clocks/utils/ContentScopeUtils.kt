/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.android.systemui.customization.clocks.utils

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.android.compose.animation.scene.BaseContentScope
import com.android.compose.animation.scene.ElementContentScope
import com.android.compose.animation.scene.ElementKey
import com.android.compose.animation.scene.ElementScope
import com.android.compose.animation.scene.MovableElementContentScope
import com.android.compose.animation.scene.MovableElementKey

object ContentScopeUtils {
    @Composable
    /** Convenience method for building an element w/ the default Modifier */
    fun BaseContentScope.Element(key: ElementKey, content: @Composable BoxScope.() -> Unit) {
        Element(key, Modifier, content)
    }

    @Composable
    /** Convenience method for building an element w/ the default Modifier */
    fun BaseContentScope.ElementWithValues(
        key: ElementKey,
        content: @Composable (ElementScope<ElementContentScope>.() -> Unit),
    ) {
        ElementWithValues(key, Modifier, content)
    }

    @Composable
    /** Convenience method for building a movable element w/ the default Modifier */
    fun BaseContentScope.MovableElement(
        key: MovableElementKey,
        content: @Composable ElementScope<MovableElementContentScope>.() -> Unit,
    ) {
        MovableElement(key, Modifier, content)
    }
}
