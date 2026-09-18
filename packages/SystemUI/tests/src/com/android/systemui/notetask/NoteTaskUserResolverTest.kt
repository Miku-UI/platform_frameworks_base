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

package com.android.systemui.notetask

import android.app.admin.DevicePolicyManager
import android.content.pm.UserInfo
import android.os.UserManager
import android.provider.Settings
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.notetask.NoteTaskEntryPoint.APP_CLIPS
import com.android.systemui.notetask.NoteTaskEntryPoint.QUICK_AFFORDANCE
import com.android.systemui.notetask.NoteTaskEntryPoint.TAIL_BUTTON
import com.android.systemui.settings.FakeUserTracker
import com.android.systemui.util.settings.FakeSettings
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import org.mockito.kotlin.whenever

/** atest SystemUITests:NoteTaskUserResolverTest */
@SmallTest
@RunWith(AndroidJUnit4::class)
internal class NoteTaskUserResolverTest : SysuiTestCase() {

    @get:Rule val mockito: MockitoRule = MockitoJUnit.rule()

    @Mock private lateinit var userManager: UserManager
    @Mock private lateinit var devicePolicyManager: DevicePolicyManager
    private val userTracker = FakeUserTracker()
    @OptIn(ExperimentalCoroutinesApi::class) private val testDispatcher = UnconfinedTestDispatcher()
    private val secureSettings = FakeSettings(testDispatcher) { userTracker.userId }

    private lateinit var underTest: NoteTaskUserResolver

    @Before
    fun setUp() {
        underTest =
            NoteTaskUserResolver(
                userManager = userManager,
                devicePolicyManager = devicePolicyManager,
                userTracker = userTracker,
                secureSettings = secureSettings,
                bgDispatcher = testDispatcher,
            )

        whenever(userManager.isManagedProfile(profileUserInfo.id)).thenReturn(true)
    }

    @Test
    fun getUserForHandlingNoteTaking_cope_quickAffordance_shouldReturnProfileUser() = runTest {
        whenever(devicePolicyManager.isOrganizationOwnedDeviceWithManagedProfile).thenReturn(true)
        userTracker.set(
            userInfos = mainAndProfileUsers,
            selectedUserIndex = mainAndProfileUsers.indexOf(mainUserInfo),
        )

        val user = underTest.getUserForHandlingNoteTaking(QUICK_AFFORDANCE)

        assertThat(user).isEqualTo(profileUserInfo.userHandle)
    }

    @Suppress("ktlint:standard:max-line-length")
    @Test
    fun getUserForHandlingNoteTaking_cope_userSelectedProfile_tailButton_shouldReturnProfileUser() =
        runTest {
            secureSettings.putIntForUser(
                /* name= */ Settings.Secure.DEFAULT_NOTE_TASK_PROFILE,
                /* value= */ profileUserInfo.id,
                /* userHandle= */ userTracker.userId,
            )
            whenever(devicePolicyManager.isOrganizationOwnedDeviceWithManagedProfile)
                .thenReturn(true)
            userTracker.set(
                userInfos = mainAndProfileUsers,
                selectedUserIndex = mainAndProfileUsers.indexOf(mainUserInfo),
            )

            val user = underTest.getUserForHandlingNoteTaking(TAIL_BUTTON)

            assertThat(user).isEqualTo(profileUserInfo.userHandle)
        }

    @Suppress("ktlint:standard:max-line-length")
    @Test
    fun getUserForHandlingNoteTaking_cope_userSelectedMainProfile_tailButton_shouldReturnMainProfileUser() =
        runTest {
            secureSettings.putIntForUser(
                /* name= */ Settings.Secure.DEFAULT_NOTE_TASK_PROFILE,
                /* value= */ mainUserInfo.id,
                /* userHandle= */ userTracker.userId,
            )
            whenever(devicePolicyManager.isOrganizationOwnedDeviceWithManagedProfile)
                .thenReturn(true)
            userTracker.set(
                userInfos = mainAndProfileUsers,
                selectedUserIndex = mainAndProfileUsers.indexOf(mainUserInfo),
            )

            val user = underTest.getUserForHandlingNoteTaking(TAIL_BUTTON)

            assertThat(user).isEqualTo(mainUserInfo.userHandle)
        }

