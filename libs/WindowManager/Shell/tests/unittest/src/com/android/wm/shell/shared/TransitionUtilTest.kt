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

package com.android.wm.shell.shared

import android.app.WindowConfiguration.ACTIVITY_TYPE_HOME
import android.app.WindowConfiguration.ACTIVITY_TYPE_STANDARD
import android.graphics.Rect
import android.view.SurfaceControl
import android.view.SurfaceControl.Transaction
import android.view.WindowManager.TRANSIT_CHANGE
import android.view.WindowManager.TRANSIT_CLOSE
import android.view.WindowManager.TRANSIT_OPEN
import android.window.TransitionInfo
import android.window.TransitionInfo.FLAG_CHANGED_INTERACTIVE
import android.window.TransitionInfo.FLAG_IS_DISPLAY
import android.window.TransitionInfo.FLAG_IS_WALLPAPER
import android.window.TransitionInfo.FLAG_MOVED_TO_TOP
import android.window.TransitionInfo.FLAG_NO_ANIMATION
import android.window.TransitionInfo.FLAG_SHOW_WALLPAPER
import android.window.TransitionInfo.FLAG_TRANSLUCENT
import android.window.WindowContainerToken
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.testing.wm.util.TransitionInfoBuilder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

/**
 * Tests for the transition leash manager that sets up and tears down inner and handler-specific
 * leashes.
 *
 * Build/Install/Run: atest WMShellUnitTests:TransitionUtilTest
 */
@SmallTest
@RunWith(AndroidJUnit4::class)
class TransitionUtilTest {

    @Test
    fun testCalculateAnimLayer() {
        val change = TransitionInfo.Change(mock<WindowContainerToken>(), mock<SurfaceControl>())

        // Opening transition type, opening mode.
        change.mode = TRANSIT_OPEN
        var result =
            TransitionUtil.calculateAnimLayer(
                change,
                3 /* order */,
                6 /* numChanges */,
                TRANSIT_OPEN,
            )
        assertEquals(expected = 10, result)

        // Opening transition type, closing mode.
        change.mode = TRANSIT_CLOSE
        result =
            TransitionUtil.calculateAnimLayer(
                change,
                3 /* order */,
                6 /* numChanges */,
                TRANSIT_OPEN,
            )
        assertEquals(expected = 4, result)

        // Opening transition type, other mode.
        change.mode = TRANSIT_CHANGE
        result =
            TransitionUtil.calculateAnimLayer(
                change,
                3 /* order */,
                6 /* numChanges */,
                TRANSIT_OPEN,
            )
        assertEquals(expected = 10, result)

        // Closing transition type, opening mode.
        change.mode = TRANSIT_OPEN
        result =
            TransitionUtil.calculateAnimLayer(
                change,
                3 /* order */,
                6 /* numChanges */,
                TRANSIT_CLOSE,
            )
        assertEquals(expected = 4, result)

        // Closing transition type, closing mode.
        change.mode = TRANSIT_CLOSE
        result =
            TransitionUtil.calculateAnimLayer(
                change,
                3 /* order */,
                6 /* numChanges */,
                TRANSIT_CLOSE,
            )
        assertEquals(expected = 10, result)

        // Closing transition type, other mode.
        change.mode = TRANSIT_CHANGE
        result =
            TransitionUtil.calculateAnimLayer(
                change,
                3 /* order */,
                6 /* numChanges */,
                TRANSIT_CLOSE,
            )
        assertEquals(expected = 4, result)

        // Other transition type, opening mode.
        change.mode = TRANSIT_CLOSE
        result =
            TransitionUtil.calculateAnimLayer(
                change,
                3 /* order */,
                6 /* numChanges */,
                TRANSIT_CHANGE,
            )
        assertEquals(expected = 10, result)

        // Other transition type, closing mode.
        change.mode = TRANSIT_CLOSE
        result =
            TransitionUtil.calculateAnimLayer(
                change,
                3 /* order */,
                6 /* numChanges */,
                TRANSIT_CHANGE,
            )
        assertEquals(expected = 10, result)

        // Other transition type, other mode.
        change.mode = TRANSIT_CHANGE
        result =
            TransitionUtil.calculateAnimLayer(
                change,
                3 /* order */,
                6 /* numChanges */,
                TRANSIT_CHANGE,
            )
        assertEquals(expected = 10, result)

        // Other transition type, closing wallpaper.
        change.mode = TRANSIT_CLOSE
        change.flags = change.flags or FLAG_IS_WALLPAPER
        result =
            TransitionUtil.calculateAnimLayer(
                change,
                3 /* order */,
                6 /* numChanges */,
                TRANSIT_CHANGE,
            )
        assertEquals(expected = 4, result)

        if (com.android.window.flags.Flags.keepShowWallpaperOnBottom()) {
            change.flags = change.flags and FLAG_IS_WALLPAPER.inv()
            change.flags = change.flags or FLAG_SHOW_WALLPAPER
            result =
                TransitionUtil.calculateAnimLayer(
                    change,
                    3 /* order */,
                    6 /* numChanges */,
                    TRANSIT_CHANGE,
                )
            assertEquals(expected = 4, result)
        }

        // Opening transition type, other mode (order only).
        change.flags = change.flags or FLAG_MOVED_TO_TOP
        result =
            TransitionUtil.calculateAnimLayer(
                change,
                3 /* order */,
                6 /* numChanges */,
                TRANSIT_OPEN,
            )
        assertEquals(expected = 4, result)
    }

