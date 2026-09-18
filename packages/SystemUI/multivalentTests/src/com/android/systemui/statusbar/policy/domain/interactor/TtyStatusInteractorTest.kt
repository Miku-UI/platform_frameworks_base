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

package com.android.systemui.statusbar.policy.domain.interactor

import android.content.Intent
import android.telecom.TelecomManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.broadcast.broadcastDispatcher
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.testKosmos
import com.android.telecom.mockTelecomManager
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
class TtyStatusInteractorTest : SysuiTestCase() {

    private val kosmos = testKosmos().useUnconfinedTestDispatcher()
    private val Kosmos.underTest by Kosmos.Fixture { ttyStatusInteractor }

    @Test
    fun isEnabled_initialValueIsFalse() =
        kosmos.runTest {
            whenever(mockTelecomManager.currentTtyMode).thenReturn(TelecomManager.TTY_MODE_OFF)
            val state by collectLastValue(underTest.isEnabled)
            assertThat(state).isFalse()
        }

    @Test
    fun isEnabled_onBroadcast_TtyModeFull_isTrue() =
        kosmos.runTest {
            whenever(mockTelecomManager.currentTtyMode).thenReturn(TelecomManager.TTY_MODE_OFF)
            val isEnabled by collectLastValue(underTest.isEnabled)
            assertThat(isEnabled).isFalse()

            sendTtyModeBroadcast(TelecomManager.TTY_MODE_FULL)

            assertThat(isEnabled).isTrue()
        }

    @Test
    fun isEnabled_onBroadcast_TtyModeHco_isTrue() =
        kosmos.runTest {
            whenever(mockTelecomManager.currentTtyMode).thenReturn(TelecomManager.TTY_MODE_OFF)
            val isEnabled by collectLastValue(underTest.isEnabled)
            assertThat(isEnabled).isFalse()

            sendTtyModeBroadcast(TelecomManager.TTY_MODE_HCO)

            assertThat(isEnabled).isTrue()
        }

    @Test
    fun isEnabled_onBroadcast_TtyModeVco_isTrue() =
        kosmos.runTest {
            whenever(mockTelecomManager.currentTtyMode).thenReturn(TelecomManager.TTY_MODE_OFF)
            val isEnabled by collectLastValue(underTest.isEnabled)
            assertThat(isEnabled).isFalse()

            sendTtyModeBroadcast(TelecomManager.TTY_MODE_VCO)

            assertThat(isEnabled).isTrue()
        }

    @Test
    fun isEnabled_onBroadcast_TtyModeOff_isFalse() =
        kosmos.runTest {
            whenever(mockTelecomManager.currentTtyMode).thenReturn(TelecomManager.TTY_MODE_FULL)
            val isEnabled by collectLastValue(underTest.isEnabled)
            sendTtyModeBroadcast(TelecomManager.TTY_MODE_FULL)
            assertThat(isEnabled).isTrue()

            sendTtyModeBroadcast(TelecomManager.TTY_MODE_OFF)
            assertThat(isEnabled).isFalse()
        }

    /** Helper function to send a TTY mode change broadcast. */
    private fun Kosmos.sendTtyModeBroadcast(ttyMode: Int) {
        val intent =
            Intent(TelecomManager.ACTION_CURRENT_TTY_MODE_CHANGED).apply {
                putExtra(TelecomManager.EXTRA_CURRENT_TTY_MODE, ttyMode)
            }
        broadcastDispatcher.sendIntentToMatchingReceiversOnly(context, intent)
    }
}
