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

package com.android.systemui.notifications.intelligence.rules.domain.interactor

import android.graphics.drawable.Icon
import android.platform.test.annotations.EnableFlags
import androidx.core.graphics.createBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.runTest
import com.android.systemui.notifications.intelligence.rules.data.repository.fakeConversationPartnersRepository
import com.android.systemui.notifications.intelligence.rules.shared.NmContextualDisplayLaunch
import com.android.systemui.notifications.intelligence.rules.shared.model.PersonModel
import com.android.systemui.testKosmosNew
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class ConversationPartnersInteractorTest : SysuiTestCase() {
    private val kosmos = testKosmosNew()

    private val Kosmos.underTest by Kosmos.Fixture { kosmos.conversationPartnersInteractor }

    @Test
    @EnableFlags(NmContextualDisplayLaunch.FLAG_NAME)
    fun fetchRecentConversationPartners_emptySearchQuery_returnsAll() =
        kosmos.runTest {
            val partner1 =
                PersonModel.ConversationPartner(
                    id = "1",
                    displayLabel = "Conversation #1",
                    avatarIcon = Icon.createWithBitmap(createBitmap(1, 1)),
                    appBadgeIcon = null,
                )
            val partner2 =
                PersonModel.ConversationPartner(
                    id = "2",
                    displayLabel = "Conversation #2",
                    avatarIcon = Icon.createWithBitmap(createBitmap(1, 1)),
                    appBadgeIcon = null,
                )
            fakeConversationPartnersRepository.conversationPartners = listOf(partner1, partner2)

            val result = underTest.fetchRecentConversationPartners(searchQuery = "")

            assertThat(result).containsExactly(partner1, partner2).inOrder()
        }

    @Test
    @EnableFlags(NmContextualDisplayLaunch.FLAG_NAME)
    fun fetchRecentConversationPartners_hasSearchQuery_returnsAllMatchingCaseIgnored() =
        kosmos.runTest {
            val eeveeCapital =
                PersonModel.ConversationPartner(
                    id = "1",
                    displayLabel = "Eevee",
                    avatarIcon = Icon.createWithBitmap(createBitmap(1, 1)),
                    appBadgeIcon = null,
                )
            val eeveeAllCaps =
                PersonModel.ConversationPartner(
                    id = "2",
                    displayLabel = "EEVEE",
                    avatarIcon = Icon.createWithBitmap(createBitmap(1, 1)),
                    appBadgeIcon = null,
                )
            val vaporeon =
                PersonModel.ConversationPartner(
                    id = "3",
                    displayLabel = "Vaporeon",
                    avatarIcon = Icon.createWithBitmap(createBitmap(1, 1)),
                    appBadgeIcon = null,
                )
            fakeConversationPartnersRepository.conversationPartners =
                listOf(eeveeCapital, vaporeon, eeveeAllCaps)

            val result = underTest.fetchRecentConversationPartners(searchQuery = "eevee")

            assertThat(result).containsExactly(eeveeCapital, eeveeAllCaps).inOrder()
        }
}
