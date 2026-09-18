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

package com.android.systemui.statusbar.chips.ui.compose

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.constrain
import java.util.Locale

/** A modifier that ensures the width of the content only increases and never decreases. */
fun Modifier.neverDecreaseWidth(density: Density, locale: Locale?, textLength: Int): Modifier {
    return this.then(NeverDecreaseWidthElement(density, locale, textLength))
}

private data class NeverDecreaseWidthElement(
    val density: Density,
    val locale: Locale?,
    val textLength: Int,
) : ModifierNodeElement<NeverDecreaseWidthNode>() {
    override fun create(): NeverDecreaseWidthNode {
        return NeverDecreaseWidthNode(density, locale, textLength)
    }

    override fun update(node: NeverDecreaseWidthNode) {
        node.textLength = textLength
        node.locale = locale
        node.density = density
    }
}

private class NeverDecreaseWidthNode(
    initialDensity: Density,
    initialLocale: Locale?,
    initialTextLength: Int,
) : Modifier.Node(), LayoutModifierNode {
    private var minWidth = 0

    var density: Density = initialDensity
        set(value) {
            if (field != value) {
                // Reset minWidth in case display size decreased. See b/395607413.
                minWidth = 0
            }
            field = value
        }

    var locale: Locale? = initialLocale
        set(value) {
            if (field != value) {
                // Reset minWidth in case new locale has smaller characters. See b/414387398.
                minWidth = 0
            }
            field = value
        }

    var textLength = initialTextLength
        set(value) {
            if (field != value) {
                // Reset minWidth in case the total number of characters has decreased. (e.g. from
                // 1:00:00 to 59:59). See b/450956553.
                minWidth = 0
            }
            field = value
        }

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        val placeable = measurable.measure(Constraints(minWidth = minWidth).constrain(constraints))
        val width = placeable.width
        val height = placeable.height

        minWidth = maxOf(minWidth, width)

        return layout(width, height) { placeable.place(0, 0) }
    }
}
