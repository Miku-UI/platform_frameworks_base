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
import com.android.settingslib.metadata.KeyParametersSchema
import com.android.settingslib.metadata.R
import com.android.settingslib.metadata.preferencesapi.types.EType

/** Any int in the given range, along the given step. */
class IntInRange(val min: Int?, val max: Int?, val step: Int = 1, private val unitOfMeasurement: String? = null): DirectApiType<Int> {

    override val externalType: EType<Int> = EType.Int

    override fun getParametersSchema() = KeyParametersSchema.Builder()
        .parameter("unit", "The unit of measurement (if any) such as dB or milliseconds.", type = AnyString)
        .build()

    override fun getParameters() = getParametersSchema().prepare(buildMap {
        unitOfMeasurement?.let { put("unit", it) }
    })

    init {
        require(min != null || max != null)
    }

    override fun getDescription(context: Context): String {
        return when {
            min != null && max != null ->
                context.getString(R.string.int_in_range_type_description_between, min, max, step)
            min != null -> context.getString(R.string.int_in_range_type_description_from, min, step)
            max != null -> context.getString(R.string.int_in_range_type_description_to, max, step)
            else -> error("There needs to be at least a minimum bound or a maximum bound in an IntInRange")
        }
    }

    override fun getKey(): String = "IntInRange:${min}:${max}:${step}"
}

/** A percentage int in the range [0, 100], along with a unit description. */
val PercentageInt = IntInRange(min = 0, max = 100, step = 1, unitOfMeasurement = "percentage")
