/*
 * Copyright 2025 The Android Open Source Project
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

package android.hardware.input

import android.compat.testing.PlatformCompatChangeRule
import android.graphics.PointF
import android.platform.test.annotations.Presubmit
import com.android.server.testutils.whenever
import com.google.common.truth.Truth.assertThat
import libcore.junit.util.compat.CoreCompatChangeRule.DisableCompatChanges
import libcore.junit.util.compat.CoreCompatChangeRule.EnableCompatChanges
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoJUnitRunner

/** Tests for [VirtualMouse]. */
@Presubmit
@RunWith(MockitoJUnitRunner::class)
class VirtualMouseTest {

    @get:Rule val mockitoJunitRule = MockitoJUnit.rule()

    @get:Rule val compatChangeRule = PlatformCompatChangeRule()

    @Mock private lateinit var virtualMouseConfig: VirtualMouseConfig

    @Mock private lateinit var virtualInputDevice: IVirtualInputDevice

    private lateinit var virtualMouse: VirtualMouse

    @Before
    fun setup() {
        virtualMouse = VirtualMouse(virtualMouseConfig, virtualInputDevice)
    }

    @Test
    fun testCursorPosition_nullToNaN() {
        whenever(virtualInputDevice.getCursorPositionInLogicalDisplay()).thenReturn(null)
        whenever(virtualInputDevice.getCursorPositionInPhysicalDisplay()).thenReturn(null)

        val res = virtualMouse.cursorPosition
        assertThat(res).isEqualTo(PointF(Float.NaN, Float.NaN))
    }

    @Test
    @EnableCompatChanges(VirtualMouse.VIRTUAL_MOUSE_CURSOR_POTION_IN_LOGICAL_COORDINATES)
    fun testCursorPosition_CompatChangeEnabled() {
        whenever(virtualInputDevice.getCursorPositionInLogicalDisplay())
            .thenReturn(PointF(10f, 20f))

        val res = virtualMouse.cursorPosition
        assertThat(res).isEqualTo(PointF(10f, 20f))
    }

    @Test
    @DisableCompatChanges(VirtualMouse.VIRTUAL_MOUSE_CURSOR_POTION_IN_LOGICAL_COORDINATES)
    fun testCursorPosition_CompatChangeDisabled() {
        whenever(virtualInputDevice.getCursorPositionInPhysicalDisplay())
            .thenReturn(PointF(30f, 40f))

        val res = virtualMouse.cursorPosition
        assertThat(res).isEqualTo(PointF(30f, 40f))
    }
}
