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

package com.android.systemui.statusbar.pipeline.mobile.ui.viewmodel

import android.content.Context
import android.platform.test.annotations.EnableFlags
import android.telephony.SubscriptionManager.PROFILE_CLASS_UNSET
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.Kosmos.Fixture
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.lifecycle.activateIn
import com.android.systemui.statusbar.connectivity.ui.mobileContextProvider
import com.android.systemui.statusbar.core.NewStatusBarIcons
import com.android.systemui.statusbar.core.StatusBarRootModernization
import com.android.systemui.statusbar.pipeline.mobile.data.model.SubscriptionModel
import com.android.systemui.statusbar.pipeline.mobile.domain.interactor.fakeMobileIconsInteractor
import com.android.systemui.statusbar.pipeline.mobile.domain.model.SignalIconModel
import com.android.systemui.statusbar.pipeline.shared.connectivityConstants
import com.android.systemui.statusbar.pipeline.shared.data.model.DataActivityModel
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
class StackedMobileIconViewModelTest : SysuiTestCase() {
    private val kosmos =
        testKosmos().useUnconfinedTestDispatcher().apply {
            connectivityConstants.shouldShowActivityConfig = true
        }

    private val Kosmos.underTest: StackedMobileIconViewModelImpl by Fixture {
        stackedMobileIconViewModelImpl
    }

    @Before
    fun setUp() =
        kosmos.run {
            // Set prerequisites for the stacked icon
            fakeMobileIconsInteractor.isStackable.value = true
            whenever(mobileContextProvider.getMobileContextForSub(any(), any())).thenReturn(context)
            underTest.activateIn(testScope)
        }

    @Test
    @EnableFlags(NewStatusBarIcons.FLAG_NAME, StatusBarRootModernization.FLAG_NAME)
    fun dualSim_filtersOutNonDualConnections() =
        kosmos.runTest {
            fakeMobileIconsInteractor.filteredSubscriptions.value = listOf()
            assertThat(underTest.dualSim).isNull()

            fakeMobileIconsInteractor.filteredSubscriptions.value = listOf(SUB_1)
            assertThat(underTest.dualSim).isNull()

            fakeMobileIconsInteractor.filteredSubscriptions.value = listOf(SUB_1, SUB_2, SUB_3)
            assertThat(underTest.dualSim).isNull()

            fakeMobileIconsInteractor.filteredSubscriptions.value = listOf(SUB_1, SUB_2)
            assertThat(underTest.dualSim).isNotNull()
        }

    @Test
    @EnableFlags(NewStatusBarIcons.FLAG_NAME, StatusBarRootModernization.FLAG_NAME)
    fun dualSim_filtersOutNonCellularIcons() =
        kosmos.runTest {
            fakeMobileIconsInteractor.filteredSubscriptions.value = listOf(SUB_1)
            assertThat(underTest.dualSim).isNull()

            fakeMobileIconsInteractor
                .getInteractorForSubId(SUB_1.subscriptionId)!!
                .signalLevelIcon
                .value =
                SignalIconModel.Satellite(
                    level = 0,
                    icon = Icon.Resource(resId = 0, contentDescription = null),
                )
            fakeMobileIconsInteractor.filteredSubscriptions.value = listOf(SUB_1, SUB_2)
            assertThat(underTest.dualSim).isNull()
        }

    @Test
    @EnableFlags(NewStatusBarIcons.FLAG_NAME, StatusBarRootModernization.FLAG_NAME)
    fun dualSim_tracksActiveSubId() =
        kosmos.runTest {
            // Active sub id is null, order is unchanged
            fakeMobileIconsInteractor.filteredSubscriptions.value = listOf(SUB_1, SUB_2)
            setIconLevel(SUB_1.subscriptionId, 1)
            setIconLevel(SUB_2.subscriptionId, 2)

            assertThat(underTest.dualSim!!.primary.level).isEqualTo(1)
            assertThat(underTest.dualSim!!.secondary.level).isEqualTo(2)

            // Active sub is 2, order is swapped
            fakeMobileIconsInteractor.activeMobileDataSubscriptionId.value = SUB_2.subscriptionId

            assertThat(underTest.dualSim!!.primary.level).isEqualTo(2)
            assertThat(underTest.dualSim!!.secondary.level).isEqualTo(1)
        }

