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

package com.android.settingslib.metadata.preferencesapi.types

import android.content.Context
import androidx.annotation.StringRes
import com.android.settingslib.metadata.KeyParameters
import com.android.settingslib.metadata.KeyParametersSchema
import com.android.settingslib.metadata.R
import com.android.settingslib.metadata.ValidatedKeyParameters
import com.android.settingslib.metadata.preferencesapi.types.EType

/** Any int value. */
open class AnyInt(private val unitOfMeasurement: String? = null): DirectApiType<Int> {

    override fun getParametersSchema() = KeyParametersSchema.Builder()
        .parameter("unit", "The unit of measurement (if any) such as dB or milliseconds.", type = AnyString)
        .build()

    override fun getParameters() = getParametersSchema().prepare(buildMap {
        unitOfMeasurement?.let { put("unit", it) }
    })

    override val externalType: EType<Int> = EType.Int
    override fun getDescription(context: Context): String =
        context.getString(R.string.any_int_type_description)
    override fun getKey(): String = "AnyInt"

    companion object : AnyInt()
}
