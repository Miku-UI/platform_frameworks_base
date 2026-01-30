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

package com.android.systemui.statusbar.chips.call.domain.interactor

import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.statusbar.notification.promoted.PromotedNotificationUi
import com.android.systemui.statusbar.phone.ongoingcall.data.repository.ongoingCallRepository
import com.android.systemui.statusbar.phone.ongoingcall.shared.model.OngoingCallModel
import com.android.systemui.statusbar.phone.ongoingcall.shared.model.OngoingCallTestHelper.addOngoingCallState
import com.android.systemui.statusbar.phone.ongoingcall.shared.model.OngoingCallTestHelper.removeOngoingCallState
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlin.test.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class CallChipInteractorTest : SysuiTestCase() {
    private val kosmos = testKosmos().useUnconfinedTestDispatcher()
    private val Kosmos.repo by Kosmos.Fixture { kosmos.ongoingCallRepository }

    private val Kosmos.underTest by Kosmos.Fixture { kosmos.callChipInteractor }

    @Test
    fun ongoingCallState_noCall_isNoCall() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.ongoingCallState)

            removeOngoingCallState(key = "testKey")

            assertThat(latest).isEqualTo(OngoingCallModel.NoCall)
        }

    @Test
    fun ongoingCallState_updatesCorrectly() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.ongoingCallState)

            addOngoingCallState(key = "testKey")
            assertThat(latest).isInstanceOf(OngoingCallModel.InCall::class.java)

            removeOngoingCallState(key = "testKey")
            assertThat(latest).isEqualTo(OngoingCallModel.NoCall)
        }

    @Test
    @DisableFlags(PromotedNotificationUi.FLAG_NAME)
    fun ongoingCallState_inCall_noRequestedPromotion_promotedNotifFlagOff_isInCall() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.ongoingCallState)

            addOngoingCallState(key = "testKey", requestedPromotion = false)

            assertThat(latest).isInstanceOf(OngoingCallModel.InCall::class.java)
        }

    @Test
    @EnableFlags(PromotedNotificationUi.FLAG_NAME)
    fun ongoingCallState_inCall_noRequestedPromotion_promotedNotifFlagOn_isInCall() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.ongoingCallState)

            addOngoingCallState(key = "testKey", requestedPromotion = false)

            assertThat(latest).isInstanceOf(OngoingCallModel.InCall::class.java)
        }

    @Test
    @DisableFlags(PromotedNotificationUi.FLAG_NAME)
    fun ongoingCallState_inCall_requestedPromotion_promotedNotifFlagOff_isInCall() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.ongoingCallState)

            addOngoingCallState(key = "testKey", requestedPromotion = true)

            assertThat(latest).isInstanceOf(OngoingCallModel.InCall::class.java)
        }

    @Test
    @EnableFlags(PromotedNotificationUi.FLAG_NAME)
    fun ongoingCallState_inCall_requestedPromotion_promotedNotifFlagOn_isNoCall() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.ongoingCallState)

            addOngoingCallState(key = "testKey", requestedPromotion = true)

            assertThat(latest).isInstanceOf(OngoingCallModel.NoCall::class.java)
        }
}
