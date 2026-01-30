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

package com.android.settingslib.spa.system.restricted

import android.content.Context
import android.os.UserManager
import androidx.core.os.bundleOf
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.settingslib.spa.restricted.NoRestricted
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.stub

@RunWith(AndroidJUnit4::class)
class SystemRestrictedRepositoryTest {
    private val mockUserManager = mock<UserManager>()

    private val context: Context =
        spy(ApplicationProvider.getApplicationContext()) {
            on { getSystemService(UserManager::class.java) } doReturn mockUserManager
        }

    private val repository = SystemRestrictedRepository(context)

    @Test
    fun getRestrictedMode_emptyKeys_returnsNoRestricted() = runBlocking {
        val restrictedMode =
            repository.restrictedModeFlow(SystemRestrictions(keys = emptyList())).first()

        assertThat(restrictedMode).isInstanceOf(NoRestricted::class.java)
    }

    @Test
    fun getRestrictedMode_noUserRestrictions_returnsNoRestricted() = runBlocking {
        mockUserManager.stub { on { userRestrictions } doReturn bundleOf() }

        val restrictedMode =
            repository
                .restrictedModeFlow(SystemRestrictions(keys = listOf(RESTRICTION_KEY)))
                .first()

        assertThat(restrictedMode).isInstanceOf(NoRestricted::class.java)
    }

    @Test
    fun getRestrictedMode_userRestrictionFalse_returnsNoRestricted() = runBlocking {
        mockUserManager.stub { on { userRestrictions } doReturn bundleOf(RESTRICTION_KEY to false) }

        val restrictedMode =
            repository
                .restrictedModeFlow(SystemRestrictions(keys = listOf(RESTRICTION_KEY)))
                .first()

        assertThat(restrictedMode).isInstanceOf(NoRestricted::class.java)
    }

    @Test
    fun getRestrictedMode_userRestrictionTrue_returnsBlockedByAdmin() = runBlocking {
        mockUserManager.stub { on { userRestrictions } doReturn bundleOf(RESTRICTION_KEY to true) }

        val restrictedMode =
            repository
                .restrictedModeFlow(SystemRestrictions(keys = listOf(RESTRICTION_KEY)))
                .first()

        assertThat(restrictedMode).isInstanceOf(SystemBlockedByAdmin::class.java)
    }

    private companion object {
        const val RESTRICTION_KEY = UserManager.DISALLOW_ADJUST_VOLUME
    }
}
