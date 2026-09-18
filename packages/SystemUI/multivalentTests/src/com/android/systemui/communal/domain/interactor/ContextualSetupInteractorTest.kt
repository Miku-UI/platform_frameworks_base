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

package com.android.systemui.communal.domain.interactor

import android.content.ComponentName
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.communal.contextualSetupDefinitionFactory
import com.android.systemui.communal.contextualSetupRepository
import com.android.systemui.communal.data.repository.SetupState
import com.android.systemui.communal.domain.definition.SetupTarget
import com.android.systemui.communal.fake
import com.android.systemui.dump.dumpManager
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.collectValues
import com.android.systemui.kosmos.runTest
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@SmallTest
@RunWith(AndroidJUnit4::class)
class ContextualSetupInteractorTest : SysuiTestCase() {
    private val kosmos = testKosmos()

    private val repository = kosmos.contextualSetupRepository.fake
    private val definition1 = kosmos.contextualSetupDefinitionFactory("def1")
    private val definition2 = kosmos.contextualSetupDefinitionFactory("def2")

    private val underTest by lazy {
        ContextualSetupInteractor(
            repository = repository,
            definitions = setOf(definition1, definition2),
            dumpManager = kosmos.dumpManager,
        )
    }

    @Before
    fun setUp() {
        definition1.fake.target = SetupTarget.Activity(ComponentName("pkg", "cls"))
        definition2.fake.target = SetupTarget.Activity(ComponentName("pkg", "cls"))
        underTest.init()
    }

    @Test
    fun launchRequest_nothingReady_doesNotEmit() =
        kosmos.runTest {
            val launchRequest by collectLastValue(underTest.launchRequest)
            assertThat(launchRequest).isNull()
        }

    @Test
    fun launchRequest_emitsWhenReadyAndNotStarted() =
        kosmos.runTest {
            val launchRequest by collectLastValue(underTest.launchRequest)

            definition1.fake.setIsReady(true)

            assertThat(launchRequest).isEqualTo(definition1)
        }

    @Test
    fun launchRequest_doesNotEmitWhenTargetIsNull() =
        kosmos.runTest {
            val launchRequest by collectLastValue(underTest.launchRequest)

            definition1.fake.target = null
            definition1.fake.setIsReady(true)

            assertThat(launchRequest).isNull()
        }

    @Test
    fun launchRequest_doesNotEmitWhenReadyButCompleted() =
        kosmos.runTest {
            val launchRequest by collectLastValue(underTest.launchRequest)

            repository.setSetupState(definition1.id, SetupState.Completed)
            definition1.fake.setIsReady(true)

            assertThat(launchRequest).isNull()
        }

    @Test
    fun launchRequest_multipleDefinitionsOneReady_emitsCorrectOne() =
        kosmos.runTest {
            val launchRequest by collectLastValue(underTest.launchRequest)

            definition2.fake.setIsReady(true)

            assertThat(launchRequest).isEqualTo(definition2)
        }

    @Test
    fun launchRequest_emitsNextReadyDefinition_whenPreviousBecomesUnready() =
        kosmos.runTest {
            val launchRequest by collectLastValue(underTest.launchRequest)

            // Start ready, should emit.
            definition1.fake.setIsReady(true)
            assertThat(launchRequest).isEqualTo(definition1)

            // Become unready.
            definition1.fake.setIsReady(false)

            // The launchRequest flow filters out nulls, so it will not emit a `null` value
            // when definition1 becomes unready. To confirm that emissions for definition1
            // have stopped, we make another definition ready. The flow should now emit the
            // new definition, proving it's active and correctly ignoring the unready one.
            definition2.fake.setIsReady(true)
            assertThat(launchRequest).isEqualTo(definition2)
        }

    @Test
    fun launchRequest_isSilentWhenAllBecomeUnready() =
        kosmos.runTest {
            val launchRequests by collectValues(underTest.launchRequest)
            assertThat(launchRequests).isEmpty()

            // Go from unready -> ready
            definition1.fake.setIsReady(true)
            assertThat(launchRequests).containsExactly(definition1)

            // Go from ready -> unready
            definition1.fake.setIsReady(false)

            // Assert that the flow remained silent and no new value was emitted.
            assertThat(launchRequests).containsExactly(definition1)
        }

    @Test
    fun launchRequest_multipleReady_emitsHighestPriority() =
        kosmos.runTest {
            val launchRequest by collectLastValue(underTest.launchRequest)

            // Setup priorities
            definition1.fake.priority = 0
            definition2.fake.priority = 1

            // Both become ready
            definition1.fake.setIsReady(true)
            definition2.fake.setIsReady(true)

            // Should pick definition2 (higher priority)
            assertThat(launchRequest).isEqualTo(definition2)
        }

    @Test
    fun launchRequest_multipleReady_samePriority_emitsLowestId() =
        kosmos.runTest {
            val launchRequest by collectLastValue(underTest.launchRequest)

            // Setup priorities (equal)
            definition1.fake.priority = 0
            definition2.fake.priority = 0

            // Both become ready
            definition1.fake.setIsReady(true)
            definition2.fake.setIsReady(true)

            // Should pick definition1 (lexicographically smaller ID: "def1" < "def2")
            assertThat(launchRequest).isEqualTo(definition1)
        }
}
