/*
 * Copyright 2025 The Android Open Source Project
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

package com.android.settingslib.media

import android.content.ComponentName

/**
 * Information about missing permissions for [android.media.MediaRoute2Info] routes.
 */
data class MissingPermissionsInfo (
    /**
     * Component provided by the app to address missing permissions.
     * @see [android.media.RouteListingPreference.getMissingPermissionsComponentName]
     */
    val componentName: ComponentName,

    /**
     * Set of permissions that are part of route permission sets as per
     * [android.media.MediaRoute2Info.getRequiredPermissions], and requested by the app but not
     * granted.
     * @see [android.media.MediaRouter2.getMissingPermissions()]
     */
    val permissions: Set<String>
)