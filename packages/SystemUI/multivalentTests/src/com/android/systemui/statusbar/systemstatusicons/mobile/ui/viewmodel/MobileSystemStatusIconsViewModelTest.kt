/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.statusbar.systemstatusicons.mobile.ui.viewmodel

import android.content.testableContext
import android.platform.test.annotations.EnableFlags
import android.telephony.SubscriptionManager.PROFILE_CLASS_UNSET
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.lifecycle.activateIn
import com.android.systemui.statusbar.core.NewStatusBarIcons
import com.android.systemui.statusbar.pipeline.mobile.data.model.SubscriptionModel
import com.android.systemui.statusbar.pipeline.mobile.domain.interactor.fakeMobileIconsInteractor
import com.android.systemui.statusbar.systemstatusicons.SystemStatusIconsInCompose
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@EnableFlags(SystemStatusIconsInCompose.FLAG_NAME, NewStatusBarIcons.FLAG_NAME)
@SmallTest
@RunWith(AndroidJUnit4::class)
class MobileSystemStatusIconsViewModelTest : SysuiTestCase() {

    private val kosmos = testKosmos().useUnconfinedTestDispatcher()
    private val underTest =
        kosmos.mobileSystemStatusIconsViewModelFactory.create(kosmos.testableContext).apply {
            activateIn(kosmos.testScope)
        }

    @Test fun visible_default_isFalse() = kosmos.runTest { assertThat(underTest.visible).isFalse() }

    @Test
    fun visible_whenSubsListIsPopulated_isTrue() =
        kosmos.runTest {
            fakeMobileIconsInteractor.filteredSubscriptions.value = emptyList()
            assertThat(underTest.visible).isFalse()

            fakeMobileIconsInteractor.filteredSubscriptions.value = listOf(SUB_1)

            assertThat(underTest.visible).isTrue()
        }

    @Test
    fun visible_whenSubsListIsCleared_isFalse() =
        kosmos.runTest {
            fakeMobileIconsInteractor.filteredSubscriptions.value = listOf(SUB_1)
            assertThat(underTest.visible).isTrue()

            fakeMobileIconsInteractor.filteredSubscriptions.value = emptyList()

            assertThat(underTest.visible).isFalse()
        }

    @Test
    fun visible_subscriptionChanges_flipsCorrectly() =
        kosmos.runTest {
            assertThat(underTest.visible).isFalse()

            fakeMobileIconsInteractor.filteredSubscriptions.value = listOf(SUB_1, SUB_2)
            assertThat(underTest.visible).isTrue()

            fakeMobileIconsInteractor.filteredSubscriptions.value = listOf(SUB_1, SUB_2, SUB_3)
            assertThat(underTest.visible).isTrue()

            fakeMobileIconsInteractor.filteredSubscriptions.value = listOf(SUB_1)
            assertThat(underTest.visible).isTrue()

            fakeMobileIconsInteractor.filteredSubscriptions.value = emptyList()
            assertThat(underTest.visible).isFalse()
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
        private val SUB_3 =
            SubscriptionModel(
                subscriptionId = 3,
                isOpportunistic = false,
                carrierName = "Carrier 3",
                profileClass = PROFILE_CLASS_UNSET,
            )
    }
}
