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

package com.android.systemui.accessibility.hearingaid

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SmallTest
class HearingDeviceNotificationDismissControllerTest : SysuiTestCase() {

    private lateinit var underTest: HearingDeviceNotificationDismissController

    @Before
    fun setUp() {
        underTest = HearingDeviceNotificationDismissController()
    }

    @Test
    fun updateAndCheckNotification_batteryHigh_returnTrue() {
        assertThat(underTest.updateAndCheckNotification(TEST_ADDRESS, TEST_BATTERY_HIGH)).isTrue()
    }

    @Test
    fun updateAndCheckNotification_dismissedAndBatteryAboveThreshold_returnFalse() {
        underTest.updateAndCheckNotification(TEST_ADDRESS, TEST_BATTERY_HIGH)
        underTest.dismissNotification(TEST_ADDRESS)

        assertThat(underTest.updateAndCheckNotification(TEST_ADDRESS, TEST_BATTERY_MEDIUM))
            .isFalse()
    }

    @Test
    fun updateAndCheckNotification_dismissedThenCrossLowThreshold_returnTrue() {
        underTest.updateAndCheckNotification(TEST_ADDRESS, TEST_BATTERY_MEDIUM)
        underTest.dismissNotification(TEST_ADDRESS)

        assertThat(underTest.updateAndCheckNotification(TEST_ADDRESS, TEST_BATTERY_LOW)).isTrue()
    }

    @Test
    fun updateAndCheckNotification_dismissedThenCrossVeryLowThreshold_returnTrue() {
        underTest.updateAndCheckNotification(TEST_ADDRESS, TEST_BATTERY_LOW)
        underTest.dismissNotification(TEST_ADDRESS)

        assertThat(underTest.updateAndCheckNotification(TEST_ADDRESS, TEST_BATTERY_VERY_LOW))
            .isTrue()
    }

    @Test
    fun updateAndCheckNotification_chargedAboveThresholdThenCrossLowThreshold_returnTrue() {
        underTest.updateAndCheckNotification(TEST_ADDRESS, TEST_BATTERY_LOW)
        underTest.dismissNotification(TEST_ADDRESS)
        underTest.updateAndCheckNotification(TEST_ADDRESS, TEST_BATTERY_MEDIUM)

        assertThat(underTest.updateAndCheckNotification(TEST_ADDRESS, TEST_BATTERY_LOW)).isTrue()
    }

    @Test
    fun removeDevice_clearDeviceDismissalState() {
        underTest.updateAndCheckNotification(TEST_ADDRESS, TEST_BATTERY_HIGH)
        underTest.dismissNotification(TEST_ADDRESS)
        underTest.removeDevice(TEST_ADDRESS)

        assertThat(underTest.updateAndCheckNotification(TEST_ADDRESS, TEST_BATTERY_HIGH)).isTrue()
    }

    @Test
    fun reset_clearAllDevicesDismissalState() {
        underTest.updateAndCheckNotification(TEST_ADDRESS, TEST_BATTERY_HIGH)
        underTest.dismissNotification(TEST_ADDRESS)
        underTest.reset()

        assertThat(underTest.updateAndCheckNotification(TEST_ADDRESS, TEST_BATTERY_HIGH)).isTrue()
    }

    companion object {
        private const val TEST_ADDRESS = "AA:BB:CC:DD:EE:FF"
        private const val TEST_BATTERY_HIGH = 90
        private const val TEST_BATTERY_MEDIUM = 50
        private const val TEST_BATTERY_LOW = 15 // <= 20%
        private const val TEST_BATTERY_VERY_LOW = 8 // <= 10%
    }
}
