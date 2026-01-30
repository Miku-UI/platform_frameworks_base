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
import com.android.systemui.biometrics.data.repository.fingerprintPropertyRepository
import com.android.systemui.deviceentry.domain.interactor.deviceEntryUdfpsInteractor
import com.android.systemui.flags.andSceneContainer
import com.android.systemui.keyguard.shared.transition.fakeKeyguardTransitionAnimationCallback
import com.android.systemui.keyguard.shared.transition.keyguardTransitionAnimationCallbackDelegator
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runCurrent
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.lifecycle.activateIn
import com.android.systemui.shade.domain.interactor.enableSingleShade
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Job
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import platform.test.runner.parameterized.ParameterizedAndroidJunit4
import platform.test.runner.parameterized.Parameters

@SmallTest
@RunWith(ParameterizedAndroidJunit4::class)
class LockscreenContentViewModelTest(flags: FlagsParameterization) : SysuiTestCase() {

    private val kosmos: Kosmos = testKosmos()
    private val activationJob = Job()
    private val viewState: ViewStateAccessor = ViewStateAccessor({ 0f })
    private val Kosmos.underTest by
        Kosmos.Fixture {
            lockscreenContentViewModelFactory.create(
                fakeKeyguardTransitionAnimationCallback,
                viewState,
            )
        }

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
            underTest.activateIn(testScope, activationJob)
        }
    }

    @Test
    fun activate_setsDelegate_onKeyguardTransitionAnimationCallbackDelegator() =
        kosmos.runTest {
            runCurrent()
            assertThat(keyguardTransitionAnimationCallbackDelegator.delegate)
                .isSameInstanceAs(fakeKeyguardTransitionAnimationCallback)
        }

    @Test
    fun deactivate_clearsDelegate_onKeyguardTransitionAnimationCallbackDelegator() =
        kosmos.runTest {
            activationJob.cancel()
            runCurrent()
            assertThat(keyguardTransitionAnimationCallbackDelegator.delegate).isNull()
        }

    fun isUdfpsSupported_withoutUdfps_false() =
        kosmos.runTest {
            val isUdfpsSupported by collectLastValue(deviceEntryUdfpsInteractor.isUdfpsSupported)

            fingerprintPropertyRepository.supportsRearFps()
            assertThat(isUdfpsSupported).isFalse()
            assertThat(underTest.isUdfpsSupported).isFalse()
        }

    @Test
    fun isUdfpsSupported_withUdfps_true() =
        kosmos.runTest {
            val isUdfpsSupported by collectLastValue(deviceEntryUdfpsInteractor.isUdfpsSupported)

            fingerprintPropertyRepository.supportsUdfps()
            assertThat(isUdfpsSupported).isTrue()
            assertThat(underTest.isUdfpsSupported).isTrue()
        }
}
