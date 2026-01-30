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

package com.android.systemui.statusbar.systemstatusicons.alarm.ui.viewmodel

import android.app.AlarmManager
import android.app.PendingIntent
import android.platform.test.annotations.EnableFlags
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.common.shared.model.ContentDescription
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.lifecycle.activateIn
import com.android.systemui.res.R
import com.android.systemui.statusbar.policy.fakeNextAlarmController
import com.android.systemui.statusbar.systemstatusicons.SystemStatusIconsInCompose
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock

@SmallTest
@RunWith(AndroidJUnit4::class)
@EnableFlags(SystemStatusIconsInCompose.FLAG_NAME)
class NextAlarmIconViewModelTest : SysuiTestCase() {

    private val kosmos = testKosmos().useUnconfinedTestDispatcher()
    private val testScope = kosmos.testScope

    private val underTest: NextAlarmIconViewModel =
        kosmos.nextAlarmIconViewModelFactory.create(context).apply { activateIn(testScope) }

    @Test
    fun icon_visible_isCorrect() =
        kosmos.runTest {
            val alarmClockInfo = AlarmManager.AlarmClockInfo(1L, mock<PendingIntent>())
            fakeNextAlarmController.setNextAlarm(alarmClockInfo)

            assertThat(underTest.icon).isEqualTo(EXPECTED_ALARM_ICON)
        }

    @Test
    fun icon_notVisible_isNull() =
        kosmos.runTest {
            fakeNextAlarmController.setNextAlarm(null)

            assertThat(underTest.icon).isNull()
        }

    @Test
    fun visible_isFalse_byDefault() =
        kosmos.runTest {
            fakeNextAlarmController.setNextAlarm(null)

            assertThat(underTest.visible).isFalse()
        }

    @Test
    fun visible_alarmIsSet_isTrue() =
        kosmos.runTest {
            val alarmClockInfo = AlarmManager.AlarmClockInfo(1L, mock<PendingIntent>())
            fakeNextAlarmController.setNextAlarm(alarmClockInfo)

            assertThat(underTest.visible).isTrue()
        }

    @Test
    fun visible_alarmChanges_flips() =
        kosmos.runTest {
            assertThat(underTest.visible).isFalse()

            val alarmInfo = AlarmManager.AlarmClockInfo(1L, mock<PendingIntent>())
            fakeNextAlarmController.setNextAlarm(alarmInfo)
            assertThat(underTest.visible).isTrue()

            fakeNextAlarmController.setNextAlarm(null)
            assertThat(underTest.visible).isFalse()
        }

    companion object {
        private val EXPECTED_ALARM_ICON =
            Icon.Resource(
                resId = R.drawable.ic_alarm,
                contentDescription = ContentDescription.Resource(R.string.status_bar_alarm),
            )
    }
}
