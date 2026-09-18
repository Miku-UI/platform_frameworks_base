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

package com.android.wm.shell.transition

import android.app.ActivityManager.RunningTaskInfo
import android.os.Binder
import android.view.SurfaceControl
import android.view.SurfaceControl.Transaction
import android.view.WindowManager.TRANSIT_CHANGE
import android.view.WindowManager.TRANSIT_OPEN
import android.view.WindowManager.TRANSIT_TO_FRONT
import android.window.TransitionInfo.FLAG_IS_DISPLAY
import android.window.TransitionInfo.FLAG_MOVED_TO_TOP
import android.window.WindowContainerToken
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.testing.wm.util.TransitionInfoBuilder
import com.android.wm.shell.ShellTestCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify

/**
 * Tests for the transition leash manager that sets up and tears down inner and handler-specific
 * leashes.
 *
 * Build/Install/Run: atest WMShellUnitTests:TransitionLeashManagerTest
 */
@SmallTest
@RunWith(AndroidJUnit4::class)
class TransitionLeashManagerTest : ShellTestCase() {
    private val underTest = TransitionLeashManager()

    @Test
    fun testHandlerLeashLifecycle() {
        val independentTaskInfo = RunningTaskInfo().apply { token = mock<WindowContainerToken>() }
        val dependentTaskInfo = RunningTaskInfo().apply { token = mock<WindowContainerToken>() }
        val info =
            TransitionInfoBuilder(TRANSIT_OPEN)
                .addChange(TRANSIT_CHANGE)
                .addChange(TRANSIT_OPEN, 0 /* flags */, independentTaskInfo)
                .addChange(TRANSIT_CHANGE)
                .addChange(TRANSIT_CHANGE, 0 /* flags */, dependentTaskInfo)
                .build()

        val displayChange = info.changes[0]
        val displayChangeSurface = displayChange.leash
        displayChange.flags = displayChange.flags or FLAG_IS_DISPLAY or FLAG_MOVED_TO_TOP

        val independentChange = info.changes[1]
        val independentChangeSurface = independentChange.leash

        val dependentChange = info.changes[2]
        val dependentChangeSurface = dependentChange.leash
        dependentChange.parent = independentChange.container

        val dependentTaskChange = info.changes[3]
        val dependentTaskChangeSurface = dependentTaskChange.leash
        dependentTaskChange.parent = displayChange.container

        val mergeIndependentTaskInfo =
            RunningTaskInfo().apply { token = mock<WindowContainerToken>() }
        val mergeInfo =
            TransitionInfoBuilder(TRANSIT_TO_FRONT)
                .addChange(independentChange)
                .addChange(dependentTaskChange)
                .addChange(TRANSIT_TO_FRONT, 0 /* flags */, mergeIndependentTaskInfo)
                .build()

        val mergeIndependentChange = mergeInfo.changes[2]
        val mergeIndependentChangeSurface = mergeIndependentChange.leash

        val token = Binder()
        val mergeToken = Binder()
        val transaction = mock<Transaction>()

        underTest.setUpLeashes(token, info, transaction)
        // The independent change and the dependent task should have a one-off leash.
        val leashCaptor = argumentCaptor<SurfaceControl>()
        verify(transaction, never()).reparent(eq(dependentChangeSurface), any())
        assertEquals(dependentChangeSurface, dependentChange.leash)
        verify(transaction, never()).reparent(eq(displayChangeSurface), any())
        assertEquals(displayChangeSurface, displayChange.leash)
        verify(transaction).reparent(eq(dependentTaskChangeSurface), leashCaptor.capture())
        val dependentLeash = leashCaptor.firstValue
        verify(transaction).reparent(eq(dependentLeash), eq(info.getRoot(0).leash))
        assertEquals(dependentLeash, dependentTaskChange.leash)
        verify(transaction).reparent(eq(independentChangeSurface), leashCaptor.capture())
        val independentLeash = leashCaptor.secondValue
        verify(transaction).reparent(eq(independentLeash), eq(info.getRoot(0).leash))
        assertEquals(independentLeash, independentChange.leash)
        reset(transaction)

        underTest.setUpLeashes(
            mergeToken,
            mergeInfo,
            transaction,
            setOf(independentChange, dependentTaskChange),
        )
        // Only the new independent change should get a new a one-off leash.
        verify(transaction, never()).reparent(eq(independentLeash), any())
        assertEquals(independentLeash, independentChange.leash)
        verify(transaction, never()).reparent(eq(dependentLeash), any())
        assertEquals(dependentLeash, dependentTaskChange.leash)
        verify(transaction).reparent(eq(mergeIndependentChangeSurface), leashCaptor.capture())
        val mergeIndependentLeash = leashCaptor.thirdValue
        verify(transaction).reparent(eq(mergeIndependentLeash), eq(mergeInfo.getRoot(0).leash))
        assertEquals(mergeIndependentLeash, mergeIndependentChange.leash)
        reset(transaction)

        underTest.detachLeashes(token, info, transaction)
        // The change with a parent has its leash reparented to it. The independent change and the
        // dependent task have their original surfaces reparented to the root. All changes are
        // updated with the original surfaces.
        verify(transaction).reparent(eq(dependentChangeSurface), eq(independentChangeSurface))
        assertEquals(dependentChangeSurface, dependentChange.leash)
        verify(transaction, never()).reparent(eq(displayChangeSurface), any())
        assertEquals(displayChangeSurface, displayChange.leash)
        verify(transaction, never()).reparent(eq(dependentLeash), any())
        verify(transaction).reparent(eq(dependentTaskChangeSurface), eq(info.getRoot(0).leash))
        assertEquals(dependentTaskChangeSurface, dependentTaskChange.leash)
        verify(transaction, never()).reparent(eq(independentLeash), any())
        verify(transaction).reparent(eq(independentChangeSurface), eq(info.getRoot(0).leash))
        assertEquals(independentChangeSurface, independentChange.leash)
        reset(transaction)

        underTest.detachLeashes(mergeToken, mergeInfo, transaction)
        // Only the new independent change has its original surface reparented to the root.
        verify(transaction, never()).reparent(eq(independentChangeSurface), any())
        assertEquals(independentChangeSurface, independentChange.leash)
        verify(transaction, never()).reparent(eq(dependentChangeSurface), any())
        assertEquals(dependentChangeSurface, dependentChange.leash)
        verify(transaction, never()).reparent(eq(mergeIndependentLeash), any())
        verify(transaction)
            .reparent(eq(mergeIndependentChangeSurface), eq(mergeInfo.getRoot(0).leash))
        assertEquals(mergeIndependentChangeSurface, mergeIndependentChange.leash)
        reset(transaction)

        underTest.cleanUp(token)
        assertFalse(dependentLeash.isValid)
        assertFalse(independentLeash.isValid)
        assertTrue(mergeIndependentLeash.isValid)

        underTest.cleanUp(mergeToken)
        assertFalse(mergeIndependentLeash.isValid)
    }
}
