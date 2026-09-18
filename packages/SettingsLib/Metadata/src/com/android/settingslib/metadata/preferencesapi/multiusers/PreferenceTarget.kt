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
 * Defines how widely a preference applies.
 *
 * This can be device-wide (there is only a single instance of the value governing the entire
 * device), profile-group-wide (there is a single instance of the value for each user and that
 * value also applies to profiles of that user), or user-specific (each user and profile has their
 * own value).
 */
sealed interface PreferenceTarget {
    /**
     * The preference applies to the entire device, meaning it is a global setting that is not
     * user-specific.
     */
    object DEVICE : PreferenceTarget

    /** The preference applies to a specific user and their profiles. */
    object PROFILE_GROUP : PreferenceTarget

    /**
     * The preference applies to a specific user.
     *
     * @property canManage The [ManagementScope] governing this preference.
     */
    data class USER(val canManage: ManagementScope) : PreferenceTarget
}
