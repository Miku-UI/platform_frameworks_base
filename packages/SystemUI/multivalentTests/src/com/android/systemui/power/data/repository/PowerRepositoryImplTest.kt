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

import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import android.os.powerManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.broadcast.BroadcastDispatcher
import com.android.systemui.concurrency.fakeExecutor
import com.android.systemui.coroutines.collectLastValue
import com.android.systemui.coroutines.collectValues
import com.android.systemui.keyguard.userActivityNotifier
import com.android.systemui.kosmos.testScope
import com.android.systemui.testKosmos
import com.android.systemui.util.mockito.any
import com.android.systemui.util.mockito.argumentCaptor
import com.android.systemui.util.mockito.capture
import com.android.systemui.util.mockito.eq
import com.android.systemui.util.time.FakeSystemClock
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.Mock
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.isNull
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when` as whenever
import org.mockito.MockitoAnnotations

@SmallTest
@RunWith(AndroidJUnit4::class)
class PowerRepositoryImplTest : SysuiTestCase() {
    private val kosmos = testKosmos()
    private val testScope = kosmos.testScope
    private val systemClock = FakeSystemClock()

    val manager: PowerManager = kosmos.powerManager
    @Mock private lateinit var dispatcher: BroadcastDispatcher
    @Captor private lateinit var receiverCaptor: ArgumentCaptor<BroadcastReceiver>
    @Captor private lateinit var filterCaptor: ArgumentCaptor<IntentFilter>

    private lateinit var underTest: PowerRepositoryImpl

    private var isInteractive = true

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        isInteractive = true
        whenever(manager.isInteractive).then { isInteractive }

        underTest =
            PowerRepositoryImpl(
                manager,
                context.applicationContext,
                kosmos.testScope.backgroundScope,
                systemClock,
                dispatcher,
                kosmos.userActivityNotifier,
            )
    }

    @Test
    fun isInteractive_emitsInitialTrueValueIfScreenWasOn() =
        testScope.runTest {
            isInteractive = true
            val value by collectLastValue(underTest.isInteractive)
            runCurrent()
            verifyRegistered()

            assertThat(value).isTrue()
        }

    @Test
    fun isInteractive_emitsInitialFalseValueIfScreenWasOff() =
        testScope.runTest {
            isInteractive = false
            val value by collectLastValue(underTest.isInteractive)
            runCurrent()
            verifyRegistered()

            assertThat(value).isFalse()
        }

    @Test
    fun isInteractive_emitsTrueWhenTheScreenTurnsOn() =
        testScope.runTest {
            val value by collectLastValue(underTest.isInteractive)
            runCurrent()
            verifyRegistered()

            isInteractive = true
            receiverCaptor.value.onReceive(context, Intent(Intent.ACTION_SCREEN_ON))

            assertThat(value).isTrue()
        }

    @Test
    fun isInteractive_emitsFalseWhenTheScreenTurnsOff() =
        testScope.runTest {
            val value by collectLastValue(underTest.isInteractive)
            runCurrent()
            verifyRegistered()

            isInteractive = false
            receiverCaptor.value.onReceive(context, Intent(Intent.ACTION_SCREEN_OFF))

            assertThat(value).isFalse()
        }

    @Test
    fun isInteractive_emitsCorrectlyOverTime() =
        testScope.runTest {
            val values by collectValues(underTest.isInteractive)
            runCurrent()
            verifyRegistered()

            isInteractive = false
            receiverCaptor.value.onReceive(context, Intent(Intent.ACTION_SCREEN_OFF))
            isInteractive = true
            receiverCaptor.value.onReceive(context, Intent(Intent.ACTION_SCREEN_ON))
            isInteractive = false
            receiverCaptor.value.onReceive(context, Intent(Intent.ACTION_SCREEN_OFF))

            assertThat(values).isEqualTo(listOf(false, true, false))
        }

    @Test
    fun wakeUp_notifiesPowerManager() {
        systemClock.setUptimeMillis(345000)

        underTest.wakeUp("fakeWhy", PowerManager.WAKE_REASON_GESTURE)

        val reasonCaptor = argumentCaptor<String>()
        verify(manager)
            .wakeUp(eq(345000L), eq(PowerManager.WAKE_REASON_GESTURE), capture(reasonCaptor))
        assertThat(reasonCaptor.value).contains("fakeWhy")
    }

    @Test
    fun wakeUp_usesApplicationPackageName() {
        underTest.wakeUp("fakeWhy", PowerManager.WAKE_REASON_GESTURE)

        val reasonCaptor = argumentCaptor<String>()
        verify(manager).wakeUp(any(), any(), capture(reasonCaptor))
        assertThat(reasonCaptor.value).contains(context.applicationContext.packageName)
    }

    @Test
    fun userActivity_notifiesPowerManager() {
        systemClock.setUptimeMillis(345000)

        underTest.userTouch()
        kosmos.fakeExecutor.runAllReady()

        val flagsCaptor = argumentCaptor<Int>()
        verify(manager)
            .userActivity(
                eq(345000L),
                eq(PowerManager.USER_ACTIVITY_EVENT_TOUCH),
                capture(flagsCaptor),
            )
        assertThat(flagsCaptor.value).isNotEqualTo(PowerManager.USER_ACTIVITY_FLAG_NO_CHANGE_LIGHTS)
        assertThat(flagsCaptor.value).isNotEqualTo(PowerManager.USER_ACTIVITY_FLAG_INDIRECT)
    }

    @Test
    fun userActivity_notifiesPowerManager_noChangeLightsTrue() {
        systemClock.setUptimeMillis(345000)

        underTest.userTouch(noChangeLights = true)
        kosmos.fakeExecutor.runAllReady()

        val flagsCaptor = argumentCaptor<Int>()
        verify(manager)
            .userActivity(
                eq(345000L),
                eq(PowerManager.USER_ACTIVITY_EVENT_TOUCH),
                capture(flagsCaptor),
            )
        assertThat(flagsCaptor.value).isEqualTo(PowerManager.USER_ACTIVITY_FLAG_NO_CHANGE_LIGHTS)
    }

    private fun verifyRegistered() {
        // We must verify with all arguments, even those that are optional because they have default
        // values because Mockito is forcing us to. Once we can use mockito-kotlin, we should be
        // able to remove this.
        verify(dispatcher)
            .registerReceiver(
                capture(receiverCaptor),
                capture(filterCaptor),
                isNull(),
                isNull(),
                anyInt(),
                isNull(),
            )
    }
}
