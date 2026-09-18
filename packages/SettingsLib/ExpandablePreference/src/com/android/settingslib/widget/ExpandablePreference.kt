/*
 * Copyright (C) 2024 The Android Open Source Project
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
import android.view.View
import android.widget.ImageView
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.preference.Preference
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceViewHolder
import com.android.settingslib.widget.preference.expandable.R

class ExpandablePreference
@JvmOverloads
constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0,
) : PreferenceGroup(context, attrs, defStyleAttr, defStyleRes), Expandable {

    private var isExpanded = false
    private var expandIcon: ImageView? = null
    private var isDirty = true // Flag to track changes
    var onPreferenceExpansionStateChangeListener: OnPreferenceExpansionStateChangeListener? = null

    interface OnPreferenceExpansionStateChangeListener {
        fun onExpansionStateChange(isExpanded: Boolean)
    }

    init {
        layoutResource =
            com.android.settingslib.widget.theme.R.layout.settingslib_expressive_preference
        widgetLayoutResource = R.layout.settingslib_widget_expandable_icon
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)

        holder.isDividerAllowedAbove = false
        holder.isDividerAllowedBelow = false

        expandIcon = holder.findViewById(R.id.expand_icon) as ImageView?

        updateExpandedState()

        holder.itemView.setOnClickListener { toggleExpansion() }
        updateAccessibilityState(holder.itemView)
    }

    override fun addPreference(preference: Preference): Boolean {
        preference.isVisible = isExpanded
        return super.addPreference(preference)
    }

    override fun onPrepareAddPreference(preference: Preference): Boolean {
        preference.isVisible = isExpanded
        return super.onPrepareAddPreference(preference)
    }

    override fun isExpanded(): Boolean {
        return isExpanded
    }

    private fun updateAccessibilityState(view: View) {
        // Replace the click action with the appropriate action based on the expansion state.
        ViewCompat.replaceAccessibilityAction(
            view,
            AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_CLICK,
            context.getString(
                if (isExpanded) {
                    com.android.settingslib.widget.theme.R.string
                        .settingslib_expressive_text_collapse
                } else {
                    com.android.settingslib.widget.theme.R.string.settingslib_expressive_text_expand
                }
            ),
            /* command= */ null,
        )
        // Add the appropriate action based on the expansion state and set the expanded state.
        ViewCompat.setAccessibilityDelegate(
            view,
            object : AccessibilityDelegateCompat() {
                override fun onInitializeAccessibilityNodeInfo(
                    host: View,
                    info: AccessibilityNodeInfoCompat,
                ) {
                    super.onInitializeAccessibilityNodeInfo(host, info)
                    if (isExpanded) {
                        info.expandedState = AccessibilityNodeInfoCompat.EXPANDED_STATE_FULL
                        info.addAction(
                            AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_COLLAPSE
                        )
                    } else {
                        info.expandedState = AccessibilityNodeInfoCompat.EXPANDED_STATE_COLLAPSED
                        info.addAction(
                            AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_EXPAND
                        )
                    }
                }
            },
        )
        // Set the state description to the appropriate string based on the expansion state.
        ViewCompat.setStateDescription(
            view,
            context.getString(
                if (isExpanded) {
                    com.android.settingslib.widget.theme.R.string
                        .settingslib_expressive_expanded_state_expanded
                } else {
                    com.android.settingslib.widget.theme.R.string
                        .settingslib_expressive_expanded_state_collapsed
                }
            ),
        )
    }

    private fun toggleExpansion() {
        isExpanded = !isExpanded
        isDirty = true // Mark as dirty when expansion state changes
        updateExpandedState()
        notifyChanged()
    }

    override fun setExpanded(expanded: Boolean) {
        if (isExpanded == expanded) {
            return
        }

        isExpanded = expanded
        isDirty = true
        updateExpandedState()
        notifyChanged()

        onPreferenceExpansionStateChangeListener?.onExpansionStateChange(isExpanded)
    }

    private fun updateExpandedState() {
        expandIcon?.rotation =
            when (isExpanded) {
                true -> ROTATION_EXPANDED
                false -> ROTATION_COLLAPSED
            }

        if (isDirty) {
            (0 until preferenceCount).forEach { i -> getPreference(i).isVisible = isExpanded }
            isDirty = false
        }
    }

    companion object {
        private const val ROTATION_EXPANDED = 180f
        private const val ROTATION_COLLAPSED = 0f
    }
}
