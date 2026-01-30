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

package com.android.systemui.qs.panels.data.repository

import android.content.packageManager
import android.content.pm.ApplicationInfo
import com.android.systemui.common.shared.model.ContentDescription
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.common.shared.model.asIcon
import com.android.systemui.kosmos.Kosmos

val Kosmos.appIconRepository by
    Kosmos.Fixture {
        object : AppIconRepository {
            override fun loadIcon(applicationInfo: ApplicationInfo): Icon.Loaded? {
                // Return the app icon unmodified
                return applicationInfo
                    .loadIcon(packageManager)
                    ?.asIcon(ContentDescription.Loaded(null))
            }
        }
    }

val Kosmos.appIconRepositoryFactory by
    Kosmos.Fixture {
        object : AppIconRepository.Factory {
            override fun create(): AppIconRepository {
                return appIconRepository
            }
        }
    }
