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

import android.app.people.mockIPeopleManager
import com.android.systemui.keyboard.shortcut.fakeLauncherApps
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.testDispatcher
import com.android.systemui.notifications.content.icon.realAppIconProvider
import com.android.systemui.notifications.intelligence.rules.shared.model.PersonModel
import com.android.systemui.notifications.intelligence.rules.shared.notificationRulesLogBuffer
import com.android.systemui.user.data.repository.fakeUserRepository

val Kosmos.realConversationPartnersRepository by
    Kosmos.Fixture {
        ConversationPartnersRepositoryImpl(
            fakeLauncherApps.launcherApps,
            backgroundDispatcher = testDispatcher,
            realAppIconProvider,
            fakeUserRepository,
            mockIPeopleManager,
            notificationRulesLogBuffer,
        )
    }

val Kosmos.fakeConversationPartnersRepository by
    Kosmos.Fixture { FakeConversationPartnersRepository() }

class FakeConversationPartnersRepository : ConversationPartnersRepository {
    var conversationPartners = emptyList<PersonModel.ConversationPartner>()

    override suspend fun lookupConversationPartner(id: String): PersonModel.ConversationPartner? {
        return conversationPartners.find { it.id == id }
    }

    override suspend fun fetchRecentConversationPartners(): List<PersonModel.ConversationPartner> {
        return conversationPartners
    }
}
