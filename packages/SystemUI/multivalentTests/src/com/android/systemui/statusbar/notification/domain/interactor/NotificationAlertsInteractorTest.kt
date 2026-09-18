/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.android.systemui.statusbar.notification.domain.interactor

import android.app.StatusBarManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.statusbar.disableflags.data.repository.fakeDisableFlagsRepository
import com.android.systemui.statusbar.disableflags.shared.model.DisableFlagsModel
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class NotificationAlertsInteractorTest : SysuiTestCase() {

    private val kosmos = testKosmos().useUnconfinedTestDispatcher()
    private val Kosmos.underTest by Kosmos.Fixture { notificationAlertsInteractorImpl }

    @Test
    fun disableFlags_notifAlertsNotDisabled_notifAlertsEnabledTrue() =
        kosmos.runTest {
            fakeDisableFlagsRepository.disableFlags.value =
                DisableFlagsModel(StatusBarManager.DISABLE_NONE, StatusBarManager.DISABLE2_NONE)
            assertThat(underTest.areNotificationAlertsEnabled()).isTrue()
        }

    @Test
    fun disableFlags_notifAlertsDisabled_notifAlertsEnabledFalse() =
        kosmos.runTest {
            fakeDisableFlagsRepository.disableFlags.value =
                DisableFlagsModel(
                    StatusBarManager.DISABLE_NOTIFICATION_ALERTS,
                    StatusBarManager.DISABLE2_NONE,
                )
            assertThat(underTest.areNotificationAlertsEnabled()).isFalse()
        }
}