    @Test
    fun testSetUpLeashes() {
        val info =
            TransitionInfoBuilder(TRANSIT_OPEN)
                .addChange(TRANSIT_CHANGE)
                .addChange(TRANSIT_CHANGE)
                .addChange(TRANSIT_OPEN)
                .addChange(TRANSIT_OPEN)
                .build()

        val displayChange = info.changes[0]
        val displayChangeSurface = displayChange.leash
        displayChange.flags = displayChange.flags or FLAG_IS_DISPLAY or FLAG_MOVED_TO_TOP

        val dependentChange = info.changes[1]
        val dependentChangeSurface = dependentChange.leash
        dependentChange.parent = mock<WindowContainerToken>()

        val independentChangeWithoutParent = info.changes[2]
        val independentChangeWithoutParentSurface = independentChangeWithoutParent.leash

        val independentChangeWithParent = info.changes[3]
        val independentChangeWithParentSurface = independentChangeWithParent.leash
        independentChangeWithParent.parent = mock<WindowContainerToken>()
        independentChangeWithParent.lastParent = mock<WindowContainerToken>()

        val transaction = mock<Transaction>()

        TransitionUtil.setUpSurface(dependentChange, info, 0 /* order */, transaction)
        // Dependent change is skipped.
        verify(transaction, never()).reparent(eq(dependentChangeSurface), any())
        verify(transaction, never()).setPosition(eq(dependentChangeSurface), any(), any())
        verify(transaction, never()).setLayer(eq(dependentChangeSurface), any())

        TransitionUtil.setUpSurface(displayChange, info, 0 /* order */, transaction)
        // Order-only display-level change is skipped.
        verify(transaction, never()).reparent(eq(displayChangeSurface), any())
        verify(transaction, never()).setPosition(eq(displayChangeSurface), any(), any())
        verify(transaction, never()).setLayer(eq(dependentChangeSurface), any())

        TransitionUtil.setUpSurface(
            independentChangeWithoutParent,
            info,
            0 /* order */,
            transaction,
        )
        // Independent change without parent is fully set up.
        verify(transaction)
            .reparent(eq(independentChangeWithoutParentSurface), eq(info.getRoot(0).leash))
        verify(transaction).setPosition(eq(independentChangeWithoutParentSurface), any(), any())
        verify(transaction).setLayer(eq(independentChangeWithoutParentSurface), any())

        TransitionUtil.setUpSurface(independentChangeWithParent, info, 0 /* order */, transaction)
        // Independent change with parent only has its layer.
        verify(transaction, never()).reparent(eq(independentChangeWithParentSurface), any())
        verify(transaction, never())
            .setPosition(eq(independentChangeWithParentSurface), any(), any())
        verify(transaction).setLayer(eq(independentChangeWithParentSurface), any())
    }

