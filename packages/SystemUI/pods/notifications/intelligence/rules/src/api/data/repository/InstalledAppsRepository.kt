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
import com.android.systemui.notifications.intelligence.rules.shared.model.AppModel

/** A repository to fetch installed app information for notification rules. */
interface InstalledAppsRepository {
    /** Returns the app associated with the given [uid], or null if it couldn't be found. */
    suspend fun lookupApp(uid: Int, context: Context): AppModel?

    /**
     * Fetches all apps installed on the device.
     *
     * TODO: b/478225883 - Ensure provided package manager is for the current user by having
     *   [InstalledAppsInteractor] passing in the user-specific package manager from
     *   [SelectedUserInteractor].
     */
    suspend fun fetchInstalledApps(context: Context): List<AppModel>
}
