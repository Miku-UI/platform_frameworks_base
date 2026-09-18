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

package com.android.systemui.dreams.ui.viewmodel

import android.content.Intent
import android.content.res.mainResources
import android.provider.Settings
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.internal.logging.testing.UiEventLoggerFake
import com.android.systemui.SysuiTestCase
import com.android.systemui.communal.contextualSetupRepository
import com.android.systemui.communal.data.repository.SetupState
import com.android.systemui.communal.domain.definition.UprightChargingSetupDefinition
import com.android.systemui.communal.fake
import com.android.systemui.dreams.ui.metrics.DreamSetupUiEvent
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.advanceUntilIdle
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testDispatcher
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.plugins.activityStarter
import com.android.systemui.res.R
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlin.time.Duration.Companion.days
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.eq
import org.mockito.ArgumentMatchers.isNull
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat

@OptIn(ExperimentalCoroutinesApi::class)
@SmallTest
@RunWith(AndroidJUnit4::class)
class DreamSetupViewModelTest : SysuiTestCase() {
    private val kosmos = testKosmos().useUnconfinedTestDispatcher()
    private val fakeUiEventLogger = UiEventLoggerFake()
    private val Kosmos.underTest by
        Kosmos.Fixture {
            DreamSetupViewModel(
                activityStarter,
                contextualSetupRepository,
                mainResources,
                fakeUiEventLogger,
            )
        }
    private val Kosmos.repository by Kosmos.Fixture { contextualSetupRepository.fake }
    private val Kosmos.starter by Kosmos.Fixture { activityStarter }

    private val flowId = UprightChargingSetupDefinition.FLOW_ID

    @Before
    fun setUp() {
        Dispatchers.setMain(kosmos.testDispatcher)
        overrideResource(R.integer.config_dream_setup_max_dismiss_count, 2)
        overrideResource(
            R.integer.config_dream_setup_snooze_duration_minutes,
            14.days.inWholeMinutes.toInt(),
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun onEvent_dismiss_incrementsFailureCount_andDismissesAfterMaxAttempts() =
        kosmos.runTest {
            val state by collectLastValue(repository.setupState(flowId))
            assertThat(state).isEqualTo(SetupState.NotStarted)

            underTest.onEvent(DreamSetupEvent.Dismiss)
            advanceUntilIdle()
            assertThat(state).isEqualTo(SetupState.NotStarted)
            assertThat(fakeUiEventLogger.eventId(0))
                .isEqualTo(DreamSetupUiEvent.DREAM_SETUP_DISMISSED.id)

            // 2nd Dismiss - should trigger permanent dismissal (max count is 2)
            underTest.onEvent(DreamSetupEvent.Dismiss)
            advanceUntilIdle()
            assertThat(state).isEqualTo(SetupState.Dismissed)
            assertThat(fakeUiEventLogger.eventId(1))
                .isEqualTo(DreamSetupUiEvent.DREAM_SETUP_DISMISSED.id)

            verify(starter, never()).postStartActivityDismissingKeyguard(any(), any(), any())
        }

    @Test
    fun onEvent_notNow_snoozesForTwoWeeks() =
        kosmos.runTest {
            val now = System.currentTimeMillis()
            val state by collectLastValue(repository.setupState(flowId))
            assertThat(state).isEqualTo(SetupState.NotStarted)

            underTest.onEvent(DreamSetupEvent.NotNow)
            advanceUntilIdle()

            assertThat(state).isInstanceOf(SetupState.Snoozed::class.java)
            val snoozed = state as SetupState.Snoozed
            val expectedDuration = 14.days.inWholeMilliseconds
            assertThat(snoozed.expirationTime.toEpochMilli()).isAtLeast(now + expectedDuration)
            assertThat(snoozed.expirationTime.toEpochMilli())
                .isAtMost(now + expectedDuration + 5000)

            assertThat(fakeUiEventLogger.eventId(0))
                .isEqualTo(DreamSetupUiEvent.DREAM_SETUP_SNOOZED.id)

            verify(starter, never()).postStartActivityDismissingKeyguard(any(), any(), any())
        }

    @Test
    fun onEvent_setUp_startsDreamSettings() =
        kosmos.runTest {
            val state by collectLastValue(repository.setupState(flowId))
            assertThat(state).isEqualTo(SetupState.NotStarted)

            underTest.onEvent(DreamSetupEvent.SetUp)
            advanceUntilIdle()

            assertThat(fakeUiEventLogger.eventId(0))
                .isEqualTo(DreamSetupUiEvent.DREAM_SETUP_TRIGGERED.id)

            verify(starter)
                .postStartActivityDismissingKeyguard(
                    argThat { intent ->
                        intent?.action == Settings.ACTION_DREAM_SETTINGS &&
                            intent.flags ==
                                (Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    },
                    eq(0),
                    isNull(),
                )
        }
}
