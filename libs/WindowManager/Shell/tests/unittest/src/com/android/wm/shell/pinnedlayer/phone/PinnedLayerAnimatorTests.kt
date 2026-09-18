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

package com.android.wm.shell.pinnedlayer.phone

import android.animation.ValueAnimator
import android.app.ActivityManager.RunningTaskInfo
import android.graphics.Rect
import android.platform.test.annotations.EnableFlags
import android.testing.TestableLooper
import android.view.SurfaceControl
import android.window.TransitionInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.window.flags.Flags
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.transition.Transitions
import kotlin.math.roundToInt
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.atLeastOnce
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when` as whenever
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.stub

/**
 * Unit tests against [PinnedLayerAnimator]
 *
 * Build/Install/Run: atest WMShellUnitTests:PinnedLayerAnimatorTests
 */
@SmallTest
@TestableLooper.RunWithLooper
@EnableFlags(Flags.FLAG_ENABLE_INTERACTIVE_PICTURE_IN_PICTURE)
@RunWith(AndroidJUnit4::class)
class PinnedLayerAnimatorTests : ShellTestCase() {
    @Mock private lateinit var startTransaction: SurfaceControl.Transaction
    @Mock private lateinit var finishTransaction: SurfaceControl.Transaction
    @Mock private lateinit var intermediateTransaction: SurfaceControl.Transaction
    @Mock private lateinit var finishCallback: Transitions.TransitionFinishCallback
    @Mock private lateinit var change: TransitionInfo.Change

    private lateinit var testLeash: SurfaceControl
    private lateinit var task: RunningTaskInfo

    @Before
    fun setUp() {
        testLeash =
            SurfaceControl.Builder()
                .setName("PinnedLayerAnimatorTest")
                .setCallsite("PinnedLayerAnimatorTest")
                .build()
        task = createTask(0)

        whenever(change.leash).thenReturn(testLeash)
        change.stub { on { taskInfo } doReturn task }
    }

    @Test
    fun createPinAnimator_startToEnd_appliesTransactionsAndCallsFinish() {
        val startBounds = Rect(0, 0, 100, 100)
        val endBounds = Rect(100, 100, 300, 400)
        whenever(change.startAbsBounds).thenReturn(startBounds)
        whenever(change.endAbsBounds).thenReturn(endBounds)

        val animator =
            PinnedLayerAnimator.createPinAnimator(
                change,
                startTransaction,
                finishTransaction,
                finishCallback,
            )

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            animator.start()
            animator.end()
        }

        // Then onAnimationStart applies the start state to startTransaction.
        verify(startTransaction).setPosition(testLeash, 0f, 0f)
        verify(startTransaction).setWindowCrop(testLeash, 100, 100)
        verify(startTransaction).apply()

        // Then onAnimationEnd applies the final state to finishTransaction.
        verify(finishTransaction).setPosition(testLeash, 100f, 100f)
        verify(finishTransaction).setWindowCrop(testLeash, 200, 300)

        // And the finish callback is invoked.
        verify(finishCallback).onTransitionFinished(null)
    }

    @Test
    fun createPinAnimator_atHalfway_appliesIntermediateTransactions() {
        val startBounds = Rect(0, 0, 100, 100)
        val endBounds = Rect(100, 100, 300, 400) // w=200, h=300
        whenever(change.startAbsBounds).thenReturn(startBounds)
        whenever(change.endAbsBounds).thenReturn(endBounds)

        val animator =
            PinnedLayerAnimator.createPinAnimator(
                change,
                startTransaction,
                finishTransaction,
                finishCallback,
                /* sctFactory = */ { intermediateTransaction },
            ) as ValueAnimator // cast to be able to call setCurrentPlayTime

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            animator.start()
            animator.setCurrentPlayTime(150) // Halfway through 300ms duration
            animator.pause()
        }

        // At 50% progress (in time) animation is at |interpolatedProgress|:
        val interpolatedProgress = animator.interpolator.getInterpolation(0.5f)
        val expectedXY = (100f * interpolatedProgress).roundToInt().toFloat()
        val expectedWidth = 100 + (200 - 100) * interpolatedProgress
        val expectedHeight = 100 + (300 - 100) * interpolatedProgress
        verify(intermediateTransaction).setPosition(testLeash, expectedXY, expectedXY)
        verify(intermediateTransaction)
            .setWindowCrop(testLeash, expectedWidth.toInt(), expectedHeight.toInt())
        verify(intermediateTransaction, atLeastOnce()).apply()

        // Finish transaction and callback should not be called yet.
        verify(finishTransaction, never()).setPosition(any(), any(), any())
        verify(finishTransaction, never()).setWindowCrop(any(), any(), any())
        verify(finishCallback, never()).onTransitionFinished(any())
    }

    @Test
    fun createPinAnimator_listenerReturnsFalse_appliesTransactions() {
        val startBounds = Rect(0, 0, 100, 100)
        val endBounds = Rect(100, 100, 300, 400)
        whenever(change.startAbsBounds).thenReturn(startBounds)
        whenever(change.endAbsBounds).thenReturn(endBounds)
        val animator =
            PinnedLayerAnimator.createPinAnimator(
                change,
                startTransaction,
                finishTransaction,
                finishCallback,
                sctFactory = { intermediateTransaction },
                onAnimationStart = { _, _, _ -> false },
                onAnimationUpdate = { _, _, _ -> false },
            ) as ValueAnimator

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            animator.start()
            animator.setCurrentPlayTime(150) // Halfway through 300ms duration
            animator.end()
        }

        // Then onAnimationStart applies the start state to startTransaction.
        verify(startTransaction).apply()
        // And intermediate transactions are applied.
        verify(intermediateTransaction, atLeastOnce()).apply()
    }

    private fun createTask(taskId: Int): RunningTaskInfo {
        return RunningTaskInfo().apply { this.taskId = taskId }
    }
}
