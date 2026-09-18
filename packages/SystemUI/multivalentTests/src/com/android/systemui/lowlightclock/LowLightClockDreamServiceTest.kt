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
package com.android.systemui.lowlightclock

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.testing.TestableLooper.RunWithLooper
import android.widget.TextClock
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.dream.lowlight.LowLightTransitionCoordinator
import com.android.systemui.SysuiTestCase
import com.android.systemui.animation.AnimatorTestRule
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.runTest
import com.android.systemui.res.R
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import java.util.Optional
import javax.inject.Provider
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.kotlin.any
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
@RunWithLooper(setAsMainLooper = true)
class LowLightClockDreamServiceTest : SysuiTestCase() {

    @get:Rule
    val animatorTestRule = AnimatorTestRule(this)

    private val kosmos = testKosmos()

    private val Kosmos.chargingStatusProvider by Kosmos.Fixture { mock<ChargingStatusProvider>() }
    private val Kosmos.displayController by Kosmos.Fixture { mock<LowLightDisplayController>() }
    private val Kosmos.lowLightTransitionCoordinator by
        Kosmos.Fixture { mock<LowLightTransitionCoordinator>() }
    private val Kosmos.animationInAnimator by Kosmos.Fixture { ValueAnimator.ofFloat(0f, 1f) }
    private val Kosmos.animationOutAnimator by Kosmos.Fixture { ValueAnimator.ofFloat(0f, 1f) }
    private val Kosmos.animationProvider by
        Kosmos.Fixture<LowLightClockAnimationProvider> {
            mock {
                on { provideAnimationIn(any(), any()) }.thenReturn(animationInAnimator)
                on { provideAnimationOut(any(), any()) }.thenReturn(animationOutAnimator)
            }
        }
    private val Kosmos.textClock by Kosmos.Fixture { mock<TextClock>() }
    private val Kosmos.chargingStatusTextView by Kosmos.Fixture { mock<TextView>() }

    private val Kosmos.underTest by
        Kosmos.Fixture {
            LowLightClockDreamServiceImpl(
                dreamServiceDelegate,
                chargingStatusProvider,
                animationProvider,
                lowLightTransitionCoordinator,
                Optional.of(Provider { displayController }),
            )
        }

    @Before
    fun setUp() {
        with(kosmos.dreamServiceDelegate.fake) {
            setViewForId(R.id.low_light_text_clock, kosmos.textClock)
            setViewForId(R.id.charging_status_text_view, kosmos.chargingStatusTextView)
        }
    }

    @Test
    fun testSetDbmStateWhenSupported() =
        kosmos.runTest {
            whenever(displayController.isDisplayBrightnessModeSupported()).thenReturn(true)
            underTest.onAttachedToWindow()
            underTest.onDreamingStarted()

            verify(displayController).setDisplayBrightnessModeEnabled(true)
        }

    @Test
    fun testNotSetDbmStateWhenNotSupported() =
        kosmos.runTest {
            whenever(displayController.isDisplayBrightnessModeSupported()).thenReturn(false)
            underTest.onAttachedToWindow()
            underTest.onDreamingStarted()

            verify(displayController, never()).setDisplayBrightnessModeEnabled(any())
        }

    @Test
    fun testClearDbmState() =
        kosmos.runTest {
            whenever(displayController.isDisplayBrightnessModeSupported()).thenReturn(true)

            underTest.onAttachedToWindow()
            underTest.onDreamingStarted()
            clearInvocations(displayController)

            underTest.onDreamingStopped()

            verify(displayController).setDisplayBrightnessModeEnabled(false)
        }

    @Test
    fun testAnimationsStartedOnDreamingStarted() =
        kosmos.runTest {
            underTest.onAttachedToWindow()
            underTest.onDreamingStarted()

            // Entry animation started.
            assertThat(animationInAnimator.isStarted).isTrue()
        }

    @Test
    fun testAnimationsStartedOnWakeUp() =
        kosmos.runTest {
            // Start dreaming then wake up.
            underTest.onAttachedToWindow()
            underTest.onDreamingStarted()
            underTest.onWakeUp()

            // Entry animation is not running.
            assertThat(animationInAnimator.isRunning).isFalse()

            // Exit animation started.
            assertThat(animationOutAnimator.isStarted).isTrue()
        }

    @Test
    fun testAnimationsStartedBeforeExitingLowLight() =
        kosmos.runTest {
            underTest.onAttachedToWindow()
            underTest.onBeforeExitLowLight()

            // Exit animation started.
            assertThat(animationOutAnimator.isStarted).isTrue()
        }

    @Test
    fun testFinishCalledOnWakeUpAnimationEnd() =
        kosmos.runTest {
            underTest.onAttachedToWindow()
            underTest.onDreamingStarted()
            underTest.onWakeUp()

            animationOutAnimator.listeners?.forEach {
                it.onAnimationEnd(animationOutAnimator)
            }

            assertThat(dreamServiceDelegate.fake.finished).isTrue()
        }

    @Test
    fun testWakeUpAnimationCancelledOnDetach() =
        kosmos.runTest {
            underTest.onAttachedToWindow()
            underTest.onWakeUp()

            // Exit animation started.
            assertThat(animationOutAnimator.isStarted).isTrue()

            underTest.onDetachedFromWindow()

            assertThat(animationOutAnimator.isRunning).isFalse()
        }

    @Test
    fun testExitLowLightAnimationCancelledOnDetach() =
        kosmos.runTest {
            underTest.onAttachedToWindow()
            underTest.onBeforeExitLowLight()

            // Exit animation started.
            assertThat(animationOutAnimator.isStarted).isTrue()

            underTest.onDetachedFromWindow()

            assertThat(animationOutAnimator.isRunning).isFalse()
        }

    @Test
    fun testSetScreenBrightnessWhenDisplayControllerIsNull() =
        kosmos.runTest {
            val underTestWithoutController =
                LowLightClockDreamServiceImpl(
                    dreamServiceDelegate,
                    chargingStatusProvider,
                    animationProvider,
                    lowLightTransitionCoordinator,
                    Optional.empty(),
                )
            underTestWithoutController.onAttachedToWindow()
            underTestWithoutController.onDreamingStarted()

            assertThat(dreamServiceDelegate.fake.screenBrightness).isEqualTo(0f)
        }
}
