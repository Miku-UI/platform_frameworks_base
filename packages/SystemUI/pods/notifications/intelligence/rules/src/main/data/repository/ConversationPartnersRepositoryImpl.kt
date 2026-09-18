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
import android.app.people.IPeopleManager
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.log.LogBuffer
import com.android.systemui.log.core.Logger
import com.android.systemui.notifications.content.icon.AppIconProvider
import com.android.systemui.notifications.intelligence.rules.shared.NmContextualDisplayLaunch
import com.android.systemui.notifications.intelligence.rules.shared.NotificationRulesLog
import com.android.systemui.notifications.intelligence.rules.shared.model.PersonModel
import com.android.systemui.user.data.repository.UserRepository
import javax.inject.Inject
import kotlin.collections.map
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

@SysUISingleton
class ConversationPartnersRepositoryImpl
@Inject
constructor(
    private val launcherApps: LauncherApps,
    @Background private val backgroundDispatcher: CoroutineDispatcher,
    private val appIconProvider: AppIconProvider,
    private val userRepository: UserRepository,
    private val peopleManager: IPeopleManager?,
    @NotificationRulesLog logBuffer: LogBuffer,
) : ConversationPartnersRepository {
    private val logger = Logger(logBuffer, "ConversationsRepo")

    override suspend fun lookupConversationPartner(id: String): PersonModel.ConversationPartner? {
        // TODO: b/478225883 - Look up conversation in PeopleManager once we also have the package
        // and user info associated with this conversation partner.
        return null
    }

    override suspend fun fetchRecentConversationPartners(): List<PersonModel.ConversationPartner> {
        if (!NmContextualDisplayLaunch.isEnabled) {
            return emptyList()
        }
        if (peopleManager == null) {
            logger.e({ "IPeopleManager is null, cannot fetch recent conversations" }) {}
            return emptyList()
        }
        return withContext(backgroundDispatcher) {
            val recentConversations: List<ConversationChannel> =
                peopleManager.recentConversations.getList() as List<ConversationChannel>

            recentConversations
                .map { it.shortcutInfo }
                .map {
                    val icon = launcherApps.getShortcutIcon(it)
                    PersonModel.ConversationPartner(
                        id = it.id,
                        displayLabel = it.label.toString(),
                        avatarIcon = icon,
                        appBadgeIcon = fetchAppBadgeIcon(it.`package`),
                    )
                }
        }
    }

    private fun fetchAppBadgeIcon(packageName: String): Drawable? {
        return try {
            appIconProvider.getOrFetchAppIcon(
                packageName,
                userRepository.selectedUser.value.userInfo.userHandle,
                instanceKey = "rule:conversation",
            )
        } catch (_: PackageManager.NameNotFoundException) {
            logger.w({ "Could not fetch app icon for package=$str1" }) { str1 = packageName }
            null
        }
    }
}
