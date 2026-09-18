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

package com.android.systemui.common.ui.compose.gestures

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class StrictTapTest : SysuiTestCase() {

    @get:Rule val composeTestRule = createComposeRule(StandardTestDispatcher())

    private var tapped = false
    private var longPressed = false
    private var lastOffset = Offset.Unspecified

    private var lastUpEventConsumed = false
    private var consumeEventsExternally = false

    @Before
    fun setUp() {
        tapped = false
        longPressed = false
        lastOffset = Offset.Unspecified
        lastUpEventConsumed = false
        consumeEventsExternally = false
    }

    @Test
    fun tap_triggersOnTap_andConsumesEvent() {
        setContent()

        composeTestRule.onNodeWithTag(TEST_TAG).performTouchInput {
            down(Offset(50f, 50f))
            up()
        }

        assertThat(tapped).isTrue()
        assertThat(lastUpEventConsumed).isTrue()
    }

    @Test
    fun longPress_triggersOnLongPress_andConsumesEvent() {
        setContent()

        composeTestRule.onNodeWithTag(TEST_TAG).performTouchInput { down(Offset(50f, 50f)) }

        composeTestRule.mainClock.advanceTimeBy(LONG_PRESS_TIMEOUT + 10)
        assertThat(longPressed).isTrue()

        composeTestRule.onNodeWithTag(TEST_TAG).performTouchInput { up() }
        assertThat(lastUpEventConsumed).isTrue()
    }

    @Test
    fun exceedingTouchSlop_cancelsTap() {
        setContent()

        composeTestRule.onNodeWithTag(TEST_TAG).performTouchInput {
            down(Offset(50f, 50f))
            moveTo(Offset(50f, 50f + TOUCH_SLOP + 1f))
            up()
        }

        assertThat(tapped).isFalse()
        assertThat(lastUpEventConsumed).isFalse()
    }

    @Test
    fun exceedingTouchSlop_cancelsLongPress() {
        setContent()

        composeTestRule.onNodeWithTag(TEST_TAG).performTouchInput {
            down(Offset(50f, 50f))
            moveTo(Offset(50f, 50f + TOUCH_SLOP + 1f))
        }

        composeTestRule.mainClock.advanceTimeBy(LONG_PRESS_TIMEOUT + 10)

        assertThat(longPressed).isFalse()
    }

    @Test
    fun movementWithinSlop_stillTriggersTap() {
        setContent()

        composeTestRule.onNodeWithTag(TEST_TAG).performTouchInput {
            down(Offset(50f, 50f))
            moveTo(Offset(50f, 50f + TOUCH_SLOP - 1f))
            up()
        }

        assertThat(tapped).isTrue()
    }

    @Test
    fun externalConsumption_cancelsTap() {
        consumeEventsExternally = true
        setContent()

        composeTestRule.onNodeWithTag(TEST_TAG).performTouchInput {
            down(Offset(50f, 50f))
            up()
        }

        assertThat(tapped).isFalse()
    }

    private fun setContent() {
        composeTestRule.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(1f),
                LocalViewConfiguration provides TEST_VIEW_CONFIG,
            ) {
                Box(
                    modifier =
                        Modifier.fillMaxSize()
                            .testTag(TEST_TAG)
                            // A "spy" pointer input to check consumption in the Final pass
                            // and optionally simulate external consumption in the Initial pass
                            .pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) {
                                        val event = awaitPointerEvent(PointerEventPass.Initial)
                                        if (consumeEventsExternally) {
                                            event.changes.forEach { it.consume() }
                                        }

                                        val finalEvent = awaitPointerEvent(PointerEventPass.Final)
                                        val upChange =
                                            finalEvent.changes.find {
                                                it.pressed == false && it.previousPressed == true
                                            }
                                        if (upChange != null) {
                                            lastUpEventConsumed = upChange.isConsumed
                                        }
                                    }
                                }
                            }
                            .pointerInput(Unit) {
                                detectTapGesturesStrict(
                                    onTap = {
                                        tapped = true
                                        lastOffset = it
                                    },
                                    onLongPress = {
                                        longPressed = true
                                        lastOffset = it
                                    },
                                )
                            }
                )
            }
        }
    }

    companion object {
        private const val TEST_TAG = "StrictTapTarget"
        private const val TOUCH_SLOP = 8f
        private const val LONG_PRESS_TIMEOUT = 500L

        private val TEST_VIEW_CONFIG =
            object : ViewConfiguration {
                override val doubleTapMinTimeMillis = 40L
                override val doubleTapTimeoutMillis = 300L
                override val longPressTimeoutMillis = LONG_PRESS_TIMEOUT
                override val minimumTouchTargetSize = DpSize.Zero
                override val touchSlop = TOUCH_SLOP
            }
    }
}
