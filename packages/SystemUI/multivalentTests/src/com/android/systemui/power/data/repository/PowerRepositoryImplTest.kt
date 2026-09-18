/*
 * Copyright (C) 2022 The Android Open Source Project
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
 *
 */

package com.android.systemui.power.data.repository

import android.content.Intent
import android.os.PowerManager
import android.os.powerManager
import android.platform.test.flag.junit.FlagsParameterization
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.broadcast.broadcastDispatcher
import com.android.systemui.concurrency.fakeExecutor
import com.android.systemui.coroutines.collectLastValue
import com.android.systemui.coroutines.collectValues
import com.android.systemui.flags.andSceneContainer
import com.android.systemui.keyguard.userActivityNotifier
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.collectValues
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.testKosmosNew
import com.android.systemui.util.time.fakeSystemClock
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import platform.test.runner.parameterized.ParameterizedAndroidJunit4
import platform.test.runner.parameterized.Parameters

@SmallTest
@RunWith(ParameterizedAndroidJunit4::class)
class PowerRepositoryImplTest(flags: FlagsParameterization) : SysuiTestCase() {
    init {
        mSetFlagsRule.setFlagsParameterization(flags)
    }

    private val kosmos = testKosmosNew()

    private var isInteractive = true

    val manager: PowerManager =
        kosmos.powerManager.apply {
            whenever(this.isInteractive).then { this@PowerRepositoryImplTest.isInteractive }
        }

    private val Kosmos.underTest by
        Kosmos.Fixture {
            PowerRepositoryImpl(
                manager,
                context.applicationContext,
                kosmos.testScope.backgroundScope,
                kosmos.testScope.backgroundScope,
                kosmos.fakeSystemClock,
                kosmos.broadcastDispatcher,
                kosmos.userActivityNotifier,
            )
        }

    @Test
    fun isInteractive_emitsInitialTrueValueIfScreenWasOn() =
        kosmos.runTest {
            isInteractive = true
            val value by collectLastValue(underTest.isInteractive)

            verifyRegistered()
            assertThat(value).isTrue()
        }

    @Test
    fun isInteractive_emitsInitialFalseValueIfScreenWasOff() =
        kosmos.runTest {
            isInteractive = false
            val value by collectLastValue(underTest.isInteractive)

            verifyRegistered()
            assertThat(value).isFalse()
        }

    @Test
    fun isInteractive_emitsTrueWhenTheScreenTurnsOn() =
        kosmos.runTest {
            val value by collectLastValue(underTest.isInteractive)
            verifyRegistered()

            isInteractive = true

            broadcastDispatcher.sendIntentToMatchingReceiversOnly(
                context,
                Intent(Intent.ACTION_SCREEN_ON),
            )

            assertThat(value).isTrue()
        }

    @Test
    fun isInteractive_emitsFalseWhenTheScreenTurnsOff() =
        kosmos.runTest {
            val value by collectLastValue(underTest.isInteractive)
            verifyRegistered()

            isInteractive = false
            broadcastDispatcher.sendIntentToMatchingReceiversOnly(
                context,
                Intent(Intent.ACTION_SCREEN_OFF),
            )

            assertThat(value).isFalse()
        }

    @Test
    fun isInteractive_emitsCorrectlyOverTime() =
        kosmos.runTest {
            isInteractive = true
            val values by collectValues(underTest.isInteractive)
            verifyRegistered()

            isInteractive = false
            broadcastDispatcher.sendIntentToMatchingReceiversOnly(
                context,
                Intent(Intent.ACTION_SCREEN_OFF),
            )
            isInteractive = true
            broadcastDispatcher.sendIntentToMatchingReceiversOnly(
                context,
                Intent(Intent.ACTION_SCREEN_ON),
            )
            isInteractive = false
            broadcastDispatcher.sendIntentToMatchingReceiversOnly(
                context,
                Intent(Intent.ACTION_SCREEN_OFF),
            )

            assertThat(values).isEqualTo(listOf(true, false, true, false))
        }

    @Test
    fun wakeUp_notifiesPowerManager() {
        kosmos.fakeSystemClock.setUptimeMillis(345000)

        kosmos.underTest.wakeUp("fakeWhy", PowerManager.WAKE_REASON_GESTURE)

        val reasonCaptor = argumentCaptor<String>()
        verify(manager)
            .wakeUp(eq(345000L), eq(PowerManager.WAKE_REASON_GESTURE), reasonCaptor.capture())
        assertThat(reasonCaptor.firstValue).contains("fakeWhy")
    }

    @Test
    fun wakeUp_usesApplicationPackageName() {
        kosmos.underTest.wakeUp("fakeWhy", PowerManager.WAKE_REASON_GESTURE)

        val reasonCaptor = argumentCaptor<String>()
        verify(manager).wakeUp(any(), any(), reasonCaptor.capture())
        assertThat(reasonCaptor.firstValue).contains(context.applicationContext.packageName)
    }

    @Test
    fun userActivity_notifiesPowerManager() {
        kosmos.fakeSystemClock.setUptimeMillis(345000)

        kosmos.underTest.userTouch()
        kosmos.fakeExecutor.runAllReady()

        val flagsCaptor = argumentCaptor<Int>()
        verify(manager)
            .userActivity(
                eq(345000L),
                eq(PowerManager.USER_ACTIVITY_EVENT_TOUCH),
                flagsCaptor.capture(),
            )
        assertThat(flagsCaptor.firstValue)
            .isNotEqualTo(PowerManager.USER_ACTIVITY_FLAG_NO_CHANGE_LIGHTS)
        assertThat(flagsCaptor.firstValue).isNotEqualTo(PowerManager.USER_ACTIVITY_FLAG_INDIRECT)
    }

    @Test
    fun userActivity_notifiesPowerManager_noChangeLightsTrue() {
        kosmos.fakeSystemClock.setUptimeMillis(345000)

        kosmos.underTest.userTouch(noChangeLights = true)
        kosmos.fakeExecutor.runAllReady()

        val flagsCaptor = argumentCaptor<Int>()
        verify(manager)
            .userActivity(
                eq(345000L),
                eq(PowerManager.USER_ACTIVITY_EVENT_TOUCH),
                flagsCaptor.capture(),
            )
        assertThat(flagsCaptor.firstValue)
            .isEqualTo(PowerManager.USER_ACTIVITY_FLAG_NO_CHANGE_LIGHTS)
    }

    private fun Kosmos.verifyRegistered() {
        assertThat(broadcastDispatcher.numReceiversRegistered).isEqualTo(1)
    }

    private companion object {
        @JvmStatic
        @Parameters(name = "{0}")
        fun getParams(): List<FlagsParameterization> {
            return FlagsParameterization.allCombinationsOf().andSceneContainer()
        }
    }
}
