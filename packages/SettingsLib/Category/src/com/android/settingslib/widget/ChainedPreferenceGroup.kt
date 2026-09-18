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

package com.android.settingslib.widget

import android.content.Context
import android.util.AttributeSet
import androidx.core.view.isVisible
import androidx.preference.Preference
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceViewHolder
import androidx.recyclerview.widget.RecyclerView

/** A [PreferenceGroup] that has no UI effect. The following two Preference Screen will look
 *  same in the UI.
 * ====================
 * Preference Screen
 * - Preference 1
 * - ChainedPreferenceGroup
 *   - Preference 2
 *   - Preference 3
 * - Preference 4
 * ====================
 * Preference Screen
 * - Preference 1
 * - Preference 2
 * - Preference 3
 * - Preference 4
 * ====================
 */
class ChainedPreferenceGroup
@JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0,
) : PreferenceGroup(context, attrs, defStyleAttr, defStyleRes), ChainedMixin {

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        holder.isDividerAllowedAbove = false
        holder.isDividerAllowedBelow = false

        holder.itemView.isVisible = false
        holder.itemView.setLayoutParams(RecyclerView.LayoutParams(0, 0))
    }

    override fun addPreference(preference: Preference): Boolean {
        if (preference is PreferenceGroup) {
            return false
        }
        return super.addPreference(preference)
    }
}