    @Test
    fun testIsHomeTransitionEndingOnDisplay() {
        val displayId = 0

        // Home task ending on the display
        assertTrue(
            TransitionUtil.isHomeTransitionEndingOnDisplay(
                createTransitionInfoWithTask(ACTIVITY_TYPE_HOME, displayId),
                displayId,
            )
        )

        // Home task ending on a different display
        assertFalse(
            TransitionUtil.isHomeTransitionEndingOnDisplay(
                createTransitionInfoWithTask(ACTIVITY_TYPE_HOME, displayId + 1),
                displayId,
            )
        )

        // Standard task ending on the display
        assertFalse(
            TransitionUtil.isHomeTransitionEndingOnDisplay(
                createTransitionInfoWithTask(ACTIVITY_TYPE_STANDARD, displayId),
                displayId,
            )
        )

        // null TransitionInfo
        assertFalse(TransitionUtil.isHomeTransitionEndingOnDisplay(null /* info */, displayId))
    }

    @Test
    fun testIsAllNoAnimation_noAnimationAndInteractivityChanged_noAnimation() {
        val info =
            TransitionInfoBuilder(TRANSIT_OPEN)
                .addChange(TRANSIT_CHANGE, FLAG_NO_ANIMATION)
                .addChange(TRANSIT_CHANGE, FLAG_CHANGED_INTERACTIVE)
                .build()

        assertTrue(TransitionUtil.isAllNoAnimation(info))
    }

    @Test
    fun testIsAllNoAnimation_translucentChangeWithInteractivity_skipNoAnimation() {
        val info =
            TransitionInfoBuilder(TRANSIT_OPEN)
                .addChange(TRANSIT_CHANGE, FLAG_NO_ANIMATION)
                .addChange(TRANSIT_CHANGE, FLAG_TRANSLUCENT and FLAG_CHANGED_INTERACTIVE)
                .build()

        assertFalse(TransitionUtil.isAllNoAnimation(info))
    }

    @Test
    fun testIsStationary_movedToTop_returnsTrue() {
        val change = TransitionInfo.Change(mock(), mock())
        change.mode = TRANSIT_CHANGE
        change.flags = FLAG_MOVED_TO_TOP
        assertTrue(TransitionUtil.isStationary(change))
    }

    @Test
    fun testIsStationary_interactivityChanged_returnsTrue() {
        val change = TransitionInfo.Change(mock(), mock())
        change.mode = TRANSIT_CHANGE
        change.flags = FLAG_CHANGED_INTERACTIVE
        assertTrue(TransitionUtil.isStationary(change))
    }

    @Test
    fun testIsStationary_changedBounds_returnsFalse() {
        val change = TransitionInfo.Change(mock(), mock())
        change.mode = TRANSIT_CHANGE
        change.flags = FLAG_CHANGED_INTERACTIVE
        change.setEndAbsBounds(Rect(10, 10, 10, 10))
        assertFalse(TransitionUtil.isStationary(change))
    }

    @Test
    fun testIsAllStationary() {
        val info =
            TransitionInfoBuilder(TRANSIT_OPEN)
                .addChange(TRANSIT_CHANGE, FLAG_MOVED_TO_TOP)
                .addChange(TRANSIT_CHANGE, FLAG_CHANGED_INTERACTIVE)
                .build()
        assertTrue(TransitionUtil.isAllStationary(info))
    }

    private fun createTransitionInfoWithTask(activityType: Int, endDisplayId: Int): TransitionInfo {
        val change = TransitionInfo.Change(mock(), mock())
        val taskInfo = mock<android.app.ActivityManager.RunningTaskInfo>()
        `when`(taskInfo.activityType).thenReturn(activityType)
        change.taskInfo = taskInfo
        change.setDisplayId(endDisplayId, endDisplayId)
        return TransitionInfo(TRANSIT_OPEN, 0).apply { addChange(change) }
    }
}
