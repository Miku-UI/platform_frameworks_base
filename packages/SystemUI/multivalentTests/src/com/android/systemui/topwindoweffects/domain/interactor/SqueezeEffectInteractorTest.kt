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

package com.android.systemui.topwindoweffects.domain.interactor

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.testKosmos
import com.android.systemui.topwindoweffects.data.repository.fakeSqueezeEffectRepository
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class SqueezeEffectInteractorTest : SysuiTestCase() {

    private val kosmos = testKosmos().useUnconfinedTestDispatcher()

    private val Kosmos.underTest by
        Kosmos.Fixture {
            SqueezeEffectInteractor(squeezeEffectRepository = fakeSqueezeEffectRepository)
        }

    @Test
    fun powerButtonSemantics_powerKeyNotDownAsSingleGestureAndDisabled_cancelsSqueeze() =
        kosmos.runTest {
            fakeSqueezeEffectRepository.isEffectEnabled.value = false
            fakeSqueezeEffectRepository.isPowerButtonPressedAsSingleGesture.value = false

            val powerButtonSemantics by collectLastValue(underTest.powerButtonSemantics)

            assertThat(powerButtonSemantics).isEqualTo(PowerButtonSemantics.CANCEL_SQUEEZE)
        }

    @Test
    fun powerButtonSemantics_powerKeyNotDownAsSingleGestureAndEnabled_cancelsSqueeze() =
        kosmos.runTest {
            fakeSqueezeEffectRepository.isEffectEnabled.value = true
            fakeSqueezeEffectRepository.isPowerButtonPressedAsSingleGesture.value = false

            val powerButtonSemantics by collectLastValue(underTest.powerButtonSemantics)

            assertThat(powerButtonSemantics).isEqualTo(PowerButtonSemantics.CANCEL_SQUEEZE)
        }

    @Test
    fun powerButtonSemantics_powerKeyDownAsSingleGestureAndDisabled_isNull() =
        kosmos.runTest {
            fakeSqueezeEffectRepository.isEffectEnabled.value = false
            fakeSqueezeEffectRepository.isPowerButtonPressedAsSingleGesture.value = true

            val powerButtonSemantics by collectLastValue(underTest.powerButtonSemantics)

            assertThat(powerButtonSemantics).isNull()
        }

    @Test
    fun powerButtonSemantics_powerKeyDownAsSingleGestureAndEnabled_withRumble_startsEffect() =
        kosmos.runTest {
            fakeSqueezeEffectRepository.isEffectEnabled.value = true
            fakeSqueezeEffectRepository.isPowerButtonPressedAsSingleGesture.value = true
            fakeSqueezeEffectRepository.shouldUseHapticRumble = true

            val powerButtonSemantics by collectLastValue(underTest.powerButtonSemantics)

            assertThat(powerButtonSemantics)
                .isEqualTo(PowerButtonSemantics.START_SQUEEZE_WITH_RUMBLE)
        }

    @Test
    fun powerButtonSemantics_powerKeyDownAsSingleGestureAndEnabled_withoutRumble_startsEffect() =
        kosmos.runTest {
            fakeSqueezeEffectRepository.isEffectEnabled.value = true
            fakeSqueezeEffectRepository.isPowerButtonPressedAsSingleGesture.value = true
            fakeSqueezeEffectRepository.shouldUseHapticRumble = false

            val powerButtonSemantics by collectLastValue(underTest.powerButtonSemantics)

            assertThat(powerButtonSemantics)
                .isEqualTo(PowerButtonSemantics.START_SQUEEZE_WITHOUT_RUMBLE)
        }

    @Test
    fun powerButtonSemantics_onLPPAndDisabledAndPowerKeyAsSingleGesture_playsAssistantHaptics() =
        kosmos.runTest {
            fakeSqueezeEffectRepository.isEffectEnabled.value = false
            fakeSqueezeEffectRepository.isPowerButtonPressedAsSingleGesture.value = true
            fakeSqueezeEffectRepository.isPowerButtonLongPressed.value = true

            val powerButtonSemantics by collectLastValue(underTest.powerButtonSemantics)

            assertThat(powerButtonSemantics)
                .isEqualTo(PowerButtonSemantics.PLAY_DEFAULT_ASSISTANT_HAPTICS)
        }

    @Test
    fun powerButtonSemantics_onLPPAndDisabledAndPowerKeyNotAsSingleGesture_cancelsSqueeze() =
        kosmos.runTest {
            fakeSqueezeEffectRepository.isEffectEnabled.value = false
            fakeSqueezeEffectRepository.isPowerButtonPressedAsSingleGesture.value = false
            fakeSqueezeEffectRepository.isPowerButtonLongPressed.value = true

            val powerButtonSemantics by collectLastValue(underTest.powerButtonSemantics)

            assertThat(powerButtonSemantics).isEqualTo(PowerButtonSemantics.CANCEL_SQUEEZE)
        }
}
