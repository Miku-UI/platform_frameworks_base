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

package com.android.wm.shell.bubbles.user.data

import android.content.pm.UserInfo
import android.os.UserManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.users.UserType
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub

/** Unit tests for [UserManagerBubbleUserResolver]. */
@SmallTest
@RunWith(AndroidJUnit4::class)
class UserManagerBubbleUserResolverTest {

    private val userManager = mock<UserManager>()
    private val userManagerUserResolver = UserManagerBubbleUserResolver(userManager)

    @Test
    fun unresolvedUser_returnsMainProfile() {
        val bubbleUserInfo = userManagerUserResolver.resolve(userId = 1)
        assertThat(bubbleUserInfo.userType).isEqualTo(UserType.MAIN)
    }

    @Test
    fun clonedProfile() {
        val userInfo = mock<UserInfo> { on { isCloneProfile } doReturn true }
        userManager.stub { on { getUserInfo(1) } doReturn userInfo }
        val bubbleUserInfo = userManagerUserResolver.resolve(userId = 1)
        assertThat(bubbleUserInfo.userType).isEqualTo(UserType.CLONED)
    }

    @Test
    fun workProfile() {
        val userInfo = mock<UserInfo> { on { isManagedProfile } doReturn true }
        userManager.stub { on { getUserInfo(1) } doReturn userInfo }
        val bubbleUserInfo = userManagerUserResolver.resolve(userId = 1)
        assertThat(bubbleUserInfo.userType).isEqualTo(UserType.WORK)
    }

    @Test
    fun privateProfile() {
        val userInfo = mock<UserInfo> { on { isPrivateProfile } doReturn true }
        userManager.stub { on { getUserInfo(1) } doReturn userInfo }
        val bubbleUserInfo = userManagerUserResolver.resolve(userId = 1)
        assertThat(bubbleUserInfo.userType).isEqualTo(UserType.PRIVATE)
    }

    @Test
    fun mainProfile() {
        val userInfo = mock<UserInfo>()
        userManager.stub { on { getUserInfo(1) } doReturn userInfo }
        val bubbleUserInfo = userManagerUserResolver.resolve(userId = 1)
        assertThat(bubbleUserInfo.userType).isEqualTo(UserType.MAIN)
    }
}
