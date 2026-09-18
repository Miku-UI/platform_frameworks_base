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

package com.android.systemui.communal.domain.definition

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.communal.commonSetupPreconditions
import com.android.systemui.communal.contextualSetupRepository
import com.android.systemui.communal.data.repository.SetupState
import com.android.systemui.communal.fake
import com.android.systemui.communal.uprightChargingInteractor
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@SmallTest
@RunWith(AndroidJUnit4::class)
class UprightChargingSetupDefinitionTest : SysuiTestCase() {
    private val kosmos = testKosmos()

    private val commonPreconditions = kosmos.commonSetupPreconditions.fake
    private val uprightChargingInteractor = kosmos.uprightChargingInteractor.fake
    private val contextualSetupRepo = kosmos.contextualSetupRepository.fake
    private val Kosmos.underTest by
        Kosmos.Fixture {
            context.orCreateTestableResources.addOverride(
                com.android.systemui.res.R.string
                    .config_communalUprightChargingSetupActivityComponent,
                "package/class",
            )
            UprightChargingSetupDefinition(
                commonConditions = commonPreconditions,
                uprightChargingInteractor = uprightChargingInteractor,
                contextualSetupRepo = contextualSetupRepo,
                resources = context.resources,
            )
        }

    private fun setTriggered(triggered: Boolean) {
        uprightChargingInteractor.setTriggered(triggered)
    }

    @Test
    fun isReady_preconditionsNotMet_isFalse() =
        kosmos.runTest {
            val isReady by collectLastValue(underTest.isReady)

            contextualSetupRepo.setSetupState(underTest.id, SetupState.NotStarted)
            setTriggered(true)

            commonPreconditions.setAllMet(true)
            assertThat(isReady).isTrue()

            commonPreconditions.setAllMet(false)
            assertThat(isReady).isFalse()
        }

    @Test
    fun isReady_preconditionsMet_triggerNotFired_isFalse() =
        kosmos.runTest {
            val isReady by collectLastValue(underTest.isReady)

            contextualSetupRepo.setSetupState(underTest.id, SetupState.NotStarted)
            commonPreconditions.setAllMet(true)

            setTriggered(true)
            assertThat(isReady).isTrue()

            setTriggered(false)
            assertThat(isReady).isFalse()
        }

    @Test
    fun isReady_preconditionsMet_triggerFired_isTrue() =
        kosmos.runTest {
            val isReady by collectLastValue(underTest.isReady)

            contextualSetupRepo.setSetupState(underTest.id, SetupState.NotStarted)
            commonPreconditions.setAllMet(true)
            setTriggered(true)

            assertThat(isReady).isTrue()
        }

    @Test
    fun isReady_setupCompleted_isFalse() =
        kosmos.runTest {
            val isReady by collectLastValue(underTest.isReady)

            contextualSetupRepo.setSetupState(underTest.id, SetupState.NotStarted)
            commonPreconditions.setAllMet(true)
            setTriggered(true)
            assertThat(isReady).isTrue()

            contextualSetupRepo.setSetupState(underTest.id, SetupState.Completed)
            assertThat(isReady).isFalse()
        }

    @Test
    fun isReady_setupDismissed_isFalse() =
        kosmos.runTest {
            val isReady by collectLastValue(underTest.isReady)

            contextualSetupRepo.setSetupState(underTest.id, SetupState.NotStarted)
            commonPreconditions.setAllMet(true)
            setTriggered(true)
            assertThat(isReady).isTrue()

            contextualSetupRepo.setSetupState(underTest.id, SetupState.Dismissed)
            assertThat(isReady).isFalse()
        }

    @Test
    fun isReady_setupSnoozed_isFalse() =
        kosmos.runTest {
            val isReady by collectLastValue(underTest.isReady)

            contextualSetupRepo.setSetupState(underTest.id, SetupState.NotStarted)
            commonPreconditions.setAllMet(true)
            setTriggered(true)
            assertThat(isReady).isTrue()

            contextualSetupRepo.setSetupState(
                underTest.id,
                SetupState.Snoozed(expirationTime = Instant.ofEpochMilli(100L)),
            )
            assertThat(isReady).isFalse()
        }
}
