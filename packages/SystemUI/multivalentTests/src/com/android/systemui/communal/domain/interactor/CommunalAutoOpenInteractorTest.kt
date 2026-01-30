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

package com.android.systemui.communal.domain.interactor

import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.provider.Settings
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.Flags.FLAG_GLANCEABLE_HUB_V2
import com.android.systemui.SysuiTestCase
import com.android.systemui.common.data.repository.batteryRepositoryDeprecated
import com.android.systemui.common.data.repository.fake
import com.android.systemui.communal.data.model.FEATURE_AUTO_OPEN
import com.android.systemui.communal.data.model.FEATURE_MANUAL_OPEN
import com.android.systemui.communal.data.model.SuppressionReason
import com.android.systemui.communal.posturing.data.model.PositionState
import com.android.systemui.communal.posturing.data.repository.fake
import com.android.systemui.communal.posturing.data.repository.posturingRepository
import com.android.systemui.communal.posturing.domain.interactor.advanceTimeBySlidingWindowAndRun
import com.android.systemui.communal.posturing.shared.model.ConfidenceLevel
import com.android.systemui.dock.DockManager
import com.android.systemui.dock.fakeDockManager
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.advanceTimeBy
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.statusbar.pipeline.battery.shared.StatusBarUniversalBatteryDataSource
import com.android.systemui.statusbar.policy.batteryController
import com.android.systemui.statusbar.policy.fake
import com.android.systemui.testKosmos
import com.android.systemui.user.data.repository.FakeUserRepository.Companion.MAIN_USER_ID
import com.android.systemui.user.data.repository.fakeUserRepository
import com.android.systemui.util.settings.fakeSettings
import com.google.common.truth.Truth.assertThat
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
@EnableFlags(FLAG_GLANCEABLE_HUB_V2)
class CommunalAutoOpenInteractorTest : SysuiTestCase() {
    private val kosmos = testKosmos().useUnconfinedTestDispatcher()

    private val Kosmos.underTest by Kosmos.Fixture { communalAutoOpenInteractor }

    @Before
    fun setUp() {
        kosmos.setCommunalV2ConfigEnabled(true)
        runBlocking { kosmos.fakeUserRepository.asMainUser() }
        with(kosmos.fakeSettings) {
            putIntForUser(
                Settings.Secure.WHEN_TO_START_GLANCEABLE_HUB,
                Settings.Secure.GLANCEABLE_HUB_START_NEVER,
                MAIN_USER_ID,
            )
        }
    }

    @Test
    @DisableFlags(StatusBarUniversalBatteryDataSource.FLAG_NAME)
    fun testStartWhileCharging_universalBatteryFlagOff() =
        kosmos.runTest {
            val shouldAutoOpen by collectLastValue(underTest.shouldAutoOpen)
            val suppressionReason by collectLastValue(underTest.suppressionReason)

            fakeSettings.putIntForUser(
                Settings.Secure.WHEN_TO_START_GLANCEABLE_HUB,
                Settings.Secure.GLANCEABLE_HUB_START_CHARGING,
                MAIN_USER_ID,
            )

            batteryRepositoryDeprecated.fake.setDevicePluggedIn(false)
            assertThat(shouldAutoOpen).isFalse()
            assertThat(suppressionReason)
                .isEqualTo(
                    SuppressionReason.ReasonWhenToAutoShow(FEATURE_AUTO_OPEN or FEATURE_MANUAL_OPEN)
                )

            batteryRepositoryDeprecated.fake.setDevicePluggedIn(true)
            assertThat(shouldAutoOpen).isTrue()
            assertThat(suppressionReason).isNull()
        }

