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

import android.content.Context
import android.content.packageManager
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.testDispatcher
import com.android.systemui.notifications.content.icon.mockAppIconProvider
import com.android.systemui.notifications.intelligence.rules.shared.model.AppModel
import com.android.systemui.notifications.intelligence.rules.shared.notificationRulesLogBuffer
import com.android.systemui.user.data.repository.fakeUserRepository

val Kosmos.realInstalledAppsRepository by
    Kosmos.Fixture {
        InstalledAppsRepositoryImpl(
            testDispatcher,
            packageManager,
            mockAppIconProvider,
            fakeUserRepository,
            notificationRulesLogBuffer,
        )
    }

val Kosmos.fakeInstalledAppsRepository by Kosmos.Fixture { FakeInstalledAppsRepository() }

class FakeInstalledAppsRepository : InstalledAppsRepository {
    var installedApps = emptyList<AppModel>()

    override suspend fun lookupApp(uid: Int, context: Context): AppModel? {
        return installedApps.find { it.uid == uid }
    }

    override suspend fun fetchInstalledApps(context: Context): List<AppModel> {
        return installedApps
    }
}
