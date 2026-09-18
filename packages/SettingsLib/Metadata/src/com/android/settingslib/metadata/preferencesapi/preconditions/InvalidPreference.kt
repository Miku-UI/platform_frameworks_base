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

package com.android.settingslib.metadata.preferencesapi.preconditions

import androidx.annotation.StringRes
import com.android.settingslib.metadata.KeyParametersSchema

/**
 * The getter is unavailable due to the value or state of another preference. The
 * otherPreferenceScreenKey/otherPreferenceKey/otherPreferenceParams should identify the problematic
 * preference (params being required only if the other preference is parameterised). The reason
 * should explain what is wrong.
 */
class InvalidPreference : Disallowed {
    constructor(
        otherPreferenceScreenKey: String,
        otherPreferenceKey: String,
        otherPreferenceParams: KeyParametersSchema?,
        @StringRes reason: Int,
    ) : super(reason, stability = PreconditionStability.UNSTABLE)

    constructor(
        otherPreferenceScreenKey: String,
        otherPreferenceKey: String,
        otherPreferenceParams: KeyParametersSchema?,
        reason: String,
    ) : super(reason, stability = PreconditionStability.UNSTABLE)

    constructor(
        otherPreferenceScreenKey: String,
        otherPreferenceKey: String,
        @StringRes reason: Int,
    ) : this(
        otherPreferenceScreenKey,
        otherPreferenceKey,
        null,
        reason,
    )

    constructor(
        otherPreferenceScreenKey: String,
        otherPreferenceKey: String,
        reason: String,
    ) : this(
        otherPreferenceScreenKey,
        otherPreferenceKey,
        null,
        reason,
    )
}
