/*
 * Copyright (C) 2024 The Android Open Source Project
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
 *
 */

package com.android.systemui.user.domain.interactor

import android.content.pm.UserInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.coroutines.collectLastValue
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.testKosmos
import com.android.systemui.user.data.repository.fakeUserRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class UserLogoutInteractorTest : SysuiTestCase() {

    private val kosmos = testKosmos().useUnconfinedTestDispatcher()

    private val userRepository = kosmos.fakeUserRepository
    private val testScope = kosmos.testScope

    private val underTest = kosmos.userLogoutInteractor

    @Before
    fun setUp() {
        userRepository.setUserInfos(USER_INFOS)
        runBlocking { userRepository.setSelectedUserInfo(USER_INFOS[2]) }
        userRepository.setUserManagerLogoutEnabled(false)
        userRepository.setPolicyManagerLogoutEnabled(false)
    }

    @Test
    fun logOut_doesNothing_whenBothLogoutOptionsAreDisabled() {
        testScope.runTest {
            val isLogoutEnabled by collectLastValue(underTest.isLogoutEnabled)
            val secondaryUserLogoutCount = userRepository.logOutWithPolicyManagerCallCount
            val logoutToSystemUserCount = userRepository.logOutWithUserManagerCallCount
            assertThat(isLogoutEnabled).isFalse()
            underTest.logOut()
            assertThat(userRepository.logOutWithPolicyManagerCallCount)
                .isEqualTo(secondaryUserLogoutCount)
            assertThat(userRepository.logOutWithUserManagerCallCount)
                .isEqualTo(logoutToSystemUserCount)
        }
    }

    @Test
    fun logOut_logsOutUsingPolicyManager_whenPolicyManagerLogoutIsEnabled() {
        testScope.runTest {
            val isLogoutEnabled by collectLastValue(underTest.isLogoutEnabled)
            val lastLogoutCount = userRepository.logOutWithPolicyManagerCallCount
            val logoutToSystemUserCount = userRepository.logOutWithUserManagerCallCount
            userRepository.setPolicyManagerLogoutEnabled(true)
            assertThat(isLogoutEnabled).isTrue()
            underTest.logOut()
            assertThat(userRepository.logOutWithPolicyManagerCallCount)
                .isEqualTo(lastLogoutCount + 1)
            assertThat(userRepository.logOutWithUserManagerCallCount)
                .isEqualTo(logoutToSystemUserCount)
        }
    }

    @Test
    fun logOut_logsOutUsingUserManager_whenLogoutWithUserManagerIsEnabled() {
        testScope.runTest {
            val isLogoutEnabled by collectLastValue(underTest.isLogoutEnabled)
            val lastLogoutCount = userRepository.logOutWithPolicyManagerCallCount
            val logoutToSystemUserCount = userRepository.logOutWithUserManagerCallCount
            userRepository.setUserManagerLogoutEnabled(true)
            assertThat(isLogoutEnabled).isTrue()
            underTest.logOut()
            assertThat(userRepository.logOutWithPolicyManagerCallCount).isEqualTo(lastLogoutCount)
            assertThat(userRepository.logOutWithUserManagerCallCount)
                .isEqualTo(logoutToSystemUserCount + 1)
        }
    }

    @Test
    fun logOut_policyManagerTakesPrecedence() {
        testScope.runTest {
            val isLogoutEnabled by collectLastValue(underTest.isLogoutEnabled)
            val lastLogoutCount = userRepository.logOutWithPolicyManagerCallCount
            val logoutToSystemUserCount = userRepository.logOutWithUserManagerCallCount
            userRepository.setUserManagerLogoutEnabled(true)
            userRepository.setPolicyManagerLogoutEnabled(true)
            assertThat(isLogoutEnabled).isTrue()
            underTest.logOut()
            assertThat(userRepository.logOutWithPolicyManagerCallCount)
                .isEqualTo(lastLogoutCount + 1)
            assertThat(userRepository.logOutWithUserManagerCallCount)
                .isEqualTo(logoutToSystemUserCount)
        }
    }

    @Test
    fun logOutToSystemUser_doesNothing_whenPolicyManagerLogoutIsEnabled() {
        testScope.runTest {
            val isLogoutToSystemUserEnabled by
                collectLastValue(underTest.isLogoutToSystemUserEnabled)
            val secondaryUserLogoutCount = userRepository.logOutWithPolicyManagerCallCount
            val logoutToSystemUserCount = userRepository.logOutWithUserManagerCallCount
            userRepository.setPolicyManagerLogoutEnabled(true)
            assertThat(isLogoutToSystemUserEnabled).isFalse()
            underTest.logOutToSystemUser()
            assertThat(userRepository.logOutWithPolicyManagerCallCount)
                .isEqualTo(secondaryUserLogoutCount)
            assertThat(userRepository.logOutWithUserManagerCallCount)
                .isEqualTo(logoutToSystemUserCount)
        }
    }

    @Test
    fun logOutToSystemUser_whenBothLogoutOptionsAreEnabled() {
        testScope.runTest {
            val isLogoutToSystemUserEnabled by
                collectLastValue(underTest.isLogoutToSystemUserEnabled)
            val secondaryUserLogoutCount = userRepository.logOutWithPolicyManagerCallCount
            val logoutToSystemUserCount = userRepository.logOutWithUserManagerCallCount
            userRepository.setUserManagerLogoutEnabled(true)
            userRepository.setPolicyManagerLogoutEnabled(true)
            assertThat(isLogoutToSystemUserEnabled).isTrue()
            underTest.logOutToSystemUser()
            assertThat(userRepository.logOutWithPolicyManagerCallCount)
                .isEqualTo(secondaryUserLogoutCount)
            assertThat(userRepository.logOutWithUserManagerCallCount)
                .isEqualTo(logoutToSystemUserCount + 1)
        }
    }

    companion object {
        private val USER_INFOS =
            listOf(
                UserInfo(0, "System user", 0),
                UserInfo(10, "Regular user", 0),
                UserInfo(11, "Secondary user", 0),
            )
    }
}
