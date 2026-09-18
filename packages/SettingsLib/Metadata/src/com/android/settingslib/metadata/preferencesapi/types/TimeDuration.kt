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

package com.android.settingslib.metadata.preferencesapi.types

import android.content.Context
import com.android.settingslib.metadata.R
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** A time duration represented in integer with the precision of seconds. */
object TimeDuration : ApiType<Duration, Int> {
    override val externalType: EType<Int> = EType.Int

    override fun getDescription(context: Context): String =
        context.getString(R.string.time_duration_type_description)

    override fun getKey(): String = "TimeDuration"

    override fun convertInternalToExternal(internalValue: Duration): Int =
        (internalValue + 0.5.seconds).inWholeSeconds.toInt()

    override fun convertExternalToInternal(externalValue: Int): Duration = externalValue.seconds
}
