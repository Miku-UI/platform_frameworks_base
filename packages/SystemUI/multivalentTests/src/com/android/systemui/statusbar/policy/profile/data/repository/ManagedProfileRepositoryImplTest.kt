/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.statusbar.policy.profile.data.repository

import android.content.Intent
import android.content.pm.UserInfo
import android.content.res.Resources
import android.os.RemoteException
import android.os.UserManager
import android.os.userManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.broadcast.broadcastDispatcher
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.mediaprojection.taskswitcher.fakeActivityTaskManager
import com.android.systemui.statusbar.policy.profile.data.repository.impl.ManagedProfileRepositoryImpl
import com.android.systemui.testKosmos
import com.android.systemui.user.data.repository.fakeUserRepository
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
class ManagedProfileRepositoryImplTest : SysuiTestCase() {

    private val kosmos = testKosmos().useUnconfinedTestDispatcher()
    private val Kosmos.underTest: ManagedProfileRepositoryImpl by
        Kosmos.Fixture { kosmos.realManagedProfileRepository as ManagedProfileRepositoryImpl }

    private val activityTaskManager = kosmos.fakeActivityTaskManager.activityTaskManager

    @Test
    fun currentProfileInfo_initialState_isNull() =
        kosmos.runTest {
            val profileInfo by collectLastValue(underTest.currentProfileInfo)

            assertThat(broadcastDispatcher.numReceiversRegistered).isGreaterThan(0)

            assertThat(profileInfo).isNull()
        }

    @Test
    fun currentProfileInfo_whenProfileBecomesActive_emitsProfileInfo() =
        kosmos.runTest {
            val profileInfo by collectLastValue(underTest.currentProfileInfo)

            setupActiveProfile()

            broadcastDispatcher.sendIntentToMatchingReceiversOnly(
                context,
                Intent(Intent.ACTION_MANAGED_PROFILE_AVAILABLE),
            )

            assertThat(profileInfo).isNotNull()
            assertThat(profileInfo?.userId).isEqualTo(TEST_USER_ID)
            assertThat(profileInfo?.iconResId).isEqualTo(TEST_ICON_RES_ID)
            assertThat(profileInfo?.contentDescription).isEqualTo(TEST_ACCESSIBILITY_STRING)
        }

    @Test
    fun currentProfileInfo_whenProfileBecomesInactive_emitsNull() =
        kosmos.runTest {
            val profileInfo by collectLastValue(underTest.currentProfileInfo)

            setupActiveProfile()
            broadcastDispatcher.sendIntentToMatchingReceiversOnly(
                context,
                Intent(Intent.ACTION_MANAGED_PROFILE_AVAILABLE),
            )
            assertThat(profileInfo).isNotNull()

            whenever(userManager.isProfile(TEST_USER_ID)).thenReturn(false)
            broadcastDispatcher.sendIntentToMatchingReceiversOnly(
                context,
                Intent(Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE),
            )

            assertThat(profileInfo).isNull()
        }

    @Test
    fun currentProfileInfo_whenProfileRemoved_emitsNull() =
        kosmos.runTest {
            val profileInfo by collectLastValue(underTest.currentProfileInfo)

            setupActiveProfile()
            broadcastDispatcher.sendIntentToMatchingReceiversOnly(
                context,
                Intent(Intent.ACTION_MANAGED_PROFILE_AVAILABLE),
            )
            assertThat(profileInfo).isNotNull()

            whenever(userManager.isProfile(TEST_USER_ID)).thenReturn(false)
            broadcastDispatcher.sendIntentToMatchingReceiversOnly(
                context,
                Intent(Intent.ACTION_PROFILE_REMOVED),
            )

            assertThat(profileInfo).isNull()
        }

    @Test
    fun currentProfileInfo_whenUserChanges_updatesProfileInfo() =
        kosmos.runTest {
            val profileInfo by collectLastValue(underTest.currentProfileInfo)

            setupActiveProfile()

            val testUserInfo =
                UserInfo(TEST_USER_ID, "test_user", "", 0, UserManager.USER_TYPE_PROFILE_MANAGED)

            fakeUserRepository.setUserInfos(listOf(testUserInfo))

            assertThat(profileInfo).isNotNull()
            assertThat(profileInfo?.userId).isEqualTo(TEST_USER_ID)
        }

