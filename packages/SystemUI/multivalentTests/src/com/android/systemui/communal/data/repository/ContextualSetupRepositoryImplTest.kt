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

package com.android.systemui.communal.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.coroutines.collectLastValue
import com.android.systemui.domain.interactor.sharedPreferencesInteractor
import com.android.systemui.kosmos.testDispatcher
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.testKosmos
import com.android.systemui.util.time.systemClock
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@SmallTest
@RunWith(AndroidJUnit4::class)
class ContextualSetupRepositoryImplTest : SysuiTestCase() {
    private val kosmos = testKosmos().useUnconfinedTestDispatcher()
    private val testScope = kosmos.testScope

    private val underTest: ContextualSetupRepository
        get() = kosmos.contextualSetupRepository

    private suspend fun getSharedPreferences(): SharedPreferences {
        return kosmos.sharedPreferencesInteractor
            .sharedPreferences(
                ContextualSetupRepositoryImpl.CONTEXTUAL_SETUP_PREFS,
                Context.MODE_PRIVATE,
            )
            .first()
    }

    @Test
    fun setupState_defaultsToNotStarted() =
        testScope.runTest {
            val setupState by collectLastValue(underTest.setupState("any_id"))
            assertThat(setupState).isEqualTo(SetupState.NotStarted)
        }

    @Test
    fun snooze_persistsAndEmitsSnoozedState() =
        testScope.runTest {
            val setupState by collectLastValue(underTest.setupState("id"))
            assertThat(setupState).isEqualTo(SetupState.NotStarted)

            testScope.advanceTimeBy(1000)
            underTest.snooze("id", 10.minutes)

            val expectedExpiration = Instant.ofEpochMilli(1000 + 10.minutes.inWholeMilliseconds)
            assertThat(setupState).isEqualTo(SetupState.Snoozed(expectedExpiration))
        }

    @Test
    fun snooze_restoresToNotStarted_afterExpiration() =
        testScope.runTest {
            val setupState by collectLastValue(underTest.setupState("id"))

            // Snooze for 10 minutes
            testScope.advanceTimeBy(1000)
            underTest.snooze("id", 10.minutes)
            val expectedExpiration = Instant.ofEpochMilli(1000 + 10.minutes.inWholeMilliseconds)
            assertThat(setupState).isEqualTo(SetupState.Snoozed(expectedExpiration))

            // Advance time past expiration
            testScope.advanceTimeBy(10.minutes.inWholeMilliseconds + 1)

            // Since setupState is a hot flow backed by SharedPreferences, it won't automatically
            // re-emit
            // just because time passed. We need to trigger a read or simulate a re-subscription
            // to verify the logic. However, in a real scenario, the Interactor would likely
            // re-collect or the conditions would change.
            // For this unit test, we can force a re-collection by calling setupState again.
            val refreshedState by collectLastValue(underTest.setupState("id"))
            assertThat(refreshedState).isEqualTo(SetupState.NotStarted)
        }

    @Test
    fun dismiss_persistsAndEmitsDismissedState() =
        testScope.runTest {
            val setupState by collectLastValue(underTest.setupState("id"))
            assertThat(setupState).isEqualTo(SetupState.NotStarted)

            underTest.dismiss("id")
            assertThat(setupState).isEqualTo(SetupState.Dismissed)
        }

    @Test
    fun incrementFailureCount_incrementsAndPersists() =
        testScope.runTest {
            val count1 = underTest.incrementFailureCount("id")
            assertThat(count1).isEqualTo(1)

            // Create a new instance to simulate a fresh start and verify persistence.
            val newInstance =
                ContextualSetupRepositoryImpl(
                    sharedPreferencesInteractor = kosmos.sharedPreferencesInteractor,
                    backgroundDispatcher = kosmos.testDispatcher,
                    systemClock = kosmos.systemClock,
                )
            val count2 = newInstance.incrementFailureCount("id")

            assertThat(count2).isEqualTo(2)
        }

    @Test
    fun complete_persistsAndEmitsCompletedState() =
        testScope.runTest {
            val setupState by collectLastValue(underTest.setupState("id"))
            assertThat(setupState).isEqualTo(SetupState.NotStarted)

            underTest.complete("id")
            assertThat(setupState).isEqualTo(SetupState.Completed)
        }

    @Test
    fun reset_persistsAndEmitsNotStartedState() =
        testScope.runTest {
            val setupState by collectLastValue(underTest.setupState("id"))

            underTest.dismiss("id")
            assertThat(setupState).isEqualTo(SetupState.Dismissed)

            underTest.reset("id")
            assertThat(setupState).isEqualTo(SetupState.NotStarted)
        }

    @Test
    fun allStateTransitions() =
        testScope.runTest {
            val setupState by collectLastValue(underTest.setupState("id"))
            assertThat(setupState).isEqualTo(SetupState.NotStarted)

            testScope.advanceTimeBy(1000)
            underTest.snooze("id", 10.minutes)
            val expectedExpiration = Instant.ofEpochMilli(1000 + 10.minutes.inWholeMilliseconds)
            assertThat(setupState).isEqualTo(SetupState.Snoozed(expectedExpiration))

            underTest.complete("id")
            assertThat(setupState).isEqualTo(SetupState.Completed)

            underTest.dismiss("id")
            assertThat(setupState).isEqualTo(SetupState.Dismissed)

            underTest.reset("id")
            assertThat(setupState).isEqualTo(SetupState.NotStarted)
        }

    @Test
    fun multipleFlows_doNotInterfere() =
        testScope.runTest {
            val setupState1 by collectLastValue(underTest.setupState("id1"))
            val setupState2 by collectLastValue(underTest.setupState("id2"))

            underTest.dismiss("id1")

            assertThat(setupState1).isEqualTo(SetupState.Dismissed)
            assertThat(setupState2).isEqualTo(SetupState.NotStarted)
        }

    @Test
    fun onSharedPreferenceChanged_updatesFlow() =
        testScope.runTest {
            val setupState by collectLastValue(underTest.setupState("id"))
            assertThat(setupState).isEqualTo(SetupState.NotStarted)

            // Simulate an external change to the SharedPreferences.
            getSharedPreferences().edit().putString("state_id", "COMPLETED").apply()

            assertThat(setupState).isEqualTo(SetupState.Completed)
        }

    @Test
    fun onSharedPreferenceChanged_withInvalidValue_defaultsToNotStarted() =
        testScope.runTest {
            val setupState by collectLastValue(underTest.setupState("id"))
            assertThat(setupState).isEqualTo(SetupState.NotStarted)

            // First set to a valid state
            getSharedPreferences().edit().putString("state_id", "COMPLETED").apply()

            assertThat(setupState).isEqualTo(SetupState.Completed)

            // Simulate an external change with an invalid value.
            getSharedPreferences().edit().putString("state_id", "INVALID_VALUE").apply()

            assertThat(setupState).isEqualTo(SetupState.NotStarted)
        }
}
