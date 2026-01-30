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

package com.android.systemui.qs.pipeline.domain.upgrade

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.advanceTimeBy
import com.android.systemui.kosmos.runCurrent
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testDispatcher
import com.android.systemui.qs.pipeline.data.repository.CustomTileAddedRepository
import com.android.systemui.qs.pipeline.data.repository.customTileAddedRepository
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import java.util.Optional
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class CustomTileAddedRepositoryUpgraderTest : SysuiTestCase() {
    private val kosmos = testKosmos()

    private val userFlow = MutableStateFlow(0)

    private val Kosmos.underTest by Kosmos.Fixture { customTileAddedRepositoryUpgrader }

    @Test
    fun noUpgrades_noFailure_repositoryInVersion1() =
        kosmos.runTest {
            customTileAddedUpgradeSet = emptySet()

            underTest.start(userFlow)

            assertThat(customTileAddedRepository.getVersion(userFlow.value)).isEqualTo(1)
        }

    @Test
    fun upgradeSkip_exception() =
        kosmos.runTest {
            customTileAddedUpgradeSet = setOf(Optional.of(Upgrader(version = 3)))
            assertThrows(IllegalStateException::class.java) {
                underTest.start(userFlow)
            }
        }

    @Test
    fun repeatedVersions_exception() =
        kosmos.runTest {
            customTileAddedUpgradeSet =
                setOf(Optional.of(Upgrader(version = 2)), Optional.of(Upgrader(version = 2)))

            assertThrows(IllegalStateException::class.java) {
                underTest.start(userFlow)
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun upgradesInOrder() =
        kosmos.runTest {
            val upgraderV2 = Upgrader(version = 2) { testDispatcher.scheduler.currentTime }
            val upgraderV3 = Upgrader(version = 3) { testDispatcher.scheduler.currentTime }

            customTileAddedUpgradeSet = setOf(Optional.of(upgraderV2), Optional.of(upgraderV3))

            underTest.start(userFlow)
            advanceTimeBy(5.milliseconds)
            runCurrent()

            val currentUser = userFlow.value

            assertThat(upgraderV2.getUpgradesForUser(currentUser).single())
                .isLessThan(upgraderV3.getUpgradesForUser(currentUser).single())

            assertThat(customTileAddedRepository.getVersion(currentUser)).isEqualTo(3)
        }

    @Test
    fun failingUpgrader_stopsUpgrading() =
        kosmos.runTest {
            val upgraderV2 = Upgrader(version = 2)
            val failingUpgraderV3 = FailingUpgrader(version = 3)

            customTileAddedUpgradeSet =
                setOf(Optional.of(upgraderV2), Optional.of(failingUpgraderV3))

            underTest.start(userFlow)
            advanceTimeBy(5.milliseconds)
            runCurrent()

            val currentUser = userFlow.value

            assertThat(upgraderV2.getUpgradesForUser(currentUser)).hasSize(1)
            assertThat(customTileAddedRepository.getVersion(currentUser)).isEqualTo(2)
        }

    @Test
    fun upgradeOnCurrentUser() =
        kosmos.runTest {
            val upgraderV2 = Upgrader(version = 2) { testDispatcher.scheduler.currentTime }
            val upgraderV3 = Upgrader(version = 3) { testDispatcher.scheduler.currentTime }

            customTileAddedUpgradeSet = setOf(Optional.of(upgraderV2), Optional.of(upgraderV3))
            val oldUser = userFlow.value
            val newUser = oldUser + 1

            underTest.start(userFlow)
            advanceTimeBy(5.milliseconds)
            runCurrent()

            assertThat(customTileAddedRepository.getVersion(newUser)).isEqualTo(1)

            userFlow.value = newUser
            advanceTimeBy(5.milliseconds)
            runCurrent()
            assertThat(customTileAddedRepository.getVersion(newUser)).isEqualTo(3)
        }

    private class Upgrader(override val version: Int, private val time: () -> Long = { 0L }) :
        CustomTileAddedUpgrade {
        private val upgradeCalls = mutableMapOf<Int, MutableList<Long>>()

        override suspend fun CustomTileAddedRepository.upgradeForUser(userId: Int) {
            upgradeCalls.getOrPut(userId) { mutableListOf() }.add(time())
            delay(1.milliseconds) // Delay 1 ms so updates are not concurrent.
        }

        fun getUpgradesForUser(userId: Int): List<Long> {
            return upgradeCalls.getOrDefault(userId, emptyList())
        }
    }

    private class FailingUpgrader(override val version: Int) : CustomTileAddedUpgrade {
        override suspend fun CustomTileAddedRepository.upgradeForUser(userId: Int) {
            throw RuntimeException("Failed!")
        }
    }
}
