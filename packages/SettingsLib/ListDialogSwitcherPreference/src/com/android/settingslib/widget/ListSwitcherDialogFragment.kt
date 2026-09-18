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

import android.app.Dialog
import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.settingslib.widget.preference.switcher.R
import com.google.android.material.button.MaterialButton

/**
 * A [DialogFragment] that displays a list of selectable items for a [ListDialogSwitcherPreference].
 *
 * This dialog retrieves its configuration and item list from the shared [ListSwitcherViewModel]
 * and communicates user selections back to it. It is not intended to be used directly; it is
 * shown by the framework when a [ListDialogSwitcherPreference] is clicked.
 */
class ListSwitcherDialogFragment : DialogFragment() {

    private lateinit var viewModel: ListSwitcherViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(requireActivity())[ListSwitcherViewModel::class.java]
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        val inflater = LayoutInflater.from(context)
        val parent = android.widget.FrameLayout(context)
        val view = inflater.inflate(R.layout.list_switcher_dialog, parent, false)

        // Set the dialog height to 60% of the screen height
        val windowManager = requireActivity().windowManager
        val screenHeight = windowManager.currentWindowMetrics.bounds.height()
        val maxHeightInPixels = (screenHeight * 0.6f).toInt()
        val itemListWrapper = view.findViewById<View>(R.id.item_list_wrapper)
        val layoutParams =
            itemListWrapper.layoutParams as LayoutParams

        layoutParams.matchConstraintMaxHeight = maxHeightInPixels
        itemListWrapper.layoutParams = layoutParams

        val recyclerView = view.findViewById<RecyclerView>(R.id.item_list)
        val titleView = view.findViewById<TextView>(R.id.dialog_title)
        val dialogButton =
            view.findViewById<MaterialButton>(R.id.dialog_button)

        viewModel.dialogTitle?.let {
            titleView.text = it
            titleView.visibility = View.VISIBLE
        }

        viewModel.dialogButtonText?.let {
            dialogButton.text = it
            dialogButton.visibility = View.VISIBLE
            dialogButton.setOnClickListener {
                viewModel.onDialogButtonClicked()
                dismiss()
            }
        }

        viewModel.dialogButtonIcon?.let {
            dialogButton.setCompoundDrawablesRelativeWithIntrinsicBounds(it, null, null, null)
        }

        val layoutManager = LinearLayoutManager(context)
        recyclerView.layoutManager = layoutManager
        recyclerView.adapter = ItemAdapter(context, viewModel.items.orEmpty()) { item ->
            viewModel.onItemSelected(item)
            dismiss()
        }

        // Add gap between the list elements
        val spaceSize = context
            .resources
            .getDimensionPixelSize(
                com.android.settingslib.widget.theme.R.dimen.settingslib_expressive_space_extrasmall1
            )

        recyclerView.addItemDecoration(object : RecyclerView.ItemDecoration() {
            override fun getItemOffsets(
                outRect: Rect, view: View,
                parent: RecyclerView,
                state: RecyclerView.State,
            ) {
                with(outRect) {
                    if (parent.getChildAdapterPosition(view) != 0) {
                        top = spaceSize
                    }
                    left = 0
                    right = 0
                    bottom = 0
                }
            }
        })

        return AlertDialog.Builder(context)
            .setView(view)
            .create()
    }

    private class ItemAdapter(
        private val context: Context,
        private val items: List<ListDialogItemInfo>,
        private val itemListener: (ListDialogItemInfo) -> Unit,
    ) : RecyclerView.Adapter<ItemViewHolder>() {

        private val listItemStartEndPadding: Int = context.resources.getDimensionPixelSize(
            com.android.settingslib.widget.theme.R.dimen.settingslib_expressive_space_medium1
        )

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
            val view =
                LayoutInflater.from(context).inflate(R.layout.list_dialog_item, parent, false)
            return ItemViewHolder(view)
        }

        override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
            val item = items[position]
            holder.bind(item, itemListener)
            val v = holder.itemView

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
                v.setBackgroundResource(getBackgroundResource(position))
            }

            v.setPaddingRelative(listItemStartEndPadding, v.paddingTop, listItemStartEndPadding, v.paddingBottom)
        }

        override fun getItemCount(): Int = items.size

        private fun getBackgroundResource(position: Int): Int {
            return when {
                itemCount == 1 -> com.android.settingslib.widget.theme.R.drawable.settingslib_round_background
                position == 0 -> com.android.settingslib.widget.theme.R.drawable.settingslib_round_background_top
                position == itemCount - 1 -> com.android.settingslib.widget.theme.R.drawable.settingslib_round_background_bottom
                else -> com.android.settingslib.widget.theme.R.drawable.settingslib_round_background_center
            }
        }
    }

    private class ItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val iconView: ImageView = itemView.findViewById(R.id.item_icon)
        private val titleView: TextView = itemView.findViewById(R.id.item_title)
        private val summaryView: TextView = itemView.findViewById(R.id.item_summary)

        fun bind(item: ListDialogItemInfo, itemListener: (ListDialogItemInfo) -> Unit) {
            titleView.text = item.title
            summaryView.text = item.summary
            iconView.setCircularIcon(item.icon)
            itemView.setOnClickListener { itemListener(item) }
        }
    }

    companion object {
        private const val ARG_KEY = "key"

        /**
         * Creates a new instance of the dialog fragment.
         *
         * @param key The unique key of the preference that is showing this dialog.
         * @return A new [ListSwitcherDialogFragment] instance.
         */
        fun newInstance(key: String): ListSwitcherDialogFragment {
            val fragment = ListSwitcherDialogFragment()
            val args = Bundle()
            args.putString(ARG_KEY, key)
            fragment.arguments = args
            return fragment
        }
    }
}