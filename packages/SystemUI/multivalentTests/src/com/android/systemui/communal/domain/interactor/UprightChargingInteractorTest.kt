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

package com.android.systemui.communal.domain.interactor

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.communal.posturing.domain.interactor.posturingInteractor
import com.android.systemui.communal.posturing.shared.model.PosturedState
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.statusbar.pipeline.battery.data.repository.batteryRepository
import com.android.systemui.statusbar.policy.batteryController
import com.android.systemui.statusbar.policy.fake
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class UprightChargingInteractorTest : SysuiTestCase() {
    private val kosmos = testKosmos()

    private val Kosmos.underTest by
        Kosmos.Fixture {
            UprightChargingInteractorImpl(
                batteryRepository = batteryRepository,
                posturingInteractor = posturingInteractor,
            )
        }

    @Test
    fun isTriggered_whenPluggedIn_butNotPostured_isFalse() =
        kosmos.runTest {
            val isTriggered by collectLastValue(kosmos.underTest.isTriggered)

            batteryController.fake._isPluggedIn = true
            posturingInteractor.setValueForDebug(PosturedState.NotPostured(false, false))
            assertThat(isTriggered).isFalse()
        }

    @Test
    fun isTriggered_whenPostured_butNotPluggedIn_isFalse() =
        kosmos.runTest {
            val isTriggered by collectLastValue(kosmos.underTest.isTriggered)

            batteryController.fake._isPluggedIn = false
            posturingInteractor.setValueForDebug(PosturedState.Postured)
            assertThat(isTriggered).isFalse()
        }

    @Test
    fun isTriggered_whenPluggedIn_andPostured_isTrue() =
        kosmos.runTest {
            val isTriggered by collectLastValue(kosmos.underTest.isTriggered)

            batteryController.fake._isPluggedIn = true
            posturingInteractor.setValueForDebug(PosturedState.Postured)
            assertThat(isTriggered).isTrue()
        }

    @Test
    fun isTriggered_transitions() =
        kosmos.runTest {
            val isTriggered by collectLastValue(kosmos.underTest.isTriggered)

            batteryController.fake._isPluggedIn = true
            posturingInteractor.setValueForDebug(PosturedState.Postured)
            assertThat(isTriggered).isTrue()

            posturingInteractor.setValueForDebug(PosturedState.NotPostured(false, false))
            assertThat(isTriggered).isFalse()

            posturingInteractor.setValueForDebug(PosturedState.Postured)
            assertThat(isTriggered).isTrue()

            batteryController.fake._isPluggedIn = false
            assertThat(isTriggered).isFalse()
        }
}
