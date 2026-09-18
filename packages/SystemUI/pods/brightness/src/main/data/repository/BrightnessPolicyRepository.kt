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

package com.android.systemui.brightness.data.repository

import android.os.UserManager
import com.android.systemui.util.policy.PolicyRestriction
import kotlinx.coroutines.flow.Flow

/** Checks whether the current user is restricted to change the brightness ([RESTRICTION]) */
public interface BrightnessPolicyRepository {

    /**
     * Indicates whether the current user is restricted to change the brightness. As there is no way
     * to determine when a restriction has been added/removed. This value may be fetched eagerly and
     * not updated (unless the user changes) per flow.
     */
    public val restrictionPolicy: Flow<PolicyRestriction>

    public companion object {
        public const val RESTRICTION: String = UserManager.DISALLOW_CONFIG_BRIGHTNESS
    }
}