    @Test
    @EnableFlags(NewStatusBarIcons.FLAG_NAME, StatusBarRootModernization.FLAG_NAME)
    fun contentDescription_requiresBothIcons() =
        kosmos.runTest {
            fakeMobileIconsInteractor.filteredSubscriptions.value = listOf()
            assertThat(underTest.contentDescription).isNull()

            fakeMobileIconsInteractor.filteredSubscriptions.value = listOf(SUB_1)
            assertThat(underTest.contentDescription).isNull()

            fakeMobileIconsInteractor.filteredSubscriptions.value = listOf(SUB_1, SUB_2, SUB_3)
            assertThat(underTest.contentDescription).isNull()

            fakeMobileIconsInteractor.filteredSubscriptions.value = listOf(SUB_1, SUB_2)
            assertThat(underTest.contentDescription).isNotNull()
        }

    @Test
    @EnableFlags(NewStatusBarIcons.FLAG_NAME, StatusBarRootModernization.FLAG_NAME)
    fun contentDescription_tracksBars() =
        kosmos.runTest {
            fakeMobileIconsInteractor.filteredSubscriptions.value = listOf(SUB_1, SUB_2)
            setIconLevel(SUB_1.subscriptionId, 1)
            setIconLevel(SUB_2.subscriptionId, 2)

            assertThat(underTest.contentDescription!!)
                .isEqualTo("demo mode, one bar. demo mode, two bars.")

            // Change signal bars
            setIconLevel(SUB_1.subscriptionId, 3)
            setIconLevel(SUB_2.subscriptionId, 1)

            assertThat(underTest.contentDescription!!)
                .isEqualTo("demo mode, three bars. demo mode, one bar.")
        }

    @Test
    @EnableFlags(NewStatusBarIcons.FLAG_NAME, StatusBarRootModernization.FLAG_NAME)
    fun contentDescription_hasActiveIconFirst() =
        kosmos.runTest {
            // Active sub id is null, order is unchanged
            fakeMobileIconsInteractor.filteredSubscriptions.value = listOf(SUB_1, SUB_2)
            setIconLevel(SUB_1.subscriptionId, 1)
            setIconLevel(SUB_2.subscriptionId, 2)

            assertThat(underTest.contentDescription!!)
                .isEqualTo("demo mode, one bar. demo mode, two bars.")

            // Active sub is 2, order is swapped
            fakeMobileIconsInteractor.activeMobileDataSubscriptionId.value = SUB_2.subscriptionId

            assertThat(underTest.contentDescription!!)
                .isEqualTo("demo mode, two bars. demo mode, one bar.")
        }

    @Test
    @EnableFlags(NewStatusBarIcons.FLAG_NAME, StatusBarRootModernization.FLAG_NAME)
    fun contentDescription_tracksVisibility() =
        kosmos.runTest {
            fakeMobileIconsInteractor.filteredSubscriptions.value = listOf(SUB_1, SUB_2)
            assertThat(underTest.contentDescription).isNotNull()

            fakeMobileIconsInteractor.isStackable.value = false
            assertThat(underTest.contentDescription).isNull()
        }

    @Test
    @EnableFlags(NewStatusBarIcons.FLAG_NAME, StatusBarRootModernization.FLAG_NAME)
    fun mobileContext_tracksConnections() =
        kosmos.runTest {
            fakeMobileIconsInteractor.filteredSubscriptions.value = listOf()
            assertThat(underTest.mobileContext).isNull()

            fakeMobileIconsInteractor.filteredSubscriptions.value = listOf(SUB_1, SUB_2)
            assertThat(underTest.mobileContext).isNotNull()
        }

    @Test
    @EnableFlags(NewStatusBarIcons.FLAG_NAME, StatusBarRootModernization.FLAG_NAME)
    fun mobileContext_tracksPrimaryConnection() =
        kosmos.runTest {
            val contextSub1 = mock(Context::class.java)
            val contextSub2 = mock(Context::class.java)
            whenever(mobileContextProvider.getMobileContextForSub(eq(SUB_1.subscriptionId), any()))
                .thenReturn(contextSub1)
            whenever(mobileContextProvider.getMobileContextForSub(eq(SUB_2.subscriptionId), any()))
                .thenReturn(contextSub2)

            fakeMobileIconsInteractor.filteredSubscriptions.value = listOf(SUB_1, SUB_2)
            assertThat(underTest.mobileContext).isEqualTo(contextSub1)

            fakeMobileIconsInteractor.filteredSubscriptions.value = listOf(SUB_2, SUB_1)
            assertThat(underTest.mobileContext).isEqualTo(contextSub2)
        }

