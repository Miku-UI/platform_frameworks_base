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

import android.app.AppInteractionAttribution
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.UserInfo
import android.os.UserHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.server.SystemService.TargetUser
import com.google.common.util.concurrent.MoreExecutors
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class AppInteractionServiceImplTest {
    private lateinit var service: AppInteractionServiceImpl

    private lateinit var mockContext: Context
    private lateinit var mockPackageManager: PackageManager
    private lateinit var mockMultiUserHistory: MultiUserAppInteractionHistory
    private lateinit var mockUserHistory: AppInteractionHistory

    @Before
    fun setup() {
        mockContext = mock()
        mockPackageManager = mock()
        mockMultiUserHistory = mock()
        mockUserHistory = mock()

        whenever(mockContext.createPackageContextAsUser(any(), any(), any()))
            .thenReturn(mockContext)
        whenever(mockMultiUserHistory.asUser(TEST_USER.userIdentifier)).thenReturn(mockUserHistory)

        service =
            AppInteractionServiceImpl(
                mockContext,
                mockMultiUserHistory,
                MoreExecutors.newDirectExecutorService(),
            )
    }

    @Test
    fun userUnlocked_delegateToHistory() {
        service.onUserUnlocked(TEST_USER)

        verify(mockMultiUserHistory, times((1))).onUserUnlocked(TEST_USER)
    }

    @Test
    fun userStopping_delegateToHistory() {
        service.onUserStopping(TEST_USER)

        verify(mockMultiUserHistory, times(1)).onUserStopping(TEST_USER)
    }

    @Test
    fun noteAppInteraction_sourcePackageNotFound_noOp() {
        whenever(
                mockContext.createPackageContextAsUser(
                    "pkg.not.found",
                    0,
                    UserHandle.of(TEST_USER.userIdentifier),
                )
            )
            .doThrow(PackageManager.NameNotFoundException())

        service.noteAppInteraction(
            "pkg.not.found",
            "pkg.exists",
            null,
            0L,
            TEST_USER.userIdentifier,
        )

        verify(mockUserHistory, never())
            .insertAppInteractionHistory(any(), any(), anyOrNull(), any())
    }

    @Test
    fun noteAppInteraction_targetPackageNotFound_noOp() {
        whenever(
                mockContext.createPackageContextAsUser(
                    "pkg.not.found",
                    0,
                    UserHandle.of(TEST_USER.userIdentifier),
                )
            )
            .doThrow(PackageManager.NameNotFoundException())

        service.noteAppInteraction(
            "pkg.exists",
            "pkg.not.found",
            null,
            0L,
            TEST_USER.userIdentifier,
        )

        verify(mockUserHistory, never())
            .insertAppInteractionHistory(any(), any(), anyOrNull(), any())
    }

    @Test
    fun noteAppInteraction_packagesFound_success() {
        val attribution =
            AppInteractionAttribution.Builder(AppInteractionAttribution.INTERACTION_TYPE_USER_QUERY)
                .build()

        service.noteAppInteraction(
            "pkg.exists.1",
            "pkg.exists.2",
            attribution,
            1L,
            TEST_USER.userIdentifier,
        )

        verify(mockUserHistory, times(1))
            .insertAppInteractionHistory(
                eq("pkg.exists.1"),
                eq("pkg.exists.2"),
                eq(attribution),
                eq(1L),
            )
    }

    @Test
    fun noteAppInteraction_userNotUnlocked_failsGracefully() {
        whenever(mockMultiUserHistory.asUser(TEST_USER.userIdentifier))
            .doThrow(IllegalStateException("User locked"))

        service.noteAppInteraction("pkg1", "pkg2", null, 0L, TEST_USER.userIdentifier)

        verify(mockUserHistory, never())
            .insertAppInteractionHistory(any(), any(), anyOrNull(), any())
    }

    @Test
    fun onPackageRemoved_deleteHistory() {
        val packageName = "com.example.package"
        val uid = 10001 // User 0, app 1

        service.mPackageMonitor.onPackageRemoved(packageName, uid)

        verify(mockMultiUserHistory, times(1)).asUser(0)
        verify(mockUserHistory, times(1)).deleteAppInteractionHistories(packageName)
    }

    companion object {
        private val TEST_USER = TargetUser(UserInfo(10, "testUser", 0))
    }
}