    @Test
    @EnableFlags(StatusBarUniversalBatteryDataSource.FLAG_NAME)
    fun testStartWhileCharging_universalBatteryFlagOn() =
        kosmos.runTest {
            val shouldAutoOpen by collectLastValue(underTest.shouldAutoOpen)
            val suppressionReason by collectLastValue(underTest.suppressionReason)

            fakeSettings.putIntForUser(
                Settings.Secure.WHEN_TO_START_GLANCEABLE_HUB,
                Settings.Secure.GLANCEABLE_HUB_START_CHARGING,
                MAIN_USER_ID,
            )

            batteryController.fake._isPluggedIn = false
            assertThat(shouldAutoOpen).isFalse()
            assertThat(suppressionReason)
                .isEqualTo(
                    SuppressionReason.ReasonWhenToAutoShow(FEATURE_AUTO_OPEN or FEATURE_MANUAL_OPEN)
                )

            batteryController.fake._isPluggedIn = true
            assertThat(shouldAutoOpen).isTrue()
            assertThat(suppressionReason).isNull()
        }

    @Test
    @DisableFlags(StatusBarUniversalBatteryDataSource.FLAG_NAME)
    fun testStartWhileDocked_universalBatteryFlagOff() =
        kosmos.runTest {
            val shouldAutoOpen by collectLastValue(underTest.shouldAutoOpen)
            val suppressionReason by collectLastValue(underTest.suppressionReason)

            fakeSettings.putIntForUser(
                Settings.Secure.WHEN_TO_START_GLANCEABLE_HUB,
                Settings.Secure.GLANCEABLE_HUB_START_DOCKED,
                MAIN_USER_ID,
            )

            batteryRepositoryDeprecated.fake.setDevicePluggedIn(true)
            fakeDockManager.setIsDocked(false)

            assertThat(shouldAutoOpen).isFalse()
            assertThat(suppressionReason)
                .isEqualTo(
                    SuppressionReason.ReasonWhenToAutoShow(FEATURE_AUTO_OPEN or FEATURE_MANUAL_OPEN)
                )

            fakeDockManager.setIsDocked(true)
            fakeDockManager.setDockEvent(DockManager.STATE_DOCKED)
            assertThat(shouldAutoOpen).isTrue()
            assertThat(suppressionReason).isNull()
        }

    @Test
    @EnableFlags(StatusBarUniversalBatteryDataSource.FLAG_NAME)
    fun testStartWhileDocked_universalBatteryFlagOn() =
        kosmos.runTest {
            val shouldAutoOpen by collectLastValue(underTest.shouldAutoOpen)
            val suppressionReason by collectLastValue(underTest.suppressionReason)

            fakeSettings.putIntForUser(
                Settings.Secure.WHEN_TO_START_GLANCEABLE_HUB,
                Settings.Secure.GLANCEABLE_HUB_START_DOCKED,
                MAIN_USER_ID,
            )

            batteryController.fake._isPluggedIn = true
            fakeDockManager.setIsDocked(false)

            assertThat(shouldAutoOpen).isFalse()
            assertThat(suppressionReason)
                .isEqualTo(
                    SuppressionReason.ReasonWhenToAutoShow(FEATURE_AUTO_OPEN or FEATURE_MANUAL_OPEN)
                )

            fakeDockManager.setIsDocked(true)
            fakeDockManager.setDockEvent(DockManager.STATE_DOCKED)
            assertThat(shouldAutoOpen).isTrue()
            assertThat(suppressionReason).isNull()
        }

    @Test
    @DisableFlags(StatusBarUniversalBatteryDataSource.FLAG_NAME)
    fun testStartWhilePostured_universalBatteryFlagOff() =
        kosmos.runTest {
            val shouldAutoOpen by collectLastValue(underTest.shouldAutoOpen)
            val suppressionReason by collectLastValue(underTest.suppressionReason)

            fakeSettings.putIntForUser(
                Settings.Secure.WHEN_TO_START_GLANCEABLE_HUB,
                Settings.Secure.GLANCEABLE_HUB_START_CHARGING_UPRIGHT,
                MAIN_USER_ID,
            )

            batteryRepositoryDeprecated.fake.setDevicePluggedIn(true)
            posturingRepository.fake.emitPositionState(
                PositionState(
                    stationary = ConfidenceLevel.Positive(confidence = 1f),
                    orientation = ConfidenceLevel.Negative(confidence = 1f),
                )
            )

            assertThat(shouldAutoOpen).isFalse()
            assertThat(suppressionReason)
                .isEqualTo(
                    SuppressionReason.ReasonWhenToAutoShow(FEATURE_AUTO_OPEN or FEATURE_MANUAL_OPEN)
                )

            advanceTimeBy(1.milliseconds)
            posturingRepository.fake.emitPositionState(
                PositionState(
                    stationary = ConfidenceLevel.Positive(confidence = 1f),
                    orientation = ConfidenceLevel.Positive(confidence = 1f),
                )
            )
            advanceTimeBySlidingWindowAndRun()
            assertThat(shouldAutoOpen).isTrue()
            assertThat(suppressionReason).isNull()
        }

