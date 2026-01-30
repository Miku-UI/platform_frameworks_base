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

package com.android.systemui.statusbar.policy.profile.data.repository

import com.android.systemui.statusbar.policy.profile.shared.model.ProfileInfo
import kotlinx.coroutines.flow.MutableStateFlow

/** Fake implementation of [ManagedProfileRepository] for testing. */
class FakeManagedProfileRepository : ManagedProfileRepository {

    override val currentProfileInfo = MutableStateFlow<ProfileInfo?>(null)

    /** Creates a test profile info with default values. */
    fun createTestProfileInfo(
        userId: Int = 10,
        iconResId: Int = 12345,
        accessibilityString: String = "Work profile",
    ): ProfileInfo {
        return ProfileInfo(
            userId = userId,
            iconResId = iconResId,
            contentDescription = accessibilityString,
        )
    }
}
