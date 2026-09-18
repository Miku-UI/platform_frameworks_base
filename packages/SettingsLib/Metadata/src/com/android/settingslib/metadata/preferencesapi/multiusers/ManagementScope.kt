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

package com.android.settingslib.metadata.preferencesapi.multiusers

/**
 * This defines from the point of view of the process hosting a single instance of the APIs, which
 * users it can act on behalf of.
 *
 * In most cases, most apps are only able to manage data for the user they are running on - but in
 * special cases some apps are able to affect data from across their profile group (i.e. the user
 * plus all profiles) or the entire device." */
enum class ManagementScope {
    /** The preference can only manage data for the user the application is running on. */
    OWN_USER,

    /**
     * The preference can manage data for the user the application is running on and all users in
     * the profile group.
     */
    PROFILE_GROUP
}
