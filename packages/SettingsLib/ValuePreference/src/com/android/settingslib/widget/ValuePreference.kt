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
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.TextView
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.android.settingslib.widget.preference.value.R

/** The ValuePreference shows a text which describe a feature. */
class ValuePreference
@JvmOverloads
constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0,
) : Preference(context, attrs, defStyleAttr, defStyleRes), GroupSectionDividerMixin {

    /** The secondary title of value preference. */
    var secondaryTitle: String? = null
        set(value) {
            if (field != value) {
                field = value
                notifyChanged()
            }
        }

    /** The secondary summary of value preference. */
    var secondarySummary: String? = null
        set(value) {
            if (field != value) {
                field = value
                notifyChanged()
            }
        }

    /** The visibility of secondary container. */
    var secondaryContainerVisibility: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                notifyChanged()
            }
        }

    /** The first content description of value preference. */
    var firstContentDescription: String? = null
        set(value) {
            if (field != value) {
                field = value
                notifyChanged()
            }
        }

    /** The secondary content description of value preference. */
    var secondaryContentDescription: String? = null
        set(value) {
            if (field != value) {
                field = value
                notifyChanged()
            }
        }

    /** A callback set to be invoked when value preference is clicked. */
    var onValueClickListener: OnValueClickListener? = null
        set(value) {
            if (field != value) {
                field = value
                notifyChanged()
            }
        }

    init {
        layoutResource = R.layout.settingslib_expressive_value_preference
    }

    /** Interface definition for a callback to be invoked when a view is clicked. */
    interface OnValueClickListener {
        /** Called when a view has been clicked. */
        fun onValueClick(index: Int)
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        holder.isDividerAllowedAbove = false
        holder.isDividerAllowedBelow = false
        // ValuePreference should be not clickable
        holder.itemView.isClickable = false

        holder.findViewById(R.id.value_container_1)?.apply {
            // Prevent setting clickable to true when invoking setOnClickListener
            if (onValueClickListener != null) {
                setOnClickListener { onValueClickListener?.onValueClick(FIRST_ITEM) }
            }
            contentDescription = firstContentDescription
        }
        holder.findViewById(R.id.value_container_2)?.apply {
            if (secondaryContainerVisibility) {
                // Prevent setting clickable to true when invoking setOnClickListener
                if (onValueClickListener != null) {
                    setOnClickListener { onValueClickListener?.onValueClick(SECOND_ITEM) }
                }
                visibility = VISIBLE
                contentDescription = secondaryContentDescription
            } else {
                visibility = GONE
            }
        }

        if (secondaryContainerVisibility) {
            (holder.findViewById(R.id.title2) as? TextView)?.text = secondaryTitle
            (holder.findViewById(R.id.summary2) as? TextView)?.text = secondarySummary
        }
    }

    companion object {
        const val FIRST_ITEM = 1
        const val SECOND_ITEM = 2
    }
}
