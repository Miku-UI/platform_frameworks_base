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

package com.android.wm.shell.desktopmode

import android.graphics.Rect
import android.platform.test.annotations.EnableFlags
import android.testing.AndroidTestingRunner
import androidx.test.filters.SmallTest
import com.android.internal.R
import com.android.internal.policy.DesktopModeLaunchBoundsUtils.CascadingDirection.BOTTOM_RIGHT
import com.android.internal.policy.DesktopModeLaunchBoundsUtils.cascadeOneStep
import com.android.window.flags.Flags
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.desktopmode.DesktopTaskPosition.Center
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test class for {@link DesktopTaskPosition#cascadeWindowStepped}
 *
 * Usage: atest WMShellUnitTests:SteppedCascadingTest
 */
@SmallTest
@RunWith(AndroidTestingRunner::class)
class SteppedCascadingTest : ShellTestCase() {

    private fun getCascadingOffset(): Int =
        mContext.resources.getDimensionPixelSize(R.dimen.desktop_mode_cascading_offset)

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_STEPPED_CASCADING)
    fun cascade_fromCenter() {
        val prevBounds = getDefaultBounds()
        val prevBoundsList = listOf(prevBounds)
        val newBounds = getDefaultBounds()

        cascadeWindowStepped(
            FRAME,
            newBounds,
            prevBoundsList,
            isRememberedBounds = false,
            mContext.resources,
        )

        val expected = Rect(prevBounds)
        cascadeOneStep(expected, FRAME, BOTTOM_RIGHT, getCascadingOffset())
        assertThat(newBounds).isEqualTo(expected)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_STEPPED_CASCADING)
    fun cascade_multiStep() {
        val centerBounds = getDefaultBounds()
        val step1Bounds = Rect(centerBounds)
        cascadeOneStep(step1Bounds, FRAME, BOTTOM_RIGHT, getCascadingOffset())
        val prevBoundsList = listOf(step1Bounds, centerBounds)
        val newBounds = getDefaultBounds()

        cascadeWindowStepped(
            FRAME,
            newBounds,
            prevBoundsList,
            isRememberedBounds = false,
            mContext.resources,
        )

        val expected = Rect(step1Bounds)
        cascadeOneStep(expected, FRAME, BOTTOM_RIGHT, getCascadingOffset())
        assertThat(newBounds).isEqualTo(expected)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_STEPPED_CASCADING)
    fun cascade_brokenSequence_resetsToCenter() {
        val centerBounds = getDefaultBounds()
        val offset = getCascadingOffset()
        // Offset by something other than CASCADING_OFFSET to break the sequence
        val step1Bounds = Rect(centerBounds).apply { offset(offset * 2, offset * 2) }
        val prevBoundsList = listOf(step1Bounds, centerBounds)
        val newBounds = getDefaultBounds()

        cascadeWindowStepped(
            FRAME,
            newBounds,
            prevBoundsList,
            isRememberedBounds = false,
            mContext.resources,
        )

        val centerPos = Center.getTopLeftCoordinates(FRAME, newBounds)
        assertThat(newBounds.left).isEqualTo(centerPos.x)
        assertThat(newBounds.top).isEqualTo(centerPos.y)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_STEPPED_CASCADING)
    fun cascade_bounceRight() {
        val centerBounds = getDefaultBounds()
        val sequence = mutableListOf(centerBounds)
        var currentDir = BOTTOM_RIGHT

        // We know that for our FRAME (100, 100, 2000, 1000) and DEFAULT_WIDTH (1400),
        // it takes 4 steps from center to reach the right/bottom proximity.
        for (i in 1..4) {
            val nextBounds = Rect(sequence.first())
            currentDir = cascadeOneStep(nextBounds, FRAME, currentDir, getCascadingOffset())
            sequence.add(0, nextBounds)
        }

        val newBounds = getDefaultBounds()
        cascadeWindowStepped(
            FRAME,
            newBounds,
            sequence,
            isRememberedBounds = false,
            mContext.resources,
        )

        val newestBounds = sequence.first()
        val expected = Rect(newestBounds)
        cascadeOneStep(expected, FRAME, currentDir, getCascadingOffset())

        assertThat(newBounds).isEqualTo(expected)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_STEPPED_CASCADING)
    fun cascade_bounceBottom() {
        val centerBounds = getDefaultBounds()
        val sequence = mutableListOf(centerBounds)
        var currentDir = BOTTOM_RIGHT

        // Advance 3 steps to reach near-bottom proximity.
        for (i in 1..3) {
            val nextBounds = Rect(sequence.first())
            currentDir = cascadeOneStep(nextBounds, FRAME, currentDir, getCascadingOffset())
            sequence.add(0, nextBounds)
        }

        val newBounds = getDefaultBounds()
        cascadeWindowStepped(
            FRAME,
            newBounds,
            sequence,
            isRememberedBounds = false,
            mContext.resources,
        )

        val newestBounds = sequence.first()
        val expected = Rect(newestBounds)
        cascadeOneStep(expected, FRAME, currentDir, getCascadingOffset())

        assertThat(newBounds).isEqualTo(expected)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_REMEMBERED_BOUNDS)
    @EnableFlags(Flags.FLAG_ENABLE_STEPPED_CASCADING)
    fun cascade_maximized_staysAsIs() {
        val prev = getDefaultBounds()
        val prevBoundsList = listOf(prev)
        val dest = Rect(FRAME)
        val originalDest = Rect(dest)

        cascadeWindowStepped(
            FRAME,
            dest,
            prevBoundsList,
            isRememberedBounds = false,
            mContext.resources,
        )

        assertThat(dest).isEqualTo(originalDest)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_REMEMBERED_BOUNDS)
    @EnableFlags(Flags.FLAG_ENABLE_STEPPED_CASCADING)
    fun cascade_leftSnapped_staysAsIs() {
        val prev = getDefaultBounds()
        val prevBoundsList = listOf(prev)
        val dest = Rect(FRAME.left, FRAME.top, FRAME.left + 500, FRAME.bottom)
        val originalDest = Rect(dest)

        cascadeWindowStepped(
            FRAME,
            dest,
            prevBoundsList,
            isRememberedBounds = false,
            mContext.resources,
        )

        assertThat(dest).isEqualTo(originalDest)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_REMEMBERED_BOUNDS)
    @EnableFlags(Flags.FLAG_ENABLE_STEPPED_CASCADING)
    fun cascade_rightSnapped_staysAsIs() {
        val prev = getDefaultBounds()
        val prevBoundsList = listOf(prev)
        val dest = Rect(FRAME.right - 500, FRAME.top, FRAME.right, FRAME.bottom)
        val originalDest = Rect(dest)

        cascadeWindowStepped(
            FRAME,
            dest,
            prevBoundsList,
            isRememberedBounds = false,
            mContext.resources,
        )

        assertThat(dest).isEqualTo(originalDest)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_REMEMBERED_BOUNDS)
    @EnableFlags(Flags.FLAG_ENABLE_STEPPED_CASCADING)
    fun cascade_rememberedBounds_collision_cascades() {
        val prev = getDefaultBounds()
        val prevBoundsList = listOf(prev)
        val dest = getDefaultBounds()

        cascadeWindowStepped(
            FRAME,
            dest,
            prevBoundsList,
            isRememberedBounds = true,
            mContext.resources,
        )

        val expected = Rect(prev)
        cascadeOneStep(expected, FRAME, BOTTOM_RIGHT, getCascadingOffset())
        assertThat(dest).isEqualTo(expected)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_REMEMBERED_BOUNDS)
    @EnableFlags(Flags.FLAG_ENABLE_STEPPED_CASCADING)
    fun cascade_rememberedBounds_noCollision_staysAsIs() {
        val prev = getDefaultBounds()
        val prevBoundsList = listOf(prev)
        val offset = getCascadingOffset()
        // Offset significantly so there's no collision
        val dest = getDefaultBounds().apply { offset(offset * 3, offset * 3) }
        val originalDest = Rect(dest)

        cascadeWindowStepped(
            FRAME,
            dest,
            prevBoundsList,
            isRememberedBounds = true,
            mContext.resources,
        )

        assertThat(dest).isEqualTo(originalDest)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_REMEMBERED_BOUNDS)
    @EnableFlags(Flags.FLAG_ENABLE_STEPPED_CASCADING)
    fun cascade_rememberedBounds_outOfBounds_bounces() {
        // Positioned exactly on the bottom edge
        val prev = getDefaultBounds().apply { offsetTo(FRAME.left, FRAME.bottom - height()) }
        val prevBoundsList = listOf(prev)
        val dest = Rect(prev)

        cascadeWindowStepped(
            FRAME,
            dest,
            prevBoundsList,
            isRememberedBounds = true,
            mContext.resources,
        )

        val expected = Rect(prev)
        // Fallback direction is BOTTOM_RIGHT, which will bounce off the bottom edge.
        cascadeOneStep(expected, FRAME, BOTTOM_RIGHT, getCascadingOffset())
        assertThat(dest).isEqualTo(expected)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_REMEMBERED_BOUNDS)
    @EnableFlags(Flags.FLAG_ENABLE_STEPPED_CASCADING)
    fun cascade_rememberedBounds_maximized_staysAsIs() {
        val prev = Rect(FRAME)
        val prevBoundsList = listOf(prev)
        val dest = Rect(FRAME)
        val originalDest = Rect(dest)

        cascadeWindowStepped(
            FRAME,
            dest,
            prevBoundsList,
            isRememberedBounds = true,
            mContext.resources,
        )

        assertThat(dest).isEqualTo(originalDest)
    }

    private companion object {
        private const val DEFAULT_WIDTH = 1400
        private const val DEFAULT_HEIGHT = 600
        private val FRAME = Rect(100, 100, 2000, 1000)

        private fun getDefaultBounds(): Rect {
            return Rect(0, 0, DEFAULT_WIDTH, DEFAULT_HEIGHT).apply {
                val centerPosPrev = Center.getTopLeftCoordinates(FRAME, this)
                offsetTo(centerPosPrev.x, centerPosPrev.y)
            }
        }
    }
}
