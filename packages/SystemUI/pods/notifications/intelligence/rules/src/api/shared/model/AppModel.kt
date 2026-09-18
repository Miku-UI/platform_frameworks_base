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

package com.android.systemui.notifications.intelligence.rules.shared.model

import android.graphics.drawable.Drawable

/** Represents an app installed on the device. */
public data class AppModel(
    val uid: Int,
    val label: String,
    val icon: Drawable,
    val packageName: String,
) {
    val uniqueId: String = "$uid-$packageName"
}

/** Represents a set of apps that are included as part of a notification rule filter. */
public data class IncludedAppsModel(val apps: List<AppModel>) {
    init {
        require(apps.isNotEmpty()) { "Apps list cannot be empty" }
    }
}
