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

package com.android.server.appinteraction

import android.content.pm.UserInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.internal.infra.AndroidFuture
import com.android.server.SystemService.TargetUser
import com.google.common.truth.Truth.assertThat
import kotlin.test.assertFailsWith
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

@RunWith(AndroidJUnit4::class)
class MultiUserAppInteractionHistoryTest {

    private lateinit var serviceConfig: ServiceConfig

    private lateinit var scheduledExecutorService: FakeScheduledExecutorService

    private lateinit var multiUserInteractionHistory: MultiUserAppInteractionHistory

    private lateinit var interactionHistoryMap: MutableMap<Int, AppInteractionHistory>

    @Before
    fun setup() {
        interactionHistoryMap = mutableMapOf()
        serviceConfig = ServiceConfigImpl()
        scheduledExecutorService = FakeScheduledExecutorService()
        multiUserInteractionHistory =
            MultiUserAppInteractionHistory(serviceConfig, scheduledExecutorService) { userHandle ->
                val interactionHistory = mock<AppInteractionHistory>()
                interactionHistoryMap[userHandle.identifier] = interactionHistory
                AndroidFuture<AppInteractionHistory>.completedFuture(interactionHistory)
            }
    }

    @Test
    fun switchUser_afterUserUnlocked_success() {
        val targetUser = TargetUser(UserInfo(10, "testUser", 0))

        multiUserInteractionHistory.onUserUnlocked(targetUser)
        val userAccessHistory = multiUserInteractionHistory.asUser(10)

        assertThat(userAccessHistory).isNotNull()
        assertThat(userAccessHistory).isEqualTo(interactionHistoryMap[10])
    }

    @Test
    fun switchUser_beforeUserUnlocked_fail() {
        assertFailsWith<IllegalStateException> { multiUserInteractionHistory.asUser(10) }
    }

    @Test
    fun switchUser_afterUserStopping_fail() {
        val targetUser = TargetUser(UserInfo(10, "testUser", 0))
        multiUserInteractionHistory.onUserUnlocked(targetUser)
        multiUserInteractionHistory.onUserStopping(targetUser)

        assertFailsWith<IllegalStateException> { multiUserInteractionHistory.asUser(10) }
    }

    @Test
    fun schedulePeriodicCleanUp_afterUserUnlocked() {
        val targetUser = TargetUser(UserInfo(10, "testUser", 0))

        multiUserInteractionHistory.onUserUnlocked(targetUser)
        scheduledExecutorService.fastForwardTime(1)
        scheduledExecutorService.fastForwardTime(
            serviceConfig.appInteractionExpiredHistoryDeletionIntervalMillis
        )
        scheduledExecutorService.fastForwardTime(
            serviceConfig.appInteractionExpiredHistoryDeletionIntervalMillis
        )
        scheduledExecutorService.fastForwardTime(
            serviceConfig.appInteractionExpiredHistoryDeletionIntervalMillis
        )

        assertThat(scheduledExecutorService.futures).hasSize(1)
        val userAccessHistory = multiUserInteractionHistory.asUser(10)
        verify(userAccessHistory, times(4))
            .deleteExpiredAppInteractionHistories(
                eq(serviceConfig.appInteractionHistoryRetentionMillis)
            )
    }

    @Test
    fun periodicCleanUpStop_afterUserStopping() {
        val targetUser = TargetUser(UserInfo(10, "testUser", 0))

        multiUserInteractionHistory.onUserUnlocked(targetUser)
        val userAccessHistory = multiUserInteractionHistory.asUser(10)
        scheduledExecutorService.fastForwardTime(1)
        multiUserInteractionHistory.onUserStopping(targetUser)
        scheduledExecutorService.fastForwardTime(
            serviceConfig.appInteractionExpiredHistoryDeletionIntervalMillis
        )

        assertThat(scheduledExecutorService.futures).isEmpty()
        // Should only trigger once as the job should be cancel during the second schedule
        verify(userAccessHistory, times(1))
            .deleteExpiredAppInteractionHistories(
                eq(serviceConfig.appInteractionHistoryRetentionMillis)
            )
    }

    @Test
    fun scheduleExactlyOneCleanUp_afterMultiUserUnlocked() {
        val targetUser = TargetUser(UserInfo(10, "testUser", 0))

        multiUserInteractionHistory.onUserUnlocked(targetUser)
        multiUserInteractionHistory.onUserUnlocked(targetUser)
        multiUserInteractionHistory.onUserUnlocked(targetUser)

        assertThat(scheduledExecutorService.futures).hasSize(1)
    }

    @Test
    fun stoppingUser_nonStartedOne() {
        val targetUser = TargetUser(UserInfo(10, "testUser", 0))

        multiUserInteractionHistory.onUserStopping(targetUser)
    }

    @Test
    fun userStopping_whenHistoryCreationIsPending() {
        val pendingFuture = AndroidFuture<AppInteractionHistory>()
        multiUserInteractionHistory =
            MultiUserAppInteractionHistory(serviceConfig, scheduledExecutorService) {
                pendingFuture
            }
        val targetUser = TargetUser(UserInfo(10, "testUser", 0))

        multiUserInteractionHistory.onUserUnlocked(targetUser)
        multiUserInteractionHistory.onUserStopping(targetUser)

        assertThat(pendingFuture.isCancelled).isTrue()
        val history = mock<AppInteractionHistory>()
        pendingFuture.complete(history)
        assertFailsWith<IllegalStateException> { multiUserInteractionHistory.asUser(10) }
    }

    @Test
    fun asUser_shouldFailWhenHistoryCreationIsPending() {
        val pendingFuture = AndroidFuture<AppInteractionHistory>()
        multiUserInteractionHistory =
            MultiUserAppInteractionHistory(serviceConfig, scheduledExecutorService) {
                pendingFuture
            }
        val targetUser = TargetUser(UserInfo(10, "testUser", 0))

        multiUserInteractionHistory.onUserUnlocked(targetUser)

        assertFailsWith<IllegalStateException> { multiUserInteractionHistory.asUser(10) }
    }
}