    @Test
    @EnableFlags(StatusBarUniversalBatteryDataSource.FLAG_NAME)
    fun testStartWhilePostured_universalBatteryFlagOn() =
        kosmos.runTest {
            val shouldAutoOpen by collectLastValue(underTest.shouldAutoOpen)
            val suppressionReason by collectLastValue(underTest.suppressionReason)

            fakeSettings.putIntForUser(
                Settings.Secure.WHEN_TO_START_GLANCEABLE_HUB,
                Settings.Secure.GLANCEABLE_HUB_START_CHARGING_UPRIGHT,
                MAIN_USER_ID,
            )

            batteryController.fake._isPluggedIn = true
            posturingRepository.fake.emitPositionState(
                PositionState(
                    stationary = ConfidenceLevel.Positive(confidence = 1f),
                    orientation = ConfidenceLevel.Negative(confidence = 1f),
                )
            )

            assertThat(shouldAutoOpen).isFalse()
            assertThat(suppressionReason)
                .isEqualTo(
                    SuppressionReason.ReasonWhenToAutoShow(FEATURE_AUTO_OPEN or FEATURE_MANUAL_OPEN)
                )

            advanceTimeBy(1.milliseconds)
            posturingRepository.fake.emitPositionState(
                PositionState(
                    stationary = ConfidenceLevel.Positive(confidence = 1f),
                    orientation = ConfidenceLevel.Positive(confidence = 1f),
                )
            )
            advanceTimeBySlidingWindowAndRun()
            assertThat(shouldAutoOpen).isTrue()
            assertThat(suppressionReason).isNull()
        }

    @Test
    @DisableFlags(StatusBarUniversalBatteryDataSource.FLAG_NAME)
    fun testStartNever_universalBatteryFlagOff() =
        kosmos.runTest {
            val shouldAutoOpen by collectLastValue(underTest.shouldAutoOpen)
            val suppressionReason by collectLastValue(underTest.suppressionReason)

            fakeSettings.putIntForUser(
                Settings.Secure.WHEN_TO_START_GLANCEABLE_HUB,
                Settings.Secure.GLANCEABLE_HUB_START_NEVER,
                MAIN_USER_ID,
            )

            batteryRepositoryDeprecated.fake.setDevicePluggedIn(true)
            posturingRepository.fake.emitPositionState(PositionState())
            fakeDockManager.setIsDocked(true)

            assertThat(shouldAutoOpen).isFalse()
            assertThat(suppressionReason)
                .isEqualTo(
                    SuppressionReason.ReasonWhenToAutoShow(FEATURE_AUTO_OPEN or FEATURE_MANUAL_OPEN)
                )
        }

    @Test
    @EnableFlags(StatusBarUniversalBatteryDataSource.FLAG_NAME)
    fun testStartNever_universalBatteryFlagOn() =
        kosmos.runTest {
            val shouldAutoOpen by collectLastValue(underTest.shouldAutoOpen)
            val suppressionReason by collectLastValue(underTest.suppressionReason)

            fakeSettings.putIntForUser(
                Settings.Secure.WHEN_TO_START_GLANCEABLE_HUB,
                Settings.Secure.GLANCEABLE_HUB_START_NEVER,
                MAIN_USER_ID,
            )

            batteryController.fake._isPluggedIn = true
            posturingRepository.fake.emitPositionState(PositionState())
            fakeDockManager.setIsDocked(true)

            assertThat(shouldAutoOpen).isFalse()
            assertThat(suppressionReason)
                .isEqualTo(
                    SuppressionReason.ReasonWhenToAutoShow(FEATURE_AUTO_OPEN or FEATURE_MANUAL_OPEN)
                )
        }
}
