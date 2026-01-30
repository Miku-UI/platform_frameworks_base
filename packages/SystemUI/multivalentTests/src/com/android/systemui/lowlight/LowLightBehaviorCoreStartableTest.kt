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
package com.android.systemui.lowlight

import android.content.res.mockResources
import android.platform.test.annotations.EnableFlags
import android.provider.Settings
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.common.data.repository.batteryRepositoryDeprecated
import com.android.systemui.common.data.repository.fake
import com.android.systemui.common.domain.interactor.batteryInteractorDeprecated
import com.android.systemui.display.data.repository.displayRepository
import com.android.systemui.display.domain.interactor.displayStateInteractor
import com.android.systemui.dreams.domain.interactor.dreamSettingsInteractorKosmos
import com.android.systemui.keyguard.data.repository.fakeKeyguardRepository
import com.android.systemui.keyguard.domain.interactor.keyguardInteractor
import com.android.systemui.keyguard.shared.model.DozeStateModel
import com.android.systemui.keyguard.shared.model.DozeTransitionModel
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.advanceTimeBy
import com.android.systemui.kosmos.backgroundScope
import com.android.systemui.kosmos.runCurrent
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.lifecycle.FakeActivatable
import com.android.systemui.log.logcatLogBuffer
import com.android.systemui.lowlight.data.repository.ambientLightModeMonitorRepository
import com.android.systemui.lowlight.data.repository.lowLightRepository
import com.android.systemui.lowlight.data.repository.lowLightSettingsRepository
import com.android.systemui.lowlight.domain.interactor.ambientLightModeInteractor
import com.android.systemui.lowlight.domain.interactor.lowLightInteractor
import com.android.systemui.lowlight.domain.interactor.lowLightSettingInteractor
import com.android.systemui.lowlight.shared.model.LowLightDisplayBehavior
import com.android.systemui.lowlight.shell.lowLightBehaviorShellCommand
import com.android.systemui.lowlight.shell.lowLightShellCommand
import com.android.systemui.lowlightclock.LowLightLogger
import com.android.systemui.power.domain.interactor.PowerInteractor.Companion.setAwakeForTest
import com.android.systemui.power.domain.interactor.powerInteractor
import com.android.systemui.res.R
import com.android.systemui.statusbar.pipeline.battery.domain.interactor.batteryInteractor
import com.android.systemui.statusbar.pipeline.battery.shared.StatusBarUniversalBatteryDataSource
import com.android.systemui.statusbar.policy.batteryController
import com.android.systemui.statusbar.policy.fake
import com.android.systemui.testKosmos
import com.android.systemui.user.data.repository.fakeUserRepository
import com.android.systemui.user.domain.interactor.selectedUserInteractor
import com.android.systemui.user.domain.interactor.userLockedInteractor
import com.android.systemui.util.settings.fakeSettings
import com.google.common.truth.Truth.assertThat
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
@EnableFlags(android.os.Flags.FLAG_LOW_LIGHT_DREAM_BEHAVIOR)
class LowLightBehaviorCoreStartableTest : SysuiTestCase() {
    val kosmos = testKosmos().useUnconfinedTestDispatcher()

    private val Kosmos.logger: LowLightLogger by
        Kosmos.Fixture { LowLightLogger(logcatLogBuffer()) }

    private val Kosmos.underTest: LowLightBehaviorCoreStartable by
        Kosmos.Fixture {
            LowLightBehaviorCoreStartable(
                lowLightInteractor = lowLightInteractor,
                lowLightSettingsInteractor = lowLightSettingInteractor,
                dreamSettingsInteractor = dreamSettingsInteractorKosmos,
                displayStateInteractor = displayStateInteractor,
                logger = logger,
                userLockedInteractor = userLockedInteractor,
                keyguardInteractor = keyguardInteractor,
                powerInteractor = powerInteractor,
                ambientLightModeMonitorInteractor = ambientLightModeInteractor,
                uiEventLogger = mock(),
                lowLightBehaviorShellCommand = lowLightBehaviorShellCommand,
                lowLightShellCommand = lowLightShellCommand,
                scope = backgroundScope,
                batteryInteractorDeprecated = batteryInteractorDeprecated,
                batteryInteractor = batteryInteractor,
            )
        }

    private fun Kosmos.setDisplayOn(screenOn: Boolean) {
        displayRepository.setDefaultDisplayOff(!screenOn)
    }

    private fun Kosmos.setBatteryPluggedIn(pluggedIn: Boolean) {
        if (StatusBarUniversalBatteryDataSource.isEnabled) {
            batteryController.fake._isPluggedIn = pluggedIn
        } else {
            batteryRepositoryDeprecated.fake.setDevicePluggedIn(pluggedIn)
        }
    }

    private fun Kosmos.setDreamEnabled(enabled: Boolean) {
        fakeSettings.putBoolForUser(
            Settings.Secure.SCREENSAVER_ENABLED,
            enabled,
            selectedUserInteractor.getSelectedUserId(),
        )
    }

