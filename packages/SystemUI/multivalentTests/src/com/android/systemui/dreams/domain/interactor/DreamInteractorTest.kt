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

package com.android.systemui.dreams.domain.interactor

import android.content.ComponentName
import android.content.pm.UserInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.dreams.data.repository.dreamRepository
import com.android.systemui.dreams.data.repository.fake
import com.android.systemui.dreams.shared.model.DreamItemModel
import com.android.systemui.dreams.shared.model.DreamPlaylistModel
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.testKosmos
import com.android.systemui.user.data.repository.fakeUserRepository
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class DreamInteractorTest : SysuiTestCase() {
    private val kosmos = testKosmos().useUnconfinedTestDispatcher()

    private val Kosmos.underTest by Kosmos.Fixture { dreamInteractor }

    @Before
    fun setUp() {
        kosmos.fakeUserRepository.setUserInfos(USERS)
    }

    @Test
    fun isDreamSwitcherEnabled_true() =
        kosmos.runTest {
            dreamRepository.fake.setDreamSwitcherEnabled(true)
            assertThat(underTest.isDreamSwitcherEnabled).isTrue()
        }

    @Test
    fun isDreamSwitcherEnabled_false() =
        kosmos.runTest {
            dreamRepository.fake.setDreamSwitcherEnabled(false)
            assertThat(underTest.isDreamSwitcherEnabled).isFalse()
        }

    @Test
    fun testDreamPlaylistIsExposed() =
        kosmos.runTest {
            val dreamPlaylist =
                DreamPlaylistModel(
                    dreams = listOf(DreamItemModel(ComponentName("test", "pkg"))),
                    activeIndex = 0,
                )

            fakeUserRepository.setSelectedUserInfo(USER_1)
            dreamRepository.fake.setDreamState(USER_1.userHandle, dreamPlaylist)

            val dreamState by collectLastValue(underTest.dreamState)

            assertThat(dreamState).isEqualTo(dreamPlaylist)
        }

    @Test
    fun testSetActiveDream() =
        kosmos.runTest {
            val component1 = ComponentName("test", "component1")
            val component2 = ComponentName("test", "component2")
            val dreamItem1 = DreamItemModel(component1)
            val dreamItem2 = DreamItemModel(component2)

            setupDreamPlaylistForUser(USER_1, dreamItem1, dreamItem2)

            val dreamState by collectLastValue(underTest.dreamState)
            assertThat(dreamState?.activeDream).isEqualTo(dreamItem1)

            // Act: Set a new active dream for the current user
            underTest.setActiveDream(component2, USER_1.userHandle)

            // Assert: The active dream should be updated
            assertThat(dreamState?.activeDream).isEqualTo(dreamItem2)
        }

    @Test
    fun testSetActiveDream_forDifferentUser() =
        kosmos.runTest {
            val component1 = ComponentName("test", "component1")
            val component2 = ComponentName("test", "component2")
            val dreamItem1 = DreamItemModel(component1)
            val dreamItem2 = DreamItemModel(component2)

            setupDreamPlaylistForUser(USER_1, dreamItem1, dreamItem2)

            val dreamState by collectLastValue(underTest.dreamState)
            assertThat(dreamState?.activeDream).isEqualTo(dreamItem1)

            // Act: Set the active dream for USER_2
            underTest.setActiveDream(component2, USER_2.userHandle)

            // Assert: The active dream shouldn't change for the current user (USER_1)
            assertThat(dreamState?.activeDream).isEqualTo(dreamItem1)
        }

    /** Helper function to set up a common dream playlist state for USER_1. */
    private suspend fun Kosmos.setupDreamPlaylistForUser(
        user: UserInfo,
        dreamItem1: DreamItemModel,
        dreamItem2: DreamItemModel,
    ) {
        fakeUserRepository.setSelectedUserInfo(user)
        dreamRepository.fake.setDreamState(
            user.userHandle,
            DreamPlaylistModel(dreams = listOf(dreamItem1, dreamItem2), activeIndex = 0),
        )
    }

    private companion object {
        val USER_1 = UserInfo(1, "user1", UserInfo.FLAG_MAIN)
        val USER_2 = UserInfo(2, "user2", UserInfo.FLAG_FULL)
        val USERS = listOf(USER_1, USER_2)
    }
}
