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

package com.android.systemui.util.animation

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorTestRule
import android.animation.ValueAnimator
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.Flags
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.log.assertLogsWtfs
import com.android.systemui.runOnMainThreadAndWaitForIdleSync
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class ScreenOffAnimationGuardTest : SysuiTestCase() {

    private val kosmos = Kosmos()

    @get:Rule public val animatorTestRuleX = androidx.core.animation.AnimatorTestRule()

    @Test
    @EnableFlags(Flags.FLAG_SCREEN_OFF_ANIMATION_GUARD_ENABLED)
    fun enableScreenOffAnimationGuard_screenOn_allGood() {
        val valueAnimator = ValueAnimator.ofFloat(0.0f, 1.0f)

        val latch = CountDownLatch(1)
        valueAnimator.enableScreenOffAnimationGuard({ false })
        valueAnimator.duration = 100
        valueAnimator.addListener(
            object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    latch.countDown()
                }
            }
        )
        runOnMainThreadAndWaitForIdleSync { valueAnimator.start() }

        latch.await(1, TimeUnit.SECONDS)
        // No Log.WTF
    }

    @Test
    @EnableFlags(Flags.FLAG_SCREEN_OFF_ANIMATION_GUARD_ENABLED)
    fun enableScreenOffAnimationGuard_screenOff_reportsWtf() {
        val valueAnimator = ValueAnimator.ofFloat(0.0f, 1.0f)

        val latch = CountDownLatch(1)
        valueAnimator.enableScreenOffAnimationGuard({ true })
        valueAnimator.duration = 100
        valueAnimator.addListener(
            object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    latch.countDown()
                }
            }
        )

        assertLogsWtfs {
            runOnMainThreadAndWaitForIdleSync { valueAnimator.start() }
            latch.await(1, TimeUnit.SECONDS)
        }
    }

    @Test
    @EnableFlags(Flags.FLAG_SCREEN_OFF_ANIMATION_GUARD_ENABLED)
    fun enableScreenOffAnimationGuardX_screenOn_allGood() {
        val valueAnimator = androidx.core.animation.ValueAnimator.ofFloat(0.0f, 1.0f)
        val latch = CountDownLatch(1)
        valueAnimator.enableScreenOffAnimationGuard({ false })
        valueAnimator.duration = 100
        valueAnimator.addListener(
            object : androidx.core.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: androidx.core.animation.Animator) {
                    latch.countDown()
                }
            }
        )
        runOnMainThreadAndWaitForIdleSync { valueAnimator.start() }

        latch.await(1, TimeUnit.SECONDS)
        // No Log.WTF
    }

    @Test
    @EnableFlags(Flags.FLAG_SCREEN_OFF_ANIMATION_GUARD_ENABLED)
    fun enableScreenOffAnimationGuardX_screenOff_reportsWtf() {
        val valueAnimator = androidx.core.animation.ValueAnimator.ofFloat(0.0f, 1.0f)

        val latch = CountDownLatch(1)
        valueAnimator.enableScreenOffAnimationGuard({ true })
        valueAnimator.duration = 100
        valueAnimator.addListener(
            object : androidx.core.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: androidx.core.animation.Animator) {
                    latch.countDown()
                }
            }
        )

        assertLogsWtfs {
            runOnMainThreadAndWaitForIdleSync { valueAnimator.start() }
            latch.await(1, TimeUnit.SECONDS)
        }
    }

    @Test
    @DisableFlags(Flags.FLAG_SCREEN_OFF_ANIMATION_GUARD_ENABLED)
    fun enableScreenOffAnimationGuard_screenOff_flagDisabled_doesNotReportWtf() {
        val valueAnimator = ValueAnimator.ofFloat(0.0f, 1.0f)

        val latch = CountDownLatch(1)
        valueAnimator.enableScreenOffAnimationGuard({ true })
        valueAnimator.duration = 100
        valueAnimator.addListener(
            object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    latch.countDown()
                }
            }
        )

        runOnMainThreadAndWaitForIdleSync { valueAnimator.start() }
        latch.await(1, TimeUnit.SECONDS)
    }
}