    @Test
    fun getUserForHandlingNoteTaking_cope_appClip_shouldReturnCurrentUser() = runTest {
        whenever(devicePolicyManager.isOrganizationOwnedDeviceWithManagedProfile).thenReturn(true)
        userTracker.set(
            userInfos = mainAndProfileUsers,
            selectedUserIndex = mainAndProfileUsers.indexOf(mainUserInfo),
        )

        val user = underTest.getUserForHandlingNoteTaking(APP_CLIPS)

        assertThat(user).isEqualTo(mainUserInfo.userHandle)
    }

    @Test
    fun getUserForHandlingNoteTaking_noManagement_quickAffordance_shouldReturnCurrentUser() =
        runTest {
            userTracker.set(
                userInfos = mainAndProfileUsers,
                selectedUserIndex = mainAndProfileUsers.indexOf(mainUserInfo),
            )

            val user = underTest.getUserForHandlingNoteTaking(QUICK_AFFORDANCE)

            assertThat(user).isEqualTo(mainUserInfo.userHandle)
        }

    @Test
    fun getUserForHandlingNoteTaking_noManagement_tailButton_shouldReturnCurrentUser() = runTest {
        userTracker.set(
            userInfos = mainAndProfileUsers,
            selectedUserIndex = mainAndProfileUsers.indexOf(mainUserInfo),
        )

        val user = underTest.getUserForHandlingNoteTaking(TAIL_BUTTON)

        assertThat(user).isEqualTo(mainUserInfo.userHandle)
    }

    @Test
    fun getUserForHandlingNoteTaking_noManagement_appClip_shouldReturnCurrentUser() = runTest {
        userTracker.set(
            userInfos = mainAndProfileUsers,
            selectedUserIndex = mainAndProfileUsers.indexOf(mainUserInfo),
        )

        val user = underTest.getUserForHandlingNoteTaking(APP_CLIPS)

        assertThat(user).isEqualTo(mainUserInfo.userHandle)
    }

    @Test
    fun resolveParentUserIfManaged_managedProfile_shouldReturnParent() = runTest {
        val managedUser = profileUserInfo.userHandle
        val parentUser = mainUserInfo.userHandle
        whenever(userManager.getProfileParent(managedUser)).thenReturn(parentUser)

        val result = underTest.resolveParentUserIfManaged(managedUser)

        assertThat(result).isEqualTo(parentUser)
    }

    @Test
    fun resolveParentUserIfManaged_notManagedProfile_shouldReturnSameUser() = runTest {
        val user = mainUserInfo.userHandle
        whenever(userManager.isManagedProfile(user.identifier)).thenReturn(false)

        val result = underTest.resolveParentUserIfManaged(user)

        assertThat(result).isEqualTo(user)
    }

    @Test
    fun resolveParentUserIfManaged_managedProfileNoParent_shouldReturnNull() = runTest {
        val managedUser = profileUserInfo.userHandle
        whenever(userManager.getProfileParent(managedUser)).thenReturn(null)

        val result = underTest.resolveParentUserIfManaged(managedUser)

        assertThat(result).isNull()
    }

    private companion object {
        val mainUserInfo =
            UserInfo(/* id= */ 0, /* name= */ "primary", /* flags= */ UserInfo.FLAG_MAIN)
        val profileUserInfo =
            UserInfo(/* id= */ 10, /* name= */ "profile", /* flags= */ UserInfo.FLAG_PROFILE)
        val mainAndProfileUsers = listOf(mainUserInfo, profileUserInfo)
    }
}
