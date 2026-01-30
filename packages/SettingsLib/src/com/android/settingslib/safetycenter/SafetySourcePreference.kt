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
import androidx.preference.Preference
import com.android.settingslib.R
import androidx.core.content.withStyledAttributes

/**
 * A custom [Preference] for displaying information about a safety source.
 *
 * @property safetySource The ID of the safety source.
 * @property profile The [Profile] associated with the safety source.
 */
class SafetySourcePreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0
) : Preference(context, attrs, defStyleAttr, defStyleRes) {

    /** The ID of the safety source. */
    var safetySource: String? = null

    /** The profile associated with the safety source. */
    var profile: Profile = Profile.PERSONAL

    init {
        if (attrs != null) {
            context.withStyledAttributes(
                attrs,
                R.styleable.SafetySourcePreference,
                defStyleAttr,
                defStyleRes
            ) {
                safetySource = getString(R.styleable.SafetySourcePreference_safetySource)

                val profileIntValue = getInt(R.styleable.SafetySourcePreference_profile,
                    DEFAULT_PROFILE_INT_VALUE)
                profile = Profile.fromIntValue(profileIntValue)
            }
        }
    }

    /**
     * Defines the possible profiles for a safety source.
     *
     * @property intValue The integer value corresponding to the profile in XML.
     */
    enum class Profile(val intValue: Int) {
        PERSONAL(0),
        WORK(1),
        PRIVATE(2);

        companion object {
            private val intValueToProfileMap: Map<Int, Profile> =
                entries.associateBy { it.intValue }

            /**
             * Returns the [Profile] enum value corresponding to the given integer value.
             * If the integer value does not match any [Profile] it will return [PERSONAL] value.
             */
            fun fromIntValue(intValue: Int): Profile = intValueToProfileMap[intValue] ?: PERSONAL
        }
    }

    companion object {
        private const val DEFAULT_PROFILE_INT_VALUE = 0
    }
}
