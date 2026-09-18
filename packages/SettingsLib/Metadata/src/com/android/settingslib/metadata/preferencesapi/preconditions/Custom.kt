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

/**
 * Any failure reason not covered by the above. This should be rare so please discuss with the
 * settings team if you feel you need to use this - it may be appropriate for us to create a new
 * result type.
 */
class Custom : Disallowed {
    constructor(@StringRes reason: Int, stability: PreconditionStability) : super(reason, stability)

    constructor(reason: String, stability: PreconditionStability) : super(reason, stability)
}
