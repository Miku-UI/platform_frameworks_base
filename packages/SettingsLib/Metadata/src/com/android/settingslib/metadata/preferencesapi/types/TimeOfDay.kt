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
import com.android.settingslib.metadata.preferencesapi.types.EType
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** A time of day defined by the 24-hour format HH:mm. */
object TimeOfDay : ApiType<LocalTime, String> {
    override val externalType: EType<String> = EType.String

    override fun getDescription(context: Context): String =
        context.getString(R.string.time_of_day_type_description)

    override fun getKey(): String = "TimeOfDay"

    override fun convertInternalToExternal(internal: LocalTime): String =
        internal.format(DateTimeFormatter.ofPattern("HH:mm"))

    override fun convertExternalToInternal(external: String): LocalTime =
        LocalTime.parse(external, DateTimeFormatter.ofPattern("HH:mm"))
}
