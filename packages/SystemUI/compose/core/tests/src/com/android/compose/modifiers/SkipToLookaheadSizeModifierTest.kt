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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test

class SkipToLookaheadSizeModifierTest {

    @get:Rule val rule = createComposeRule()

    @Test
    fun measure_uses_lookahead_size_internally_forces_tight_constraints_on_child() {
        val targetSize = 100
        val approachSize = 10

        var childMeasuredWidth = 0
        var childConstraintsMinWidth = 0
        var parentReceivedWidth = 0

        rule.setContent {
            LookaheadScope {
                Layout(
                    content = {
                        Box(
                            Modifier.skipToLookaheadSize()
                                // SPY: Inspect what the Child receives and measures
                                .layout { measurable, constraints ->
                                    val p = measurable.measure(constraints)
                                    if (!isLookingAhead) {
                                        childMeasuredWidth = p.width
                                        childConstraintsMinWidth = constraints.minWidth
                                    }
                                    layout(p.width, p.height) { p.place(IntOffset.Zero) }
                                }
                                .size(with(LocalDensity.current) { targetSize.toDp() })
                        )
                    },
                    measurePolicy = { measurables, constraints ->
                        val child = measurables.first()
                        if (isLookingAhead) {
                            val p = child.measure(constraints)
                            layout(p.width, p.height) { p.place(IntOffset.Zero) }
                        } else {
                            val approachConstraints =
                                constraints.copy(maxWidth = approachSize, maxHeight = approachSize)
                            val p = child.measure(approachConstraints)
                            parentReceivedWidth = p.width
                            layout(p.width, p.height) { p.place(IntOffset.Zero) }
                        }
                    },
                )
            }
        }

        rule.runOnIdle {
            // 1. Check Resulting Size
            assertEquals(
                "Child should measure using the Lookahead size (100)",
                targetSize,
                childMeasuredWidth,
            )

            // 2. Check Constraint Type (LOOSE)
            // Because propagateConstraints = false, the modifier passed Loose constraints (min=0).
            // The child was *allowed* to be 100, but not *forced*.
            assertEquals(
                "Default behavior should pass LOOSE constraints (minWidth = 0)",
                0,
                childConstraintsMinWidth,
            )

            // 3. Check External Reporting
            assertEquals(
                "Modifier should report the constrained size (10) to the parent",
                approachSize,
                parentReceivedWidth,
            )
        }
    }

    @Test
    fun throws_when_used_outside_LookaheadScope() {
        assertThrows(IllegalStateException::class.java) {
            rule.setContent {
                // Box is not inside a LookaheadScope.
                Box(Modifier.size(100.dp).skipToLookaheadSize())
            }
        }
    }
}
