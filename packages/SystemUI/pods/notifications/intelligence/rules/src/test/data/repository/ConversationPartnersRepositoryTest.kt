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

package com.android.systemui.notifications.intelligence.rules.data.repository

import android.app.people.ConversationChannel
import android.app.people.mockIPeopleManager
import android.content.applicationContext
import android.content.pm.ParceledListSlice
import android.content.pm.ShortcutInfo
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.runTest
import com.android.systemui.notifications.intelligence.rules.shared.NmContextualDisplayLaunch
import com.android.systemui.testKosmosNew
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
class ConversationPartnersRepositoryTest : SysuiTestCase() {
    private val kosmos = testKosmosNew()

    private val Kosmos.underTest by Kosmos.Fixture { kosmos.realConversationPartnersRepository }

    @Test
    @DisableFlags(NmContextualDisplayLaunch.FLAG_NAME)
    fun fetchRecentConversationPartners_flagOff_returnsEmptyList() =
        kosmos.runTest {
            whenever(mockIPeopleManager!!.recentConversations)
                .thenReturn(ParceledListSlice(listOf(createConversationChannel("id1", "Eevee"))))

            val result = underTest.fetchRecentConversationPartners()

            assertThat(result).isEmpty()
        }

    @Test
    @EnableFlags(NmContextualDisplayLaunch.FLAG_NAME)
    fun fetchRecentConversationPartners_nullPeopleManager_returnsEmptyList() =
        kosmos.runTest {
            mockIPeopleManager = null

            val result = underTest.fetchRecentConversationPartners()

            assertThat(result).isEmpty()
        }

    @Test
    @EnableFlags(NmContextualDisplayLaunch.FLAG_NAME)
    fun fetchRecentConversationPartners_returnsAll() =
        kosmos.runTest {
            whenever(mockIPeopleManager!!.recentConversations)
                .thenReturn(
                    ParceledListSlice(
                        listOf(
                            createConversationChannel("id1", "Eevee"),
                            createConversationChannel("id2", "Jolteon"),
                            createConversationChannel("id3", "Vaporeon"),
                        )
                    )
                )

            val result = underTest.fetchRecentConversationPartners()

            assertThat(result).hasSize(3)
            assertThat(result[0].id).isEqualTo("id1")
            assertThat(result[0].displayLabel).isEqualTo("Eevee")
            assertThat(result[1].id).isEqualTo("id2")
            assertThat(result[1].displayLabel).isEqualTo("Jolteon")
            assertThat(result[2].id).isEqualTo("id3")
            assertThat(result[2].displayLabel).isEqualTo("Vaporeon")
        }

    private fun Kosmos.createConversationChannel(
        shortcutId: String,
        label: String,
    ): ConversationChannel {
        val shortcutInfo =
            ShortcutInfo.Builder(applicationContext, shortcutId).setLongLabel(label).build()
        return ConversationChannel(shortcutInfo, 0, null, null, 8, false)
    }
}
