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

package com.android.systemui.statusbar.pipeline.mobile.ui.viewmodel

import android.telephony.SubscriptionManager.INVALID_SUBSCRIPTION_ID
import android.telephony.SubscriptionManager.PROFILE_CLASS_UNSET
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.flags.Flags
import com.android.systemui.flags.fake
import com.android.systemui.flags.featureFlagsClassic
import com.android.systemui.kairos.KairosTestScope
import com.android.systemui.kairos.runKairosTest
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.statusbar.pipeline.airplane.domain.interactor.airplaneModeInteractor
import com.android.systemui.statusbar.pipeline.mobile.data.model.SubscriptionModel
import com.android.systemui.statusbar.pipeline.mobile.data.repository.fake
import com.android.systemui.statusbar.pipeline.mobile.data.repository.fakeMobileConnectionsRepositoryKairos
import com.android.systemui.statusbar.pipeline.mobile.data.repository.mobileConnectionsRepositoryKairos
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class MobileIconsViewModelKairosTest : SysuiTestCase() {

    private val Kosmos.underTest
        get() = mobileIconsViewModelKairos

    private val kosmos =
        testKosmos().apply {
            useUnconfinedTestDispatcher()
            featureFlagsClassic.fake.apply {
                setDefault(Flags.FILTER_PROVISIONING_NETWORK_SUBSCRIPTIONS)
            }
            mobileConnectionsRepositoryKairos =
                fakeMobileConnectionsRepositoryKairos.apply {
                    val subList = listOf(SUB_1, SUB_2)
                    setActiveMobileDataSubscriptionId(SUB_1.subscriptionId)
                    subscriptions.setValue(subList)
                }
        }

    private fun runTest(block: suspend KairosTestScope.() -> Unit) =
        kosmos.run { runKairosTest { block() } }

    private fun KairosTestScope.setSubscriptions(
        subList: List<SubscriptionModel>,
        activeSubId: Int = subList.getOrNull(0)?.subscriptionId ?: INVALID_SUBSCRIPTION_ID,
    ) {
        mobileConnectionsRepositoryKairos.fake.subscriptions.setValue(subList)
        mobileConnectionsRepositoryKairos.fake.setActiveMobileDataSubscriptionId(activeSubId)
    }

    @Test
    fun subscriptionIdsFlow_matchesInteractor() = runTest {
        val latest by underTest.subscriptionIds.collectLastValue()
        setSubscriptions(
            listOf(
                SubscriptionModel(
                    subscriptionId = 1,
                    isOpportunistic = false,
                    carrierName = "Carrier 1",
                    profileClass = PROFILE_CLASS_UNSET,
                )
            )
        )
        assertThat(latest).isEqualTo(listOf(1))

        setSubscriptions(
            listOf(
                SubscriptionModel(
                    subscriptionId = 2,
                    isOpportunistic = false,
                    carrierName = "Carrier 2",
                    profileClass = PROFILE_CLASS_UNSET,
                ),
                SubscriptionModel(
                    subscriptionId = 5,
                    isOpportunistic = true,
                    carrierName = "Carrier 5",
                    profileClass = PROFILE_CLASS_UNSET,
                ),
                SubscriptionModel(
                    subscriptionId = 7,
                    isOpportunistic = true,
                    carrierName = "Carrier 7",
                    profileClass = PROFILE_CLASS_UNSET,
                ),
            )
        )
        assertThat(latest).isEqualTo(listOf(2, 5, 7))

        setSubscriptions(emptyList())
        assertThat(latest).isEmpty()
    }

    @Test
    fun isStackable_apmEnabled_false() = runTest {
        val latest by underTest.isStackable.collectLastValue()

        // Two subscriptions, both cellular, with equal number of levels
        setSubscriptions(listOf(SUB_1, SUB_2))

        // Enable APM
        airplaneModeInteractor.setIsAirplaneMode(true)

        assertThat(latest).isFalse()
    }

    @Test
    fun isStackable_apmDisabled_true() = runTest {
        val latest by underTest.isStackable.collectLastValue()

        // Two subscriptions, both cellular, with equal number of levels
        setSubscriptions(listOf(SUB_1, SUB_2))

        // Disable APM
        airplaneModeInteractor.setIsAirplaneMode(false)

        assertThat(latest).isTrue()
    }

    companion object {
        private val SUB_1 =
            SubscriptionModel(
                subscriptionId = 1,
                isOpportunistic = false,
                carrierName = "Carrier 1",
                profileClass = PROFILE_CLASS_UNSET,
            )
        private val SUB_2 =
            SubscriptionModel(
                subscriptionId = 2,
                isOpportunistic = false,
                carrierName = "Carrier 2",
                profileClass = PROFILE_CLASS_UNSET,
            )
    }
}
