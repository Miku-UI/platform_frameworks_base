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

package com.android.systemui.scene.ui.compose

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.unit.IntSize
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class PointerStateAssertionNodeTest : SysuiTestCase() {
    private val errors = mutableListOf<String>()
    private val underTest =
        PointerStateAssertionNode(sharePointers = true, onError = { errors.add(it) })

    @Test
    fun validSingleTap() {
        sendPointerEvent(down(1))
        assertThat(errors).isEmpty()

        sendPointerEvent(move(1))
        assertThat(errors).isEmpty()

        sendPointerEvent(up(1))
        assertThat(errors).isEmpty()
    }

    @Test
    fun validMultiTouch() {
        sendPointerEvent(down(1))
        assertThat(errors).isEmpty()

        // P1 is still down, P2 is new down.
        sendPointerEvent(move(1), down(2))
        assertThat(errors).isEmpty()

        // P1 goes up, P2 is still down.
        sendPointerEvent(up(1), move(2))
        assertThat(errors).isEmpty()

        sendPointerEvent(up(2))
        assertThat(errors).isEmpty()
    }

    @Test
    fun ghostTouch_DuplicateDown() {
        sendPointerEvent(down(1))
        assertThat(errors).isEmpty()

        sendPointerEvent(down(1))
        assertThat(errors)
            .containsExactly("cannot add PointerId(value=1): already in pointersDown: [1]")
    }

    @Test
    fun orphanedUp_UpWithoutDown() {
        sendPointerEvent(up(1))
        assertThat(errors)
            .containsExactly("cannot remove PointerId(value=1): not in pointersDown: []")
    }

    @Test
    fun stateDesync_ReceiveMove_ForUnknownPointer() {
        sendPointerEvent(move(1))
        assertThat(errors).containsExactly("desyncs 0 != 1 pointersDown: []")
    }

    @Test
    fun stateDesync_TrackedPointer_DisappearsWithoutUp() {
        sendPointerEvent(down(1), down(2))

        sendPointerEvent(move(1))
        assertThat(errors).containsExactly("desyncs 2 != 1 pointersDown: [1, 2]")
    }

    @Test
    fun onCancel_ClearsState() {
        sendPointerEvent(down(1))
        assertThat(errors).isEmpty()

        underTest.onCancelPointerInput()
        assertThat(errors).isEmpty()

        // Same pointer goes DOWN again (note: without onCancelPointerInput this is an error).
        sendPointerEvent(down(1))
        assertThat(errors).isEmpty()
    }

    private fun sendPointerEvent(vararg changes: PointerInputChange) {
        underTest.onPointerEvent(
            PointerEvent(changes.toList()),
            PointerEventPass.Initial,
            IntSize.Zero,
        )
    }

    private companion object {
        private fun change(id: Long, prevPressed: Boolean, pressed: Boolean): PointerInputChange {
            return PointerInputChange(
                id = PointerId(id),
                uptimeMillis = 0L,
                position = Offset.Zero,
                pressed = pressed,
                previousPressed = prevPressed,
                previousUptimeMillis = 0L,
                previousPosition = Offset.Zero,
                isInitiallyConsumed = false,
            )
        }

        fun down(id: Long) = change(id = id, prevPressed = false, pressed = true)

        fun move(id: Long) = change(id = id, prevPressed = true, pressed = true)

        fun up(id: Long) = change(id = id, prevPressed = true, pressed = false)
    }
}
