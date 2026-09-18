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

package com.android.server.devicepolicy

import android.app.admin.ListOfStringPolicyValue
import android.app.admin.StringPolicyValue
import android.os.Parcel
import android.os.Parcelable
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith


@RunWith(AndroidJUnit4::class)
class PolicyValueTest {
    private fun toParcel(parcelable: Parcelable) = Parcel.obtain().apply {
        parcelable.writeToParcel(this, 0)
        setDataPosition(0)
    }

    @Test
    fun serialize_emptyString_matches() {
        val original = StringPolicyValue("")

        val parcel = toParcel(original)
        val parsed = StringPolicyValue.CREATOR.createFromParcel(parcel)

        assertThat(parsed).isEqualTo(original)
    }

    @Test
    fun serialize_testString_matches() {
        val original = StringPolicyValue("testString")

        val parcel = toParcel(original)
        val parsed = StringPolicyValue.CREATOR.createFromParcel(parcel)

        assertThat(parsed).isEqualTo(original)
    }

    @Test
    fun serialize_emptyStringList_matches() {
        val original = ListOfStringPolicyValue(listOf())

        val parcel = toParcel(original)
        val parsed = ListOfStringPolicyValue.CREATOR.createFromParcel(parcel)

        assertThat(parsed).isEqualTo(original)
    }

    @Test
    fun serialize_filledStringList_matches() {
        val original = ListOfStringPolicyValue(
            listOf(
                "firstTestString",
                "secondTestString",
            )
        )

        val parcel = toParcel(original)
        val parsed = ListOfStringPolicyValue.CREATOR.createFromParcel(parcel)

        assertThat(parsed).isEqualTo(original)
    }
}