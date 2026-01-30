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

package com.android.settingslib.collapsingtoolbar.widget

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.android.settingslib.collapsingtoolbar.R

class ScrollableToolbarItemLayout(context: Context, attrs: AttributeSet?) :
    HorizontalScrollView(context, attrs) {
    private val container: LinearLayout
    private val toolbarItemViews = mutableListOf<View>()
    private var selectedItem: View? = null
    private var onItemSelectedListener: OnItemSelectedListener? = null
    private var toolbarItemList: List<ToolbarItem> = emptyList()

    init {
        isHorizontalScrollBarEnabled = false

        container = LinearLayout(context)
        container.orientation = LinearLayout.HORIZONTAL
        addView(container)
    }

    data class ToolbarItem(val iconResId: Int?, val text: String)

    interface OnItemSelectedListener {
        fun onItemSelected(position: Int, toolbarItem: ToolbarItem)
    }

    fun setOnItemSelectedListener(listener: OnItemSelectedListener) {
        onItemSelectedListener = listener
    }

    fun removeOnItemSelectedListener() {
        onItemSelectedListener = null
    }

    fun onItemSelected(dataList: List<ToolbarItem>) {
        toolbarItemList = dataList.take(MAX_ITEMS)
        setupItems()
    }

    private fun setupItems() {
        container.removeAllViews()
        toolbarItemViews.clear()
        selectedItem = null

        toolbarItemList.forEachIndexed { index, toolbarItem ->
            val itemView = LayoutInflater.from(context)
                .inflate(R.layout.settingslib_expressive_floating_toolbar_item, container, false)
            val iconImageView = itemView.findViewById<ImageView>(android.R.id.icon)
            val textView = itemView.findViewById<TextView>(android.R.id.text1)

            textView.text = toolbarItem.text
            toolbarItem.iconResId?.let { iconImageView.setImageResource(it) }
            itemView.apply {
                background = null
                findViewById<ImageView>(android.R.id.icon)?.visibility = GONE
                (findViewById(android.R.id.text1) as? TextView)?.setTextAppearance(
                    UNSELECTED_TEXT_APPEARANCE
                )
            }

            itemView.setOnClickListener { selectItem(itemView, index, toolbarItem) }

            toolbarItemViews.add(itemView)
            container.addView(itemView)

            // if there is any item, select the first one
            if (index == 0 && toolbarItemList.isNotEmpty()) {
                selectItem(itemView, index, toolbarItem)
            }
        }
    }

    private fun selectItem(newItem: View, position: Int, toolbarItem: ToolbarItem) {
        if (newItem == selectedItem) return

        selectedItem?.apply {
            background = null
            findViewById<ImageView>(android.R.id.icon)?.visibility = GONE
            (findViewById(android.R.id.text1) as? TextView)?.setTextAppearance(
                UNSELECTED_TEXT_APPEARANCE
            )
        }

        newItem.apply {
            background =
                context.getDrawable(
                    R.drawable.settingslib_expressive_floating_toolbar_item_background
                )
            findViewById<ImageView>(android.R.id.icon)?.visibility = VISIBLE
            (findViewById(android.R.id.text1) as? TextView)?.setTextAppearance(
                SELECTED_TEXT_APPEARANCE
            )
        }

        selectedItem = newItem
        onItemSelectedListener?.onItemSelected(position, toolbarItem)

        scrollToItem(newItem)
    }

    private fun scrollToItem(itemView: View) {
        val x = itemView.left - (width - itemView.width) / 2
        smoothScrollTo(x, 0)
    }

    fun setSelectedItem(position: Int) {
        if (position in 0 until toolbarItemViews.size) {
            selectItem(toolbarItemViews[position], position, toolbarItemList[position])
        }
    }

    companion object {
        private const val MAX_ITEMS = 5
        private val SELECTED_TEXT_APPEARANCE =
            com.android.settingslib.widget.theme.R.style.TextAppearance_SettingsLib_TitleSmall_Emphasized
        private val UNSELECTED_TEXT_APPEARANCE =
            com.android.settingslib.widget.theme.R.style.TextAppearance_SettingsLib_LabelLarge
    }
}
