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

package com.android.systemui.accessibility.floatingmenu

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ListPopupWindow
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import com.android.internal.annotations.VisibleForTesting
import com.android.systemui.accessibility.floatingmenu.R as FloatingMenuR
import com.android.systemui.res.R

/** A popup menu that displays additional options for the accessibility floating menu. */
class MoreOptionsPopup(
    context: Context,
    private val nextMovePositionHint: String,
    private val listener: OnItemClickListener,
) {

    interface OnItemClickListener {
        fun onEditClicked()

        fun onMoveClicked()

        fun onRemoveAllClicked()
    }

    fun show(anchorView: View) {
        val context = anchorView.context
        val items = createMoreOptionsItems(context)
        val adapter = createMoreOptionsAdapter(context, items)
        val popup = createListPopupWindow(context)

        configurePopup(popup, anchorView, adapter)

        popup.setOnItemClickListener { _, _, position, _ ->
            onMoreOptionsItemClicked(items[position])
            popup.dismiss()
        }

        popup.show()
    }

    @VisibleForTesting
    fun createListPopupWindow(context: Context): ListPopupWindow {
        return ListPopupWindow(context)
    }

    private fun createMoreOptionsItems(context: Context): List<MoreOptionsItem> {
        return listOf(
            MoreOptionsItem(
                context.getString(R.string.more_options_edit),
                R.drawable.ic_edit,
                R.id.action_edit,
            ),
            MoreOptionsItem(
                context.getString(R.string.more_options_move),
                R.drawable.ic_move,
                R.id.action_move,
                customActionLabel = nextMovePositionHint,
            ),
            MoreOptionsItem(
                context.getString(R.string.more_options_remove_all),
                R.drawable.ic_close,
                R.id.action_remove_all,
            ),
        )
    }

    private fun createMoreOptionsAdapter(
        context: Context,
        items: List<MoreOptionsItem>,
    ): ArrayAdapter<MoreOptionsItem> {
        return object :
            ArrayAdapter<MoreOptionsItem>(context, FloatingMenuR.layout.floating_menu_item, items) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view =
                    convertView
                        ?: LayoutInflater.from(getContext())
                            .inflate(FloatingMenuR.layout.floating_menu_item, parent, false)

                val item = getItem(position)!!
                val icon = view.findViewById<ImageView>(FloatingMenuR.id.menu_item_icon)
                val text = view.findViewById<TextView>(FloatingMenuR.id.menu_item_text)

                icon.setImageResource(item.iconResId)
                text.text = item.text

                if (item.customActionLabel != null) {
                    ViewCompat.replaceAccessibilityAction(
                        view,
                        AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_CLICK,
                        item.customActionLabel,
                        null,
                    )
                } else {
                    ViewCompat.replaceAccessibilityAction(
                        view,
                        AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_CLICK,
                        null,
                        null,
                    )
                }

                return view
            }
        }
    }

    private fun configurePopup(
        popup: ListPopupWindow,
        anchorView: View,
        adapter: ArrayAdapter<MoreOptionsItem>,
    ) {
        val context = anchorView.context
        val margin =
            context.resources.getDimensionPixelSize(R.dimen.accessibility_floating_menu_margin)
        val popupWidth = calculateMaxItemWidth(context, adapter) + margin

        popup.anchorView = anchorView
        popup.setAdapter(adapter)
        popup.width = popupWidth
        popup.height = ListPopupWindow.WRAP_CONTENT
        popup.setBackgroundDrawable(
            context.getDrawable(FloatingMenuR.drawable.floating_menu_background)
        )
        popup.isModal = true

        val displayRect = android.graphics.Rect()
        anchorView.getWindowVisibleDisplayFrame(displayRect)

        val location = IntArray(2)
        anchorView.getLocationInWindow(location)
        val anchorX = location[0]

        val isOnRightSide = anchorX + (anchorView.width / 2) > displayRect.centerX()
        if (isOnRightSide) {
            popup.setDropDownGravity(android.view.Gravity.RIGHT)
        } else {
            popup.setDropDownGravity(android.view.Gravity.LEFT)
        }
    }

    private fun onMoreOptionsItemClicked(item: MoreOptionsItem) {
        when (item.actionId) {
            R.id.action_edit -> listener.onEditClicked()
            R.id.action_move -> listener.onMoveClicked()
            R.id.action_remove_all -> listener.onRemoveAllClicked()
        }
    }

    private fun calculateMaxItemWidth(
        context: Context,
        adapter: ArrayAdapter<MoreOptionsItem>,
    ): Int {
        var maxWidth = 0
        val measureParent = FrameLayout(context)
        var itemView: View? = null
        var itemType = 0

        for (i in 0 until adapter.count) {
            val positionType = adapter.getItemViewType(i)
            if (positionType != itemType) {
                itemType = positionType
                itemView = null
            }

            itemView = adapter.getView(i, itemView, measureParent)
            maxWidth = maxWidth.coerceAtLeast(measureViewWidth(itemView))
        }

        return maxWidth
    }

    private fun measureViewWidth(view: View): Int {
        val widthMeasureSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        val heightMeasureSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        view.measure(widthMeasureSpec, heightMeasureSpec)
        return view.measuredWidth
    }

    private data class MoreOptionsItem(
        val text: String,
        val iconResId: Int,
        val actionId: Int,
        val customActionLabel: String? = null,
    )
}
