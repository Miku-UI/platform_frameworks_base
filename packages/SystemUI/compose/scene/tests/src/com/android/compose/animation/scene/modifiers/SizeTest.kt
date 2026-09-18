/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.compose.animation.scene.modifiers

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.compose.animation.scene.TestElements
import com.android.compose.animation.scene.testTransition
import com.android.compose.modifiers.skipToLookaheadSize
import com.android.compose.test.assertSizeIsEqualTo
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SizeTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun noResizeDuringTransitions() {
        // The tag for the parent of the shared Foo element.
        val parentTag = "parent"

        rule.testTransition(
            fromSceneContent = { Box(Modifier.element(TestElements.Foo).size(100.dp)) },
            toSceneContent = {
                // Don't resize the parent of Foo during transitions so that it's always the same
                // size as when there is no transition (200dp).
                Box(Modifier.testTag(parentTag).noResizeDuringTransitions()) {
                    Box(Modifier.element(TestElements.Foo).size(200.dp))
                }
            },
            transition = { spec = tween(durationMillis = 4 * 16, easing = LinearEasing) },
        ) {
            at(16) { rule.onNodeWithTag(parentTag).assertSizeIsEqualTo(200.dp, 200.dp) }
            at(32) { rule.onNodeWithTag(parentTag).assertSizeIsEqualTo(200.dp, 200.dp) }
            at(48) { rule.onNodeWithTag(parentTag).assertSizeIsEqualTo(200.dp, 200.dp) }
            after { rule.onNodeWithTag(parentTag).assertSizeIsEqualTo(200.dp, 200.dp) }
        }
    }

    @Test
    fun skipToLookahead_snaps_parent_but_animates_child_node() {
        val parentTag = "parent"
        val childTag = "child"
        val elementTag = "element"

        rule.testTransition(
            fromSceneContent = { Box(Modifier.element(TestElements.Foo).size(100.dp)) },
            toSceneContent = {
                // Parent: Apply skipToLookaheadSize().
                // This forces the parent container to layout at its final target size (200.dp)
                // immediately, skipping the parent's size animation.
                Box(
                    Modifier.testTag(parentTag).skipToLookaheadSize(),
                    propagateMinConstraints = true,
                ) {
                    // Child:
                    // Because the modifier provides loose constraints (allows minWidth/Height = 0),
                    // this child is NOT forced to fill the 200dp parent immediately.
                    // It animates its size normally from 100dp to 200dp.
                    Box(Modifier.testTag(childTag), propagateMinConstraints = true) {
                        Box(Modifier.testTag(elementTag).element(TestElements.Foo).size(200.dp))
                    }
                }
            },
            // 4 frames at 16ms each = 64ms total duration.
            transition = { spec = tween(durationMillis = 4 * 16, easing = LinearEasing) },
        ) {
            // Interpolation: 100dp -> 200dp over 64ms.
            // Growth rate: 25dp per 16ms frame.

            at(16) {
                // Parent is snapped to 200dp.
                rule.onNodeWithTag(parentTag).assertSizeIsEqualTo(200.dp, 200.dp)
                // Child interpolates: 100 + 25 = 125dp.
                rule.onNodeWithTag(childTag).assertSizeIsEqualTo(125.dp, 125.dp)
                // Element interpolates: 100 + 25 = 125dp.
                rule.onNodeWithTag(elementTag).assertSizeIsEqualTo(125.dp, 125.dp)
            }
            at(32) {
                rule.onNodeWithTag(parentTag).assertSizeIsEqualTo(200.dp, 200.dp)
                rule.onNodeWithTag(childTag).assertSizeIsEqualTo(150.dp, 150.dp)
                rule.onNodeWithTag(elementTag).assertSizeIsEqualTo(150.dp, 150.dp)
            }
            at(48) {
                rule.onNodeWithTag(parentTag).assertSizeIsEqualTo(200.dp, 200.dp)
                rule.onNodeWithTag(childTag).assertSizeIsEqualTo(175.dp, 175.dp)
                rule.onNodeWithTag(elementTag).assertSizeIsEqualTo(175.dp, 175.dp)
            }
            after {
                rule.onNodeWithTag(parentTag).assertSizeIsEqualTo(200.dp, 200.dp)
                rule.onNodeWithTag(childTag).assertSizeIsEqualTo(200.dp, 200.dp)
                rule.onNodeWithTag(elementTag).assertSizeIsEqualTo(200.dp, 200.dp)
            }
        }
    }
}
