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
 *
 */

package com.android.systemui.keyguard.domain.interactor

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.biometrics.data.repository.fakeFingerprintPropertyRepository
import com.android.systemui.keyguard.data.repository.fakeBiometricSettingsRepository
import com.android.systemui.keyguard.data.repository.fakeDeviceEntryFingerprintAuthRepository
import com.android.systemui.keyguard.data.repository.fakeKeyguardRepository
import com.android.systemui.keyguard.shared.model.DozeStateModel
import com.android.systemui.keyguard.shared.model.DozeTransitionModel
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.power.data.repository.fakePowerRepository
import com.android.systemui.power.shared.model.DozeScreenStateModel
import com.android.systemui.scene.shared.flag.SceneContainerFlag
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class DozeTouchInteractorTest : SysuiTestCase() {

    private val kosmos = testKosmos()
    private val Kosmos.underTest by Kosmos.Fixture { kosmos.dozeTouchInteractor }

    @Test
    fun shouldInterceptTouches_notDozing() =
        kosmos.runTest {
            val shouldInterceptTouches by collectLastValue(underTest.shouldInterceptTouches)
            kosmos.fakeKeyguardRepository.setDozeTransitionModel(
                DozeTransitionModel(
                    from = DozeStateModel.UNINITIALIZED,
                    to = DozeStateModel.UNINITIALIZED,
                )
            )
            assertThat(shouldInterceptTouches).isFalse()
        }

    @Test
    fun shouldInterceptTouches_pulsing() =
        kosmos.runTest {
            val shouldInterceptTouches by collectLastValue(underTest.shouldInterceptTouches)
            kosmos.fakeKeyguardRepository.setDozeTransitionModel(
                DozeTransitionModel(
                    from = DozeStateModel.DOZE_REQUEST_PULSE,
                    to = DozeStateModel.DOZE_PULSING,
                )
            )
            assertThat(shouldInterceptTouches).isFalse()
        }

    @Test
    fun shouldInterceptTouches_aod() =
        kosmos.runTest {
            val shouldInterceptTouches by collectLastValue(underTest.shouldInterceptTouches)
            kosmos.fakeKeyguardRepository.setDozeTransitionModel(
                DozeTransitionModel(from = DozeStateModel.INITIALIZED, to = DozeStateModel.DOZE_AOD)
            )
            assertThat(shouldInterceptTouches).isTrue()
        }

    @Test
    fun shouldInterceptTouches_docked() =
        kosmos.runTest {
            val shouldInterceptTouches by collectLastValue(underTest.shouldInterceptTouches)
            kosmos.fakeKeyguardRepository.setDozeTransitionModel(
                DozeTransitionModel(
                    from = DozeStateModel.DOZE_AOD,
                    to = DozeStateModel.DOZE_AOD_DOCKED,
                )
            )
            assertThat(shouldInterceptTouches).isFalse()
        }

    @Test
    fun shouldInterceptTouches_aodTransition_longpressEnabled() =
        kosmos.runTest {
            val shouldInterceptTouches by collectLastValue(underTest.shouldInterceptTouches)
            kosmos.fakeKeyguardRepository.setDozeTransitionModel(
                DozeTransitionModel(from = DozeStateModel.INITIALIZED, to = DozeStateModel.DOZE_AOD)
            )
            kosmos.fakePowerRepository.dozeScreenState.value = DozeScreenStateModel.ON
            deviceEntryIconLongPressEnabled()
            assertThat(shouldInterceptTouches).isFalse()
        }

    private fun deviceEntryIconLongPressEnabled() {
        kosmos.fakeBiometricSettingsRepository.setIsFingerprintAuthEnrolledAndEnabled(true)
        kosmos.fakeFingerprintPropertyRepository.supportsUdfps()
        kosmos.fakeDeviceEntryFingerprintAuthRepository.setIsRunning(false)
        if (!SceneContainerFlag.isEnabled) {
            kosmos.fakeKeyguardRepository.setHasTrust(false)
        }
    }
}