    private fun Kosmos.debounce() {
        advanceTimeBy(
            (LowLightBehaviorCoreStartable.DREAM_STATE_DEBOUNCE_DURATION_MS + 1).milliseconds
        )
    }

    private fun Kosmos.start() {
        underTest.start()
        debounce()
    }

    private fun Kosmos.setUserUnlocked(unlocked: Boolean) {
        fakeUserRepository.setUserUnlocked(selectedUserInteractor.getSelectedUserId(), unlocked)
    }

    private fun Kosmos.setDreaming(dreaming: Boolean) {
        fakeKeyguardRepository.setDreaming(dreaming)
    }

    private fun Kosmos.setAllowLowLightWhenLocked(allowed: Boolean) {
        whenever(mockResources.getBoolean(R.bool.config_allowLowLightBehaviorWhenLocked))
            .thenReturn(allowed)
    }

    private val action = FakeActivatable()

    @Before
    fun setUp() {
        kosmos.setDisplayOn(false)
        kosmos.setUserUnlocked(true)
        kosmos.powerInteractor.setAwakeForTest()
        kosmos.fakeKeyguardRepository.setKeyguardShowing(true)

        // Activate dreams on charge by default
        mContext.orCreateTestableResources.addOverride(
            com.android.internal.R.bool.config_dreamsEnabledByDefault,
            true,
        )
        mContext.orCreateTestableResources.addOverride(
            com.android.internal.R.bool.config_dreamsActivatedOnSleepByDefault,
            true,
        )
        mContext.orCreateTestableResources.addOverride(
            com.android.internal.R.bool.config_dreamsActivatedOnDockByDefault,
            false,
        )
        mContext.orCreateTestableResources.addOverride(
            com.android.internal.R.bool.config_dreamsActivatedOnPosturedByDefault,
            false,
        )

        runBlocking {
            kosmos.lowLightSettingsRepository.setLowLightDisplayBehaviorEnabled(true)
            kosmos.lowLightSettingsRepository.setLowLightDisplayBehavior(
                LowLightDisplayBehavior.LOW_LIGHT_DREAM
            )
        }
        kosmos.lowLightRepository.addAction(LowLightDisplayBehavior.LOW_LIGHT_DREAM, action)

        kosmos.setBatteryPluggedIn(true)
    }

    @Test
    fun testSetAmbientLowLightWhenInLowLight() =
        kosmos.runTest {
            start()

            // Turn on screen
            setDisplayOn(true)
            assertThat(action.activationCount).isEqualTo(0)
            setLowLightFromSensor(true)
            assertThat(action.activationCount).isEqualTo(1)
        }

    @Test
    fun testWhenAllowLowLightBehaviorWhenLockedAndUserLocked_lowLightBehaviorActivates() =
        kosmos.runTest {
            setUserUnlocked(false)
            setAllowLowLightWhenLocked(true)
            start()
            setDisplayOn(true)

            assertThat(action.activationCount).isEqualTo(1)
        }

    @Test
    fun testDreamDebounce() =
        kosmos.runTest {
            kosmos.fakeKeyguardRepository.setKeyguardShowing(false)
            setUserUnlocked(true)
            setDreamEnabled(true)
            setAllowLowLightWhenLocked(true)
            setDisplayOn(true)
            setLowLightFromSensor(true)
            start()
            setDreaming(true)
            assertThat(action.activationCount).isEqualTo(0)
            debounce()
            assertThat(action.activationCount).isEqualTo(1)
            setDreaming(false)
            assertThat(action.cancellationCount).isEqualTo(0)
            debounce()
            assertThat(action.cancellationCount).isEqualTo(1)
        }

    @Test
    fun testWhenDisallowLowLightBehaviorWhenLockedAndUserLocked_LowLightBehaviorDoesNotActivate() =
        kosmos.runTest {
            setUserUnlocked(false)
            setAllowLowLightWhenLocked(false)
            start()
            setDisplayOn(true)

            assertThat(action.activationCount).isEqualTo(0)
        }

    @Test
    fun testSetAmbientLowLightWhenDisabledInLowLight() =
        kosmos.runTest {
            lowLightSettingsRepository.setLowLightDisplayBehaviorEnabled(false)
            start()

            // Turn on screen
            setDisplayOn(true)
            setLowLightFromSensor(true)
            runCurrent()
            assertThat(action.activationCount).isEqualTo(0)
        }

    @Test
    fun testExitAmbientLowLightWhenNotInLowLight() =
        kosmos.runTest {
            // Turn on screen
            setDisplayOn(true)
            setLowLightFromSensor(true)

            start()

            assertThat(action.cancellationCount).isEqualTo(0)
            assertThat(action.activationCount).isEqualTo(1)
            setLowLightFromSensor(false)
            assertThat(action.cancellationCount).isEqualTo(1)
            assertThat(action.activationCount).isEqualTo(1)
        }

    @Test
    fun testStopMonitorLowLightConditionsWhenScreenTurnsOff() =
        kosmos.runTest {
            start()

            setDisplayOn(true)
            assertThat(ambientLightModeMonitor.fake.started).isTrue()

            // Verify removing subscription when screen turns off.
            setDisplayOn(false)
            assertThat(ambientLightModeMonitor.fake.started).isFalse()
        }