    @Test
    fun currentProfileInfo_profileAccessibilityChanges_updatesProfileInfo() =
        kosmos.runTest {
            val profileInfo by collectLastValue(underTest.currentProfileInfo)

            setupActiveProfile()

            // Simulate profile becoming accessible
            broadcastDispatcher.sendIntentToMatchingReceiversOnly(
                context,
                Intent(Intent.ACTION_PROFILE_ACCESSIBLE),
            )
            assertThat(profileInfo).isNotNull()

            // Simulate profile becoming inaccessible
            broadcastDispatcher.sendIntentToMatchingReceiversOnly(
                context,
                Intent(Intent.ACTION_PROFILE_INACCESSIBLE),
            )
            assertThat(profileInfo).isNotNull() // Still shows profile, just inaccessible
        }

    @Test
    fun currentProfileInfo_whenIconResourceIsNull_emitsNull() =
        kosmos.runTest {
            val profileInfo by collectLastValue(underTest.currentProfileInfo)

            // Setup profile with null icon resource
            whenever(activityTaskManager.lastResumedActivityUserId).thenReturn(TEST_USER_ID)
            whenever(userManager.isProfile(TEST_USER_ID)).thenReturn(true)
            whenever(userManager.getUserStatusBarIconResId(TEST_USER_ID))
                .thenReturn(Resources.ID_NULL)

            broadcastDispatcher.sendIntentToMatchingReceiversOnly(
                context,
                Intent(Intent.ACTION_MANAGED_PROFILE_AVAILABLE),
            )

            assertThat(profileInfo).isNull()
        }

    @Test
    fun currentProfileInfo_whenRemoteExceptionOccurs_emitsNull() =
        kosmos.runTest {
            val profileInfo by collectLastValue(underTest.currentProfileInfo)

            // Setup to throw RemoteException
            whenever(activityTaskManager.lastResumedActivityUserId).thenThrow(RemoteException())

            broadcastDispatcher.sendIntentToMatchingReceiversOnly(
                context,
                Intent(Intent.ACTION_MANAGED_PROFILE_AVAILABLE),
            )

            assertThat(profileInfo).isNull()
        }

    @Test
    fun verifyIntentFilterContainsAllRequiredActions() =
        kosmos.runTest {
            // Since we can't directly access the IntentFilter from broadcastDispatcher,
            // we'll verify by sending each intent and ensuring it triggers an update
            val profileInfo by collectLastValue(underTest.currentProfileInfo)

            // Setup profile
            setupActiveProfile()

            // Test each action
            val actions =
                listOf(
                    Intent.ACTION_MANAGED_PROFILE_AVAILABLE,
                    Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE,
                    Intent.ACTION_PROFILE_REMOVED,
                    Intent.ACTION_PROFILE_ACCESSIBLE,
                    Intent.ACTION_PROFILE_INACCESSIBLE,
                )

            actions.forEach { action ->
                // Change the icon resource to detect updates
                val newIconResId = TEST_ICON_RES_ID + actions.indexOf(action) + 1
                whenever(userManager.getUserStatusBarIconResId(TEST_USER_ID))
                    .thenReturn(newIconResId)

                broadcastDispatcher.sendIntentToMatchingReceiversOnly(context, Intent(action))

                // Verify the profile info updated (showing the intent was received)
                assertThat(profileInfo?.iconResId).isEqualTo(newIconResId)
            }
        }

    private fun Kosmos.setupActiveProfile() {
        whenever(activityTaskManager.lastResumedActivityUserId).thenReturn(TEST_USER_ID)
        whenever(userManager.isProfile(TEST_USER_ID)).thenReturn(true)
        whenever(userManager.getUserStatusBarIconResId(TEST_USER_ID)).thenReturn(TEST_ICON_RES_ID)
        whenever(userManager.getProfileAccessibilityString(TEST_USER_ID))
            .thenReturn(TEST_ACCESSIBILITY_STRING)
    }

    companion object {
        private const val TEST_USER_ID = 10
        private const val TEST_ICON_RES_ID = 12345
        private const val TEST_ACCESSIBILITY_STRING = "Work profile"
    }
}
