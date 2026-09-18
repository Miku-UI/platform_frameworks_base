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

package com.android.systemui.notifications.ui

import org.json.JSONObject
import platform.test.motion.golden.DataPointType
import platform.test.motion.golden.UnknownTypeException
import platform.test.motion.golden.ValueDataPoint
import platform.test.motion.golden.dataPointType

fun YSpace.asDataPoint() = ySpace.makeDataPoint(this)

internal val ySpace: DataPointType<YSpace> =
    DataPointType.create(
        "YSpace",
        jsonToValue = {
            when (it) {
                is JSONObject -> {
                    // Delegate to the float serializer to handle special cases (NaN, etc.)
                    val top = Float.dataPointType.fromJson(it.get("top"))
                    val bottom = Float.dataPointType.fromJson(it.get("bottom"))

                    if (top !is ValueDataPoint<Float> || bottom !is ValueDataPoint<Float>) {
                        throw UnknownTypeException()
                    }

                    YSpace(top = top.value, bottom = bottom.value)
                }
                else -> throw UnknownTypeException()
            }
        },
        valueToJson = {
            JSONObject().apply {
                put("top", Float.dataPointType.toJson(it.top))
                put("bottom", Float.dataPointType.toJson(it.bottom))
            }
        },
    )