    @Test
    fun testStopMonitorLowLightConditionsWhenMonitorSwapped() =
        kosmos.runTest {
            start()

            setDisplayOn(true)
            assertThat(ambientLightModeMonitor.fake.started).isTrue()

            val secondMonitor: AmbientLightModeMonitor = mock()
            ambientLightModeMonitorRepository.setMonitor(secondMonitor)

            assertThat(ambientLightModeMonitor.fake.started).isFalse()
            verify(secondMonitor).start(any())
        }

    @Test
    fun testStopMonitorLowLightConditionsWhenDreamDisabled() =
        kosmos.runTest {
            start()

            setDisplayOn(true)
            setDreamEnabled(true)

            assertThat(ambientLightModeMonitor.fake.started).isTrue()

            setDreamEnabled(false)
            // Verify removing subscription when dream disabled.
            assertThat(ambientLightModeMonitor.fake.started).isFalse()
        }

    @Test
    fun testSubscribeIfScreenIsOnWhenStarting() =
        kosmos.runTest {
            setDisplayOn(true)

            start()
            assertThat(ambientLightModeMonitor.fake.started).isTrue()
        }

    @Test
    fun testSubscribeIfScreenIsOffForScreenOffBehaviorWhenStarting() =
        kosmos.runTest {
            lowLightRepository.addAction(LowLightDisplayBehavior.SCREEN_OFF, action)
            lowLightSettingsRepository.setLowLightDisplayBehavior(
                LowLightDisplayBehavior.SCREEN_OFF
            )

            setDisplayOn(true)

            start()
            assertThat(ambientLightModeMonitor.fake.started).isTrue()
        }

    @Test
    fun testSubscribeIfDozingForScreenOffBehavior() =
        kosmos.runTest {
            lowLightRepository.addAction(LowLightDisplayBehavior.SCREEN_OFF, action)
            lowLightSettingsRepository.setLowLightDisplayBehavior(
                LowLightDisplayBehavior.SCREEN_OFF
            )

            setBatteryPluggedIn(true)
            setDisplayOn(false)

            fakeKeyguardRepository.setDozeTransitionModel(
                DozeTransitionModel(from = DozeStateModel.UNINITIALIZED, to = DozeStateModel.DOZE)
            )

            start()
            assertThat(ambientLightModeMonitor.fake.started).isTrue()
        }

    @Test
    fun testDoNotSubscribeIfDozingForScreenOffBehaviorUnplugged() =
        kosmos.runTest {
            lowLightRepository.addAction(LowLightDisplayBehavior.SCREEN_OFF, action)
            lowLightSettingsRepository.setLowLightDisplayBehavior(
                LowLightDisplayBehavior.SCREEN_OFF
            )

            setBatteryPluggedIn(false)
            setDisplayOn(true)

            fakeKeyguardRepository.setDozeTransitionModel(
                DozeTransitionModel(from = DozeStateModel.UNINITIALIZED, to = DozeStateModel.DOZE)
            )

            start()
            assertThat(ambientLightModeMonitor.fake.started).isFalse()
        }

    @Test
    fun testDoNotSubscribeIfDozeForLowLightBehaviorPluggedIn() =
        kosmos.runTest {
            lowLightRepository.addAction(LowLightDisplayBehavior.LOW_LIGHT_DREAM, action)
            lowLightSettingsRepository.setLowLightDisplayBehavior(
                LowLightDisplayBehavior.LOW_LIGHT_DREAM
            )

            setBatteryPluggedIn(true)
            setDisplayOn(true)

            fakeKeyguardRepository.setDozeTransitionModel(
                DozeTransitionModel(from = DozeStateModel.UNINITIALIZED, to = DozeStateModel.DOZE)
            )

            start()
            assertThat(ambientLightModeMonitor.fake.started).isFalse()
        }

    @Test
    fun testDoNotSubscribeIfScreenOnNonIdleForScreenOffBehaviorPluggedIn() =
        kosmos.runTest {
            lowLightRepository.addAction(LowLightDisplayBehavior.SCREEN_OFF, action)
            lowLightSettingsRepository.setLowLightDisplayBehavior(
                LowLightDisplayBehavior.SCREEN_OFF
            )

            fakeKeyguardRepository.setDozeTransitionModel(
                DozeTransitionModel(from = DozeStateModel.UNINITIALIZED, to = DozeStateModel.DOZE)
            )

            setBatteryPluggedIn(true)
            setDisplayOn(true)

            start()
            assertThat(ambientLightModeMonitor.fake.started).isFalse()
        }

    private fun Kosmos.setLowLightFromSensor(lowlight: Boolean) {
        val lightMode =
            if (lowlight) {
                AmbientLightModeMonitor.AMBIENT_LIGHT_MODE_DARK
            } else {
                AmbientLightModeMonitor.AMBIENT_LIGHT_MODE_LIGHT
            }
        ambientLightModeMonitor.fake.setAmbientLightMode(lightMode)
    }
}
