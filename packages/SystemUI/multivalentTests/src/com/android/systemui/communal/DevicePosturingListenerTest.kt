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

package com.android.systemui.communal

import android.content.applicationContext
import android.service.dream.dreamManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.internal.logging.fake
import com.android.internal.logging.uiEventLogger
import com.android.systemui.SysuiTestCase
import com.android.systemui.communal.posturing.data.model.PositionState
import com.android.systemui.communal.posturing.data.repository.fake
import com.android.systemui.communal.posturing.data.repository.posturingRepository
import com.android.systemui.communal.posturing.domain.interactor.advanceTimeByBatchingDuration
import com.android.systemui.communal.posturing.domain.interactor.advanceTimeBySlidingWindowAndRun
import com.android.systemui.communal.posturing.domain.interactor.posturingInteractor
import com.android.systemui.communal.posturing.shared.model.ConfidenceLevel
import com.android.systemui.communal.shared.log.CommunalUiEvent
import com.android.systemui.dreams.data.repository.dreamSettingsRepository
import com.android.systemui.dreams.data.repository.fake
import com.android.systemui.dreams.domain.interactor.dreamSettingsInteractorKosmos
import com.android.systemui.dreams.shared.model.WhenToDream
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.Kosmos.Fixture
import com.android.systemui.kosmos.advanceTimeBy
import com.android.systemui.kosmos.applicationCoroutineScope
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.log.table.logcatTableLogBuffer
import com.android.systemui.power.domain.interactor.PowerInteractor.Companion.setAwakeForTest
import com.android.systemui.power.domain.interactor.powerInteractor
import com.android.systemui.statusbar.commandline.commandRegistry
import com.android.systemui.statusbar.pipeline.battery.domain.interactor.batteryInteractor
import com.android.systemui.statusbar.policy.batteryController
import com.android.systemui.statusbar.policy.fake
import com.android.systemui.testKosmos
import com.android.systemui.util.wakelock.WakeLock
import com.android.systemui.util.wakelock.WakeLockFake
import com.google.common.truth.Truth.assertThat
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.test.advanceTimeBy
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.verify
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.never

@SmallTest
@RunWith(AndroidJUnit4::class)
class DevicePosturingListenerTest : SysuiTestCase() {
    private val kosmos = testKosmos().useUnconfinedTestDispatcher()

    private val Kosmos.wakeLock by Fixture<WakeLock> { WakeLockFake() }

    private val Kosmos.wakeLockBuilder by
        Fixture<WakeLock.Builder> {
            WakeLockFake.Builder(applicationContext).apply { setWakeLock(wakeLock) }
        }

    private val Kosmos.underTest by Fixture {
        DevicePosturingListener(
            commandRegistry,
            dreamManager,
            posturingInteractor,
            dreamSettingsInteractorKosmos,
            batteryInteractor,
            applicationCoroutineScope,
            logcatTableLogBuffer(kosmos, "DevicePosturingListenerTest"),
            wakeLockBuilder,
            powerInteractor,
            uiEventLogger,
        )
    }

    @Before
    fun setUp() {
        kosmos.dreamSettingsRepository.fake.setDreamsEnabled(true)
        kosmos.dreamSettingsRepository.fake.setWhenToDream(WhenToDream.WHILE_POSTURED)
        kosmos.batteryController.fake._isPluggedIn = true
    }

    @Test
    fun testDevicePostured_logsEventAndSetsDreamManager() =
        kosmos.runTest {
            underTest.start()

            posturingRepository.fake.emitPositionState(POSTURED)
            advanceTimeBySlidingWindowAndRun()

            verify(dreamManager).setDevicePostured(true)
            assertThat(uiEventLogger.fake.numLogs()).isEqualTo(1)
            assertThat(uiEventLogger.fake.eventId(0))
                .isEqualTo(CommunalUiEvent.COMMUNAL_DEVICE_POSTURED.id)
        }

