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

package com.android.compose.gesture

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.compose.modifiers.thenIf
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NestedScrollControllerTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun nestedScrollController() {
        val state = NestedScrollControlState()
        var nestedScrollConsumesDelta = false
        rule.setContent {
            Box(
                Modifier.fillMaxSize()
                    .nestedScrollController(state)
                    .scrollable(
                        rememberScrollableState { if (nestedScrollConsumesDelta) it else 0f },
                        Orientation.Vertical,
                    )
            )
        }

        // If the nested child does not consume scrolls, then outer scrolling is allowed.
        assertThat(state.isOuterScrollAllowed).isTrue()
        nestedScrollConsumesDelta = false
        rule.onRoot().performTouchInput {
            down(topLeft)
            moveBy(Offset(0f, bottom))
        }
        assertThat(state.isOuterScrollAllowed).isTrue()
        rule.onRoot().performTouchInput { up() }

        // If the nested child consumes scrolls, then outer scrolling is disabled.
        nestedScrollConsumesDelta = true
        rule.onRoot().performTouchInput {
            down(topLeft)
            moveBy(Offset(0f, bottom))
        }
        assertThat(state.isOuterScrollAllowed).isFalse()

        // Outer scrolling is enabled again when stopping the scroll.
        rule.onRoot().performTouchInput { up() }
        assertThat(state.isOuterScrollAllowed).isTrue()
    }

    @Test
    fun nestedScrollController_detached() {
        val state = NestedScrollControlState()
        var composeNestedScroll by mutableStateOf(true)
        rule.setContent {
            val scrollableState = rememberScrollableState { it }
            Box(
                Modifier.fillMaxSize().thenIf(composeNestedScroll) {
                    Modifier.nestedScrollController(state)
                        .scrollable(scrollableState, Orientation.Vertical)
                }
            )
        }
        // The nested child consumes scrolls, so outer scrolling is disabled.
        rule.onRoot().performTouchInput {
            down(topLeft)
            moveBy(Offset(0f, bottom))
        }
        assertThat(state.isOuterScrollAllowed).isFalse()

        // Outer scrolling is enabled again when removing the controller from composition.
        composeNestedScroll = false
        rule.waitForIdle()
        assertThat(state.isOuterScrollAllowed).isTrue()
    }

    @Test
    fun supportsPreScrolls() {
        val state = NestedScrollControlState()
        rule.setContent {
            Box(
                Modifier.fillMaxSize()
                    .nestedScrollController(state)
                    .nestedScroll(
                        remember {
                            object : NestedScrollConnection {
                                override fun onPreScroll(
                                    available: Offset,
                                    source: NestedScrollSource,
                                ): Offset = available
                            }
                        }
                    )
                    .scrollable(rememberScrollableState { 0f }, Orientation.Vertical)
            )
        }

        rule.onRoot().performTouchInput {
            down(topLeft)
            moveBy(Offset(0f, bottom))
        }
        assertThat(state.isOuterScrollAllowed).isFalse()

        rule.onRoot().performTouchInput { up() }
        assertThat(state.isOuterScrollAllowed).isTrue()
    }

    @Test
    fun stateUpdate_transfersLock() {
        val state1 = NestedScrollControlState()
        val state2 = NestedScrollControlState()
        var activeState by mutableStateOf(state1)

        rule.setContent {
            Box(
                Modifier.fillMaxSize()
                    .nestedScrollController(activeState)
                    .scrollable(
                        // Always consume delta to ensure a lock is requested.
                        rememberScrollableState { it },
                        Orientation.Vertical,
                    )
            )
        }

        // Start a drag gesture to lock state1.
        rule.onRoot().performTouchInput {
            down(center)
            moveBy(Offset(0f, 100f))
        }

        // Assert: state1 is locked, state2 is free.
        assertThat(state1.isOuterScrollAllowed).isFalse()
        assertThat(state2.isOuterScrollAllowed).isTrue()

        // Hot-swap the state while the finger is still down.
        activeState = state2
        rule.waitForIdle()

        // Ensure that if the state object changes while a gesture is in progress, the lock is
        // safely transferred from the old state to the new state.
        assertThat(state1.isOuterScrollAllowed).isTrue()
        assertThat(state2.isOuterScrollAllowed).isFalse()

        // Finish the gesture.
        rule.onRoot().performTouchInput { up() }

        // Everything is released.
        assertThat(state2.isOuterScrollAllowed).isTrue()
    }

    @Test
    fun multipleStates_areLockedSimultaneously() {
        val parentState = NestedScrollControlState()
        val grandParentState = NestedScrollControlState()

        rule.setContent {
            Box(
                Modifier.fillMaxSize()
                    .nestedScrollController(listOf(parentState, grandParentState))
                    .scrollable(rememberScrollableState { it }, Orientation.Vertical)
            )
        }

        // Perform a scroll.
        rule.onRoot().performTouchInput {
            down(center)
            moveBy(Offset(0f, 100f))
        }
        assertThat(parentState.isOuterScrollAllowed).isFalse()
        assertThat(grandParentState.isOuterScrollAllowed).isFalse()

        // Release.
        rule.onRoot().performTouchInput { up() }
        assertThat(parentState.isOuterScrollAllowed).isTrue()
        assertThat(grandParentState.isOuterScrollAllowed).isTrue()
    }

    @Test
    fun sharedState_nestedHierarchy_removesLocksIndependently() {
        val sharedState = NestedScrollControlState()

        var isFirstActive by mutableStateOf(true)
        var isSecondActive by mutableStateOf(true)

        rule.setContent {
            Box(
                Modifier.fillMaxSize().thenIf(isFirstActive) {
                    Modifier.nestedScrollController(sharedState)
                }
            ) {
                Box(
                    Modifier.fillMaxSize()
                        .thenIf(isSecondActive) { Modifier.nestedScrollController(sharedState) }
                        // The Scrollable driver.
                        .scrollable(
                            state = rememberScrollableState { it }, // Consume all delta.
                            orientation = Orientation.Vertical,
                        )
                )
            }
        }

        // Start a gesture (Down + Move) to trigger the locks.
        rule.onRoot().performTouchInput {
            down(center)
            moveBy(Offset(0f, 100f))
        }

        // Both controllers should have voted to lock.
        assertThat(sharedState.isOuterScrollAllowed).isFalse()

        // Remove the second controller from composition while gesture is active.
        isSecondActive = false
        rule.waitForIdle()

        // State is still locked. Because the first controller is still attached and holding its
        // lock.
        assertThat(sharedState.isOuterScrollAllowed).isFalse()

        isFirstActive = false
        rule.waitForIdle()

        // Assert: Now that the last holder is gone, the state should be allowed.
        assertThat(sharedState.isOuterScrollAllowed).isTrue()

        // Cleanup: Lift the finger
        rule.onRoot().performTouchInput { up() }
    }
}
