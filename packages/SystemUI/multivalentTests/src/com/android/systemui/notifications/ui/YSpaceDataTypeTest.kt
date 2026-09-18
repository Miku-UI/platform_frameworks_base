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

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.google.common.truth.Truth.assertThat
import kotlin.test.assertTrue
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import platform.test.motion.golden.DataPoint

@RunWith(AndroidJUnit4::class)
@SmallTest
class YSpaceDataTypeTest : SysuiTestCase() {

    @Test
    fun ySpace_fromJson_validObject() {
        val expected = YSpace(10.0f, 20.0f)
        val actual =
            ySpace.fromJson(
                JSONObject().apply {
                    put("top", expected.top)
                    put("bottom", expected.bottom)
                }
            )

        assertThat(actual).isEqualTo(expected.asDataPoint())
    }

    @Test
    fun ySpace_toJson_validJsonValues() {
        val expected = YSpace(10.0f, 20.0f)
        val actual = ySpace.toJson(expected) as JSONObject

        assertTrue(actual.has("top"))
        assertTrue(actual.has("bottom"))
        assertThat(actual["top"]).isEqualTo(expected.top)
        assertThat(actual["bottom"]).isEqualTo(expected.bottom)
    }

    @Test
    fun ySpace_fromJson_notAnObject_returnsUnknown() {
        assertThat(ySpace.fromJson("foo")).isEqualTo(DataPoint.unknownType<YSpace>())
    }

    @Test
    fun ySpace_isApproximateEqual() {
        val point1 = YSpace(Float.NaN, 10f).asDataPoint()
        val point2 = YSpace(Float.NaN, 10f).asDataPoint()

        assertThat(point1).isEqualTo(point2)
    }
}
