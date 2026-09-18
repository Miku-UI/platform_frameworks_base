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

import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.UnplacedAwareModifierNode
import androidx.compose.ui.platform.InspectorInfo

/**
 * A modifier whose [onUnplaced] callback is invoked when the parent layout was placed earlier, and
 * it is not placed anymore. @see [UnplacedAwareModifierNode] for implementation details.
 */
@Stable fun Modifier.onUnplaced(onUnplaced: () -> Unit) = this then OnUnplacedElement(onUnplaced)

private data class OnUnplacedElement(val onUnplaced: () -> Unit) :
    ModifierNodeElement<OnUnplacedNode>() {
    override fun create() = OnUnplacedNode(callback = onUnplaced)

    override fun update(node: OnUnplacedNode) {
        node.callback = onUnplaced
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "onUnplaced"
        properties["onUnplaced"] = onUnplaced
    }
}

internal class OnUnplacedNode(var callback: () -> Unit) :
    UnplacedAwareModifierNode, Modifier.Node() {

    override fun onUnplaced() {
        callback()
    }
}
