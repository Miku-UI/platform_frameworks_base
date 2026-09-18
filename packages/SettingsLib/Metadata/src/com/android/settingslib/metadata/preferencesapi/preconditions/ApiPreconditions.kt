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

import android.content.Context
import androidx.annotation.StringRes
import com.android.settingslib.metadata.preferencesapi.resolveString

interface ApiPreconditions

/** All preconditions are met and get can be called. */
object Allowed : ApiPreconditions

/**
 * Represents a failed precondition check.
 * Every implementation of `Disallowed` needs to provide A human-readable `reason` explaining why
 * the precondition failed.
 */
abstract class Disallowed private constructor(
    @StringRes val reasonRes: Int?,
    val reason: String?,
    val stability: PreconditionStability
) : ApiPreconditions {
    init {
        require(reasonRes != null || reason != null)
    }

    constructor(@StringRes reason: Int, stability: PreconditionStability) : this(reasonRes = reason, reason = null, stability = stability)

    constructor(reason: String, stability: PreconditionStability) : this(reasonRes = null, reason = reason, stability = stability)

    /** Get the reason as a string using the provided context. */
    fun getReason(context: Context): String = resolveString(context, reasonRes, reason)
}
