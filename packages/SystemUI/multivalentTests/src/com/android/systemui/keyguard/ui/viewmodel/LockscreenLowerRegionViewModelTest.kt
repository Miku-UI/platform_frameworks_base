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

import android.platform.test.flag.junit.FlagsParameterization
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.common.ui.data.repository.fakeConfigurationRepository
import com.android.systemui.flags.andSceneContainer
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.runCurrent
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.lifecycle.activateIn
import com.android.systemui.res.R
import com.android.systemui.shade.domain.interactor.enableSingleShade
import com.android.systemui.testKosmos
import com.android.systemui.unfold.fakeUnfoldTransitionProgressProvider
import com.google.common.truth.Truth.assertThat
import java.util.Locale
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import platform.test.runner.parameterized.ParameterizedAndroidJunit4
import platform.test.runner.parameterized.Parameters

@SmallTest
@RunWith(ParameterizedAndroidJunit4::class)
class LockscreenLowerRegionViewModelTest(flags: FlagsParameterization) : SysuiTestCase() {

    private val kosmos: Kosmos = testKosmos()
    private val Kosmos.underTest by
        Kosmos.Fixture { lockscreenLowerRegionViewModelFactory.create() }

    companion object {
        @JvmStatic
        @Parameters(name = "{0}")
        fun getParams(): List<FlagsParameterization> {
            return FlagsParameterization.allCombinationsOf().andSceneContainer()
        }
    }

    init {
        mSetFlagsRule.setFlagsParameterization(flags)
    }

    @Before
    fun setup() {
        with(kosmos) {
            enableSingleShade()
            runCurrent()
            underTest.activateIn(testScope)
        }
    }

    @Test
    fun unfoldTranslations() =
        kosmos.runTest {
            val maxTranslation = prepareConfiguration()

            val unfoldProvider = fakeUnfoldTransitionProgressProvider
            unfoldProvider.onTransitionStarted()
            runCurrent()
            assertThat(underTest.unfoldTranslations.start).isZero()
            assertThat(underTest.unfoldTranslations.end).isZero()

            repeat(10) { repetition ->
                val transitionProgress = 0.1f * (repetition + 1)
                unfoldProvider.onTransitionProgress(transitionProgress)
                runCurrent()
                assertThat(underTest.unfoldTranslations.start)
                    .isEqualTo((1 - transitionProgress) * maxTranslation)
                assertThat(underTest.unfoldTranslations.end)
                    .isEqualTo(-(1 - transitionProgress) * maxTranslation)
            }

            unfoldProvider.onTransitionFinishing()
            runCurrent()
            assertThat(underTest.unfoldTranslations.start).isZero()
            assertThat(underTest.unfoldTranslations.end).isZero()

            unfoldProvider.onTransitionFinished()
            runCurrent()
            assertThat(underTest.unfoldTranslations.start).isZero()
            assertThat(underTest.unfoldTranslations.end).isZero()
        }

    private fun Kosmos.prepareConfiguration(): Int {
        val configuration = context.resources.configuration
        configuration.setLayoutDirection(Locale.US)
        fakeConfigurationRepository.onConfigurationChange(configuration)
        val maxTranslation = 10
        fakeConfigurationRepository.setDimensionPixelSize(
            R.dimen.notification_side_paddings,
            maxTranslation,
        )
        return maxTranslation
    }
}