    @Test
    fun testDeviceNotPostured_setsDreamManagerAndLogs() =
        kosmos.runTest {
            underTest.start()

            assertThat(uiEventLogger.fake.numLogs()).isEqualTo(0)

            // Start as postured.
            posturingRepository.fake.emitPositionState(POSTURED)
            advanceTimeBySlidingWindowAndRun()
            verify(dreamManager).setDevicePostured(true)
            assertThat(uiEventLogger.fake.numLogs()).isEqualTo(1)
            assertThat(uiEventLogger.fake.eventId(0))
                .isEqualTo(CommunalUiEvent.COMMUNAL_DEVICE_POSTURED.id)
            clearInvocations(dreamManager)

            // Become not postured.
            posturingRepository.fake.emitPositionState(NOT_POSTURED)
            advanceTimeByBatchingDuration()

            verify(dreamManager).setDevicePostured(false)
            // Log for unpostured.
            assertThat(uiEventLogger.fake.numLogs()).isEqualTo(2)
            assertThat(uiEventLogger.fake.eventId(1))
                .isEqualTo(CommunalUiEvent.COMMUNAL_DEVICE_UNPOSTURED.id)
        }

    @Test
    fun testPreconditionsNotMet_doesNotListenToPosturing() =
        kosmos.runTest {
            batteryController.fake._isPluggedIn = false
            underTest.start()

            posturingRepository.fake.emitPositionState(POSTURED)
            advanceTimeBySlidingWindowAndRun()

            verify(dreamManager, never()).setDevicePostured(true)
            assertThat(uiEventLogger.fake.numLogs()).isEqualTo(0)
        }

    @Test
    fun mayBePosturedSoon_acquiresWakelock() =
        kosmos.runTest {
            powerInteractor.setAwakeForTest()
            underTest.start()

            posturingRepository.fake.emitPositionState(NOT_POSTURED)
            advanceTimeByBatchingDuration()

            // May be postured soon.
            posturingRepository.fake.emitPositionState(POSTURED)
            advanceTimeByBatchingDuration()

            // Verify wakelock acquired.
            assertThat((wakeLock as WakeLockFake).isHeld).isTrue()
        }

    @Test
    fun noLongerMayBePosturedSoon_releasesWakelockAfterDebounce() =
        kosmos.runTest {
            powerInteractor.setAwakeForTest()
            underTest.start()

            posturingRepository.fake.emitPositionState(NOT_POSTURED)
            advanceTimeByBatchingDuration()

            // May be postured soon.
            posturingRepository.fake.emitPositionState(POSTURED)
            advanceTimeByBatchingDuration()

            // May be postured soon, so wakelock is acquired.
            assertThat((wakeLock as WakeLockFake).isHeld).isTrue()

            // No longer may be postured soon.
            posturingRepository.fake.emitPositionState(NOT_POSTURED)
            advanceTimeByBatchingDuration()
            advanceTimeBy(501.milliseconds) // Debounce is 500ms

            // Verify wakelock released.
            assertThat((wakeLock as WakeLockFake).isHeld).isFalse()
        }

    @Test
    fun testShellCommand_setPosturedState_postured() =
        kosmos.runTest {
            underTest.start()

            commandRegistry.onShellCommand(
                PrintWriter(StringWriter()),
                arrayOf("device-postured", "true"),
            )

            val postured by collectLastValue(posturingInteractor.postured)
            assertThat(postured).isTrue()
        }

    @Test
    fun testShellCommand_setPosturedState_notPostured() =
        kosmos.runTest {
            underTest.start()

            // First set to postured, to ensure the change is detected.
            commandRegistry.onShellCommand(
                PrintWriter(StringWriter()),
                arrayOf("device-postured", "true"),
            )

            commandRegistry.onShellCommand(
                PrintWriter(StringWriter()),
                arrayOf("device-postured", "false"),
            )

            val postured by collectLastValue(posturingInteractor.postured)
            assertThat(postured).isFalse()
        }

    @Test
    fun testShellCommand_setPosturedState_unknown() =
        kosmos.runTest {
            underTest.start()

            // First set to postured, to ensure the change is detected.
            commandRegistry.onShellCommand(
                PrintWriter(StringWriter()),
                arrayOf("device-postured", "true"),
            )

            commandRegistry.onShellCommand(
                PrintWriter(StringWriter()),
                arrayOf("device-postured", "clear"),
            )

            // When postured state is cleared, postured should be false
            val postured by collectLastValue(posturingInteractor.postured)
            assertThat(postured).isFalse()
        }

    private companion object {
        val POSTURED =
            PositionState(
                stationary = ConfidenceLevel.Positive(1f),
                postured = ConfidenceLevel.Positive(1f),
            )
        val NOT_POSTURED = PositionState(stationary = ConfidenceLevel.Negative(1f))
    }
}
