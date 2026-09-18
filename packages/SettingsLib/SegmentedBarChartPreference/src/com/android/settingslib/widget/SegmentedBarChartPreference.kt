/*
 * Copyright (C) 2026 The Android Open Source Project
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
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.android.settingslib.widget.preference.segmentedbarchart.R

/**
 * A preference that displays a [SegmentedBarChartView] to visualize data distribution.
 */
class SegmentedBarChartPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0
) : Preference(context, attrs, defStyleAttr, defStyleRes), GroupSectionDividerMixin {

    /**
     * Keep a reference to the view so we can update it later
     */
    private var barView: SegmentedBarChartView? = null

    /**
     * Cache data in case [setSegments] is called *before* the view is created
     */
    private var pendingSegments: List<SegmentItem>? = null
    private var pendingListener: SegmentedBarChartView.OnSegmentClickListener? = null

    init {
        layoutResource = R.layout.segmented_bar_preference_expressive
        isSelectable = false
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        holder.isDividerAllowedBelow = false
        holder.isDividerAllowedAbove = false
        holder.itemView.isFocusable = false
        holder.itemView.isClickable = false

        barView = holder.findViewById(R.id.segmented_bar) as? SegmentedBarChartView
        pendingSegments?.let {
            barView?.setSegments(it)
            pendingSegments = null
        }

        if (pendingListener != null) {
            barView?.setOnSegmentClickListener(pendingListener)
        }
    }

    /**
     * Sets the segments to be displayed in the bar chart.
     *
     * @param segments The list of [SegmentItem]s to display.
     */
    fun setSegments(segments: List<SegmentItem>) {
        if (barView != null) {
            barView?.setSegments(segments)
        } else {
            pendingSegments = segments
        }
    }

    /**
     * Registers a single listener for the entire chart to handle clicks on individual segments.
     *
     * The listener receives the clicked [SegmentItem], allowing you to define distinct behaviors
     * based on the segment's ID or label (e.g., navigating to different screens for "Audio" vs "Images").
     *
     * @param listener The callback invoked with the [SegmentItem] data when a user taps a segment.
     */
    fun setOnSegmentClickListener(listener: SegmentedBarChartView.OnSegmentClickListener) {
        if (barView != null) {
            barView?.setOnSegmentClickListener(listener)
        } else {
            pendingListener = listener
        }
    }
}
