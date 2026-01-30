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

package com.android.settingslib.safetycenter

import android.content.Context
import android.util.AttributeSet
import androidx.test.core.app.ApplicationProvider
import com.android.settingslib.safetycenter.SafetySourcePreference.Profile
import com.android.settingslib.R
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SafetySourcePreferenceTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun testDefaultValues() {
        val preference = SafetySourcePreference(context)

        assertThat(preference.safetySource).isNull()
        assertThat(preference.profile).isEqualTo(Profile.PERSONAL)
    }

    @Test
    fun testPersonalProfileValue() {
        val attrs = createAttributeSet(
            mapOf(
                "safetySource" to "MySafetySource",
                "profile" to Profile.PERSONAL.intValue.toString()
            )
        )

        val preference = SafetySourcePreference(context, attrs)

        assertThat(preference.safetySource).isEqualTo("MySafetySource")
        assertThat(preference.profile).isEqualTo(Profile.PERSONAL)
    }

    @Test
    fun testWorkProfileValue() {
        val attrs = createAttributeSet(
            mapOf(
                "safetySource" to "MySafetySource",
                "profile" to Profile.WORK.intValue.toString()
            )
        )

        val preference = SafetySourcePreference(context, attrs)

        assertThat(preference.safetySource).isEqualTo("MySafetySource")
        assertThat(preference.profile).isEqualTo(Profile.WORK)
    }

    @Test
    fun testPrivateProfileValue() {
        val attrs = createAttributeSet(
            mapOf(
                "safetySource" to "MySafetySource",
                "profile" to Profile.PRIVATE.intValue.toString()
            )
        )

        val preference = SafetySourcePreference(context, attrs)

        assertThat(preference.safetySource).isEqualTo("MySafetySource")
        assertThat(preference.profile).isEqualTo(Profile.PRIVATE)
    }

    private fun createAttributeSet(attributes: Map<String, String>): AttributeSet {
        val builder = Robolectric.buildAttributeSet()
        for ((name, value) in attributes) {
            val attributeId = when (name) {
                "safetySource" -> R.attr.safetySource
                "profile" -> R.attr.profile
                else -> throw IllegalArgumentException("Unknown attribute: $name")
            }
            builder.addAttribute(attributeId, value)
        }
        return builder.build()
    }
}
