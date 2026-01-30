/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.systemui.keyguard.ui.viewmodel

import android.platform.test.annotations.EnableFlags
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.Flags
import com.android.systemui.SysuiTestCase
import com.android.systemui.coroutines.collectValues
import com.android.systemui.flags.DisableSceneContainer
import com.android.systemui.keyguard.data.repository.fakeKeyguardTransitionRepository
import com.android.systemui.keyguard.shared.model.KeyguardState
import com.android.systemui.keyguard.shared.model.TransitionState
import com.android.systemui.keyguard.shared.model.TransitionState.RUNNING
import com.android.systemui.keyguard.shared.model.TransitionStep
import com.android.systemui.keyguard.ui.transitions.blurConfig
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.collectValues
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.statusbar.phone.ScrimState
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@DisableSceneContainer
@RunWith(AndroidJUnit4::class)
class PrimaryBouncerToDreamingTransitionViewModelTest : SysuiTestCase() {
    private val kosmos = testKosmos().useUnconfinedTestDispatcher()
    private lateinit var underTest: PrimaryBouncerToDreamingTransitionViewModel

    @Before
    fun setUp() {
        underTest = kosmos.primaryBouncerToDreamingTransitionViewModel
    }

    @Test
    fun blurRadiusGoesToMinImmediately() =
        kosmos.runTest {
            val values by collectValues(underTest.windowBlurRadius)
            keyguardWindowBlurTestUtil.shadeExpanded(false)

            keyguardWindowBlurTestUtil.assertTransitionToBlurRadius(
                transitionProgress = listOf(0.0f, 0.2f, 0.3f, 0.65f, 0.7f, 1.0f),
                startValue = kosmos.blurConfig.maxBlurRadiusPx,
                endValue = kosmos.blurConfig.minBlurRadiusPx,
                actualValuesProvider = { values },
                transitionFactory = ::step,
            )
        }

    @Test
    @EnableFlags(Flags.FLAG_NOTIFICATION_SHADE_BLUR)
    fun blurRadiusRemainsAtMaxIfShadeIsExpandedAndShadeBlurIsEnabled() =
        kosmos.runTest {
            val values by collectValues(underTest.windowBlurRadius)
            keyguardWindowBlurTestUtil.shadeExpanded(true)

            keyguardWindowBlurTestUtil.assertTransitionToBlurRadius(
                transitionProgress = listOf(0.0f, 0.2f, 0.3f, 0.65f, 0.7f, 1.0f),
                startValue = kosmos.blurConfig.maxBlurRadiusPx,
                endValue = kosmos.blurConfig.maxBlurRadiusPx,
                actualValuesProvider = { values },
                transitionFactory = ::step,
                checkInterpolatedValues = false,
            )
        }

    @Test
    fun notificationBlurDropsToMinWhenGoingBackFromPrimaryBouncerToDreaming() =
        kosmos.runTest {
            val values by collectValues(underTest.notificationBlurRadius)

            keyguardWindowBlurTestUtil.assertTransitionToBlurRadius(
                transitionProgress = listOf(0.0f, 0.2f, 0.3f, 0.65f, 0.7f, 1.0f),
                startValue = kosmos.blurConfig.minBlurRadiusPx,
                endValue = kosmos.blurConfig.minBlurRadiusPx,
                actualValuesProvider = { values },
                transitionFactory = ::step,
                checkInterpolatedValues = false,
            )
        }

    @Test
    fun scrimAlpha() =
        kosmos.runTest {
            val value by collectLastValue(underTest.scrimAlpha)

            for (step in listOf(0f, 0.2f, 0.5f, 0.8f, 1.0f)) {
                fakeKeyguardTransitionRepository.sendTransitionStep(step(step))
                assertThat(value?.behindAlpha)
                    .isEqualTo((1 - step) * ScrimState.KEYGUARD.behindAlpha)
            }
        }

    private fun step(value: Float, state: TransitionState = RUNNING): TransitionStep {
        return TransitionStep(
            from = KeyguardState.PRIMARY_BOUNCER,
            to = KeyguardState.DREAMING,
            value = value,
            transitionState = state,
            ownerName = "PrimaryBouncerToDreamingTransitionViewModelTest",
        )
    }
}
