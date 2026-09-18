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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OnUnplacedTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun onUnplaced_isNotCalled_whenInitiallyUnplaced() {
        var unplacedCallCount = 0

        // When the child was never placed.
        rule.setContent {
            Layout(content = { Box(Modifier.onUnplaced { unplacedCallCount++ }) }) {
                measurables,
                constraints ->
                val placeable = measurables.first().measure(constraints)
                layout(placeable.width, placeable.height) {
                    // The child is measured but nothing is placed.
                }
            }
        }

        // Then onUnplaced should NOT be called.
        rule.runOnIdle { assertThat(unplacedCallCount).isEqualTo(0) }
    }

    @Test
    fun onUnplaced_isCalled_whenComposedButNotPlaced() {
        var unplacedCallCount = 0
        var placeChild by mutableStateOf(true)

        // Given, the item is composed and placed.
        rule.setContent {
            Layout(content = { Box(Modifier.onUnplaced { unplacedCallCount++ }) }) {
                measurables,
                constraints ->
                val placeable = measurables.first().measure(constraints)
                layout(placeable.width, placeable.height) {
                    if (placeChild) {
                        placeable.placeRelative(0, 0)
                    }
                    // When placeChild is false, the child is measured but not placed.
                }
            }
        }
        // onUnplaced should NOT be called.
        rule.runOnIdle { assertThat(unplacedCallCount).isEqualTo(0) }

        // When the item is NOT placed anymore.
        placeChild = false

        // Then onUnplaced should be called.
        rule.runOnIdle { assertThat(unplacedCallCount).isEqualTo(1) }

        // When the item is placed again.
        placeChild = true
        rule.runOnIdle { assertThat(unplacedCallCount).isEqualTo(1) }

        // When the item is NOT placed anymore.
        placeChild = false

        // Then onUnplaced should be called.
        rule.runOnIdle { assertThat(unplacedCallCount).isEqualTo(2) }
    }

    @Test
    fun onUnplaced_usesLatestCallback_afterRecomposition() {
        var callback1Called = false
        var callback2Called = false
        val callback1 = { callback1Called = true }
        val callback2 = { callback2Called = true }

        var placeChild by mutableStateOf(true)
        var currentCallback by mutableStateOf(callback1)

        // Given, the item is composed and placed.
        rule.setContent {
            Layout(content = { Box(Modifier.onUnplaced(currentCallback)) }) {
                measurables,
                constraints ->
                val placeable = measurables.first().measure(constraints)
                layout(placeable.width, placeable.height) {
                    if (placeChild) {
                        placeable.placeRelative(0, 0)
                    }
                    // When placeChild is false, the child is measured but not placed.
                }
            }
        }
        rule.runOnIdle {
            assertThat(callback1Called).isFalse()
            assertThat(callback2Called).isFalse()
        }

        // When recompose with a new callback.
        currentCallback = callback2
        rule.runOnIdle {
            // Sanity check: nothing should have been called yet.
            assertThat(callback1Called).isFalse()
            assertThat(callback2Called).isFalse()
        }

        // And un-place the item.
        placeChild = false

        // Then: The latest callback was invoked, and the old one was not.
        rule.runOnIdle {
            assertThat(callback1Called).isFalse()
            assertThat(callback2Called).isTrue()
        }
    }

    @Test
    fun onUnplaced_isCalled_whenNodeLeavesComposition() {
        var unplacedCallCount = 0
        var show by mutableStateOf(true)

        // Given, the item is composed and placed.
        rule.setContent {
            if (show) {
                Box(modifier = Modifier.onUnplaced { unplacedCallCount++ })
            }
        }
        // onUnplaced should NOT be called.
        rule.runOnIdle { assertThat(unplacedCallCount).isEqualTo(0) }

        // When the item is NOT composed anymore.
        show = false

        // Then onUnplaced should be called.
        rule.runOnIdle { assertThat(unplacedCallCount).isEqualTo(1) }

        // When the item is composed again.
        show = true
        rule.runOnIdle { assertThat(unplacedCallCount).isEqualTo(1) }

        // When the item is NOT composed anymore.
        show = false

        // Then onUnplaced should be called.
        rule.runOnIdle { assertThat(unplacedCallCount).isEqualTo(2) }
    }
}
