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

package com.android.systemui.statusbar

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.internal.R
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.statusbar.phone.StatusBarSignalPolicy
import com.android.systemui.statusbar.phone.ui.StatusBarIconController
import com.android.systemui.statusbar.pipeline.airplane.domain.interactor.airplaneModeInteractor
import com.android.systemui.statusbar.pipeline.ethernet.domain.ethernetInteractor
import com.android.systemui.statusbar.pipeline.shared.data.repository.connectivityRepository
import com.android.systemui.statusbar.pipeline.shared.data.repository.fake
import com.android.systemui.statusbar.policy.SecurityController
import com.android.systemui.testKosmos
import com.android.systemui.tuner.tunerService
import com.android.systemui.util.kotlin.javaAdapter
import kotlin.test.Test
import org.junit.Before
import org.junit.runner.RunWith
import org.mockito.Mockito.verify
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.mock

@SmallTest
@RunWith(AndroidJUnit4::class)
class StatusBarSignalPolicyTest : SysuiTestCase() {
    private val kosmos = testKosmos().useUnconfinedTestDispatcher()

    private val securityController = mock<SecurityController>()
    private val statusBarIconController = mock<StatusBarIconController>()

    private val Kosmos.underTest by
        Kosmos.Fixture {
            StatusBarSignalPolicy(
                mContext,
                statusBarIconController,
                securityController,
                tunerService,
                javaAdapter,
                airplaneModeInteractor,
                ethernetInteractor,
            )
        }

    private lateinit var slotAirplane: String
    private lateinit var slotEthernet: String

    @Before
    fun setup() {
        slotAirplane = mContext.getString(R.string.status_bar_airplane)
        slotEthernet = mContext.getString(R.string.status_bar_ethernet)
    }

    @Test
    fun airplaneModeViaInteractor_iconUpdated() =
        kosmos.runTest {
            underTest.start()
            clearInvocations(statusBarIconController)

            airplaneModeInteractor.setIsAirplaneMode(true)
            verify(statusBarIconController).setIconVisibility(slotAirplane, true)

            airplaneModeInteractor.setIsAirplaneMode(false)
            verify(statusBarIconController).setIconVisibility(slotAirplane, false)
        }

    @Test
    fun ethernetIconViaInteractor_iconUpdated() =
        kosmos.runTest {
            underTest.start()
            clearInvocations(statusBarIconController)

            connectivityRepository.fake.setEthernetConnected(default = true, validated = true)
            verify(statusBarIconController).setIconVisibility(slotEthernet, true)

            connectivityRepository.fake.setEthernetConnected(default = false, validated = false)
            verify(statusBarIconController).setIconVisibility(slotEthernet, false)

            clearInvocations(statusBarIconController)

            connectivityRepository.fake.setEthernetConnected(default = true, validated = false)
            verify(statusBarIconController).setIconVisibility(slotEthernet, true)
        }
}
