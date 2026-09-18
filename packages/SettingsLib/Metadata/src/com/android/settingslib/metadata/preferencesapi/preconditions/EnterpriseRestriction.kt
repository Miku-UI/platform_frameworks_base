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
 * The getter is unavailable due to some enterprise restriction. The reason should explain which
 * restriction.
 */
class EnterpriseRestriction : Disallowed {
    // Enterprise restrictions are an unusual case given it can technically change
    // at any time, but in practice these usually only change at setup time.
    constructor(@StringRes reason: Int) : super(reason, stability = PreconditionStability.UNSTABLE)

    constructor(reason: String) : super(reason, stability = PreconditionStability.UNSTABLE)
}
