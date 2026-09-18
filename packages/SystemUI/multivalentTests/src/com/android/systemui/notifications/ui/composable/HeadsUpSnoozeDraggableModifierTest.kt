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

package com.android.systemui.notifications.ui.composable

import androidx.compose.ui.geometry.Offset
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import org.junit.Test
import org.junit.runner.RunWith
import platform.test.motion.compose.runMonotonicClockTest

@SmallTest
@RunWith(AndroidJUnit4::class)
class HeadsUpSnoozeDraggableModifierTest : SysuiTestCase() {

    private val minOffset = -500f
    private var onSnoozeCalled = false
    private var isSnoozing = false

    @Test
    fun snoozeDisabled_noSnoozing() = runMonotonicClockTest {
        val underTest = createSnoozeController(this, enabled = false)

        assertThat(underTest.shouldConsumeNestedPreScroll(sign = -1f)).isFalse()
    }

    @Test
    fun onDragDown_noSnoozing() = runMonotonicClockTest {
        val underTest = createSnoozeController(this)

        // At rest, offset is 0. sign > 0 means downward drag.
        assertThat(underTest.shouldConsumeNestedPreScroll(sign = 1f)).isFalse()
    }

    @Test
    fun onDragUp_snoozeStarts() = runMonotonicClockTest {
        val underTest = createSnoozeController(this)

        // At rest, offset is 0. sign < 0 means upward drag.
        assertThat(underTest.shouldConsumeNestedPreScroll(sign = -1f)).isTrue()
    }

    @Test
    fun snoozeStarted_onDirectionChange_snoozeContinues() = runMonotonicClockTest {
        val underTest = createSnoozeController(this)

        // In mid-snooze
        underTest.snapTo(-100f)
        testScheduler.advanceUntilIdle()

        // Consumes both upward AND downward drags so the offset can be updated
        assertThat(underTest.shouldConsumeNestedPreScroll(sign = -1f)).isTrue()
        assertThat(underTest.shouldConsumeNestedPreScroll(sign = 1f)).isTrue()
    }

    @Test
    fun dragClampedAtMinOffset() = runMonotonicClockTest {
        val underTest = createSnoozeController(this)

        underTest.snapTo(-100f)
        testScheduler.advanceUntilIdle()

        // Consumes excess drag beyond minOffset limits
        // delta is negative to go up.
        underTest.onDrag(-1000f) // delta -1000. total would be -1100f
        testScheduler.advanceUntilIdle()

        assertThat(underTest.offset).isEqualTo(minOffset)
    }

    @Test
    fun dragClampedAtZero() = runMonotonicClockTest {
        val underTest = createSnoozeController(this)

        underTest.snapTo(-100f)
        testScheduler.advanceUntilIdle()

        // Consumes downward past 0
        underTest.onDrag(200f) // delta 200. total would be +100f
        testScheduler.advanceUntilIdle()

        assertThat(underTest.offset).isEqualTo(0f)
    }

    @Test
    fun upwardFling_triggersSnooze() = runMonotonicClockTest {
        val underTest = createSnoozeController(this)

        underTest.snapTo(-100f)
        testScheduler.advanceUntilIdle()

        // upward fling (negative velocity) exceeding threshold
        underTest.onDragStopped(-1500f, awaitFling = {})
        testScheduler.advanceUntilIdle()

        assertThat(underTest.offset).isEqualTo(minOffset)
        assertThat(onSnoozeCalled).isTrue()
    }

    @Test
    fun downwardFling_triggersCancel() = runMonotonicClockTest {
        val underTest = createSnoozeController(this)

        underTest.snapTo(-400f)
        testScheduler.advanceUntilIdle()

        // downward fling (positive velocity) exceeding threshold
        underTest.onDragStopped(1500f, awaitFling = {})
        testScheduler.advanceUntilIdle()

        assertThat(underTest.offset).isEqualTo(0f)
        assertThat(onSnoozeCalled).isFalse()
    }

    @Test
    fun directionOverridesDistance_triggersCancel() = runMonotonicClockTest {
        val underTest = createSnoozeController(this)

        // offset < minOffset / 2 (-250). past midpoint.
        underTest.snapTo(-350f)
        testScheduler.advanceUntilIdle()

        // but flung Downward
        underTest.onDragStopped(1500f, awaitFling = {})
        testScheduler.advanceUntilIdle()

        // Cancelled because velocity prioritises cancel over distance
        assertThat(underTest.offset).isEqualTo(0f)
    }

    @Test
    fun draggedFarEnough_triggersSnooze() = runMonotonicClockTest {
        val underTest = createSnoozeController(this)

        // low velocity (0f), offset < minOffset / 2
        underTest.snapTo(-300f)

        testScheduler.advanceUntilIdle()

        underTest.onDragStopped(0f, awaitFling = {})
        testScheduler.advanceUntilIdle()

        assertThat(underTest.offset).isEqualTo(minOffset)
    }

    @Test
    fun onDragStarts_isSnoozing_true() = runMonotonicClockTest {
        val underTest = createSnoozeController(this)

        underTest.onDragStarted(Offset.Zero, sign = -1f, pointersDown = 1, pointerType = null)
        assertThat(isSnoozing).isTrue()
    }

    /** Creates the object under test. */
    private fun createSnoozeController(testScope: CoroutineScope, enabled: Boolean = true) =
        HeadsUpSnoozeDragController(
            minOffset = { minOffset },
            isEnabled = { enabled },
            onSnoozed = { onSnoozeCalled = true },
            onSnoozeProgressChanged = { isSnoozing = true },
            scope = testScope,
            velocityThresholdPx = 1000f,
        )
}