    @Test
    @EnableFlags(NewStatusBarIcons.FLAG_NAME, StatusBarRootModernization.FLAG_NAME)
    fun roaming_primaryConnectionIsRoaming_true() =
        kosmos.runTest {
            fakeMobileIconsInteractor.filteredSubscriptions.value = listOf(SUB_1, SUB_2)
            fakeMobileIconsInteractor.activeMobileDataSubscriptionId.value = SUB_1.subscriptionId
            val intr1 = fakeMobileIconsInteractor.getInteractorForSubId(SUB_1.subscriptionId)!!

            intr1.isRoaming.value = true

            assertThat(underTest.roaming).isTrue()
        }

    @Test
    @EnableFlags(NewStatusBarIcons.FLAG_NAME, StatusBarRootModernization.FLAG_NAME)
    fun roaming_secondaryConnectionIsRoaming_false() =
        kosmos.runTest {
            fakeMobileIconsInteractor.filteredSubscriptions.value = listOf(SUB_1, SUB_2)
            fakeMobileIconsInteractor.activeMobileDataSubscriptionId.value = SUB_1.subscriptionId
            val intr2 = fakeMobileIconsInteractor.getInteractorForSubId(SUB_2.subscriptionId)!!

            intr2.isRoaming.value = true

            assertThat(underTest.roaming).isFalse()
        }

    @Test
    @EnableFlags(NewStatusBarIcons.FLAG_NAME, StatusBarRootModernization.FLAG_NAME)
    fun activityInVisible_tracksPrimaryConnection() =
        kosmos.runTest {
            fakeMobileIconsInteractor.filteredSubscriptions.value = listOf(SUB_1, SUB_2)
            fakeMobileIconsInteractor.activeMobileDataSubscriptionId.value = SUB_1.subscriptionId

            setActivity(
                SUB_1.subscriptionId,
                DataActivityModel(hasActivityIn = true, hasActivityOut = false),
            )

            assertThat(underTest.activityInVisible).isTrue()
            assertThat(underTest.activityContainerVisible).isTrue()

            setActivity(
                SUB_1.subscriptionId,
                DataActivityModel(hasActivityIn = false, hasActivityOut = false),
            )

            assertThat(underTest.activityInVisible).isFalse()
            assertThat(underTest.activityContainerVisible).isFalse()

            // Change the activity for the secondary connection
            setActivity(
                SUB_2.subscriptionId,
                DataActivityModel(hasActivityIn = true, hasActivityOut = false),
            )

            // Assert the stacked icon activity is unchanged
            assertThat(underTest.activityInVisible).isFalse()
            assertThat(underTest.activityContainerVisible).isFalse()
        }

    @Test
    @EnableFlags(NewStatusBarIcons.FLAG_NAME, StatusBarRootModernization.FLAG_NAME)
    fun activityOutVisible_tracksPrimaryConnection() =
        kosmos.runTest {
            fakeMobileIconsInteractor.filteredSubscriptions.value = listOf(SUB_1, SUB_2)
            fakeMobileIconsInteractor.activeMobileDataSubscriptionId.value = SUB_1.subscriptionId

            setActivity(
                SUB_1.subscriptionId,
                DataActivityModel(hasActivityIn = false, hasActivityOut = true),
            )

            assertThat(underTest.activityOutVisible).isTrue()
            assertThat(underTest.activityContainerVisible).isTrue()

            setActivity(
                SUB_1.subscriptionId,
                DataActivityModel(hasActivityIn = false, hasActivityOut = false),
            )

            assertThat(underTest.activityOutVisible).isFalse()
            assertThat(underTest.activityContainerVisible).isFalse()

            // Change the activity for the secondary connection
            setActivity(
                SUB_2.subscriptionId,
                DataActivityModel(hasActivityIn = false, hasActivityOut = true),
            )

            // Assert the stacked icon activity is unchanged
            assertThat(underTest.activityOutVisible).isFalse()
            assertThat(underTest.activityContainerVisible).isFalse()
        }

    private fun setIconLevel(subId: Int, level: Int) {
        with(kosmos.fakeMobileIconsInteractor.getInteractorForSubId(subId)!!) {
            signalLevelIcon.value =
                (signalLevelIcon.value as SignalIconModel.Cellular).copy(level = level)
        }
    }

    private fun setActivity(subId: Int, activity: DataActivityModel) {
        with(kosmos.fakeMobileIconsInteractor.getInteractorForSubId(subId)!!) {
            this.activity.value = activity
        }
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
