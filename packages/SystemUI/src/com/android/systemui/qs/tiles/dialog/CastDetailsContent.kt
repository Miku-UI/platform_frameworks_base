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

package com.android.systemui.qs.tiles.dialog

import android.content.Context
import android.database.DataSetObserver
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ListAdapter
import android.widget.ListView
import android.widget.TextView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.android.compose.ui.graphics.painter.rememberDrawablePainter
import com.android.internal.R
import com.android.internal.app.MediaRouteChooserContentManager
import com.android.internal.app.MediaRouteControllerContentManager
import com.android.systemui.res.R as SystemUiR

private val TILE_DETAILS_HORIZONTAL_PADDING = SystemUiR.dimen.tile_details_horizontal_padding
private val MAX_CAST_LIST_HEIGHT = 5000.dp

@Composable
fun CastDetailsContent(castDetailsViewModel: CastDetailsViewModel) {
    if (castDetailsViewModel.shouldShowChooserDialog()) {
        val contentManager: MediaRouteChooserContentManager = remember {
            castDetailsViewModel.createChooserContentManager()
        }
        CastChooserView(contentManager, castDetailsViewModel)
        return
    }

    val contentManager: MediaRouteControllerContentManager = remember {
        castDetailsViewModel.createControllerContentManager()
    }

    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = rememberDrawablePainter(castDetailsViewModel.deviceIcon),
            contentDescription = null,
        )
        CastControllerView(contentManager)
        CastControllerDisconnectButton(contentManager)
    }
}

@Composable
fun CastChooserView(
    contentManager: MediaRouteChooserContentManager,
    castDetailsViewModel: CastDetailsViewModel,
) {
    var dataObserver: DataSetObserver? = null
    var adapter: ListAdapter? = null

    AndroidView(
        // Use heightIn on this AndroidView to ensure it measures to a non-zero height that works
        // within the scrollable area in the `TileDetails`.
        modifier =
            Modifier.fillMaxWidth()
                .heightIn(max = MAX_CAST_LIST_HEIGHT)
                .testTag(CastDetailsViewModel.CHOOSER_VIEW_TEST_TAG),
        factory = { context ->
            // Inflate with the existing dialog xml layout
            val view =
                LayoutInflater.from(context).inflate(R.layout.media_route_chooser_dialog, null)
            contentManager.bindViews(view)
            contentManager.onAttachedToWindow()

            val listView = view.findViewById<ListView>(R.id.media_route_list)
            (listView.layoutParams as? LinearLayout.LayoutParams)?.apply {
                weight = 0.0f
                height = ViewGroup.LayoutParams.WRAP_CONTENT
                listView.layoutParams = this
            }

            customizeView(listView)

            // Hide the subtitle TextView in the empty view.
            val emptyViewSubtitle = view.findViewById<TextView>(R.id.empty_subtitle)
            emptyViewSubtitle.visibility = View.GONE

            // Listen to the adapter data change and `customizeView` when changes occur.
            adapter = listView.adapter
            dataObserver =
                object : DataSetObserver() {
                    override fun onChanged() {
                        super.onChanged()
                        if (adapter?.count != 0) {
                            castDetailsViewModel.setMediaRouteDeviceSubTitle("")
                        }
                        customizeView(listView)
                    }
                }
            adapter?.registerDataSetObserver(dataObserver)

            view
        },
        onRelease = {
            contentManager.onDetachedFromWindow()
            adapter?.unregisterDataSetObserver(dataObserver)
        },
    )
}

@Composable
fun CastControllerView(contentManager: MediaRouteControllerContentManager) {
    AndroidView(
        modifier = Modifier.fillMaxWidth().testTag(CastDetailsViewModel.CONTROLLER_VIEW_TEST_TAG),
        factory = { context ->
            // Inflate with the existing dialog xml layout
            val view =
                LayoutInflater.from(context).inflate(R.layout.media_route_controller_dialog, null)
            contentManager.bindViews(view)
            contentManager.onAttachedToWindow()

            view
        },
        onRelease = { contentManager.onDetachedFromWindow() },
    )
}

@Composable
fun CastControllerDisconnectButton(contentManager: MediaRouteControllerContentManager) {
    Button(
        onClick = { contentManager.onDisconnectButtonClick() },
        modifier =
            Modifier.fillMaxWidth()
                .padding(horizontal = dimensionResource(TILE_DETAILS_HORIZONTAL_PADDING)),
    ) {
        Text(text = stringResource(id = SystemUiR.string.quick_settings_cast_disconnect))
    }
}

private fun customizeView(listView: ListView) {
    val context = listView.context

    // This code will run after the ListView has had a chance to complete its layout.
    listView.post {
        val visibleChildCount = listView.childCount
        val adapter = listView.adapter
        val totalItemCount = adapter?.count ?: 0

        if (adapter == null || totalItemCount == 0) {
            return@post
        }

        for (i in 0 until visibleChildCount) {
            val child = listView.getChildAt(i) as LinearLayout
            val adapterPosition = listView.getPositionForView(child)
            if (adapterPosition != ListView.INVALID_POSITION) {
                val entry = child.getChildAt(0) as LinearLayout
                entry.background =
                    when {
                        totalItemCount == 1 ->
                            context.getDrawable(SystemUiR.drawable.settingslib_entry_bg_off)
                        adapterPosition == 0 ->
                            context.getDrawable(SystemUiR.drawable.settingslib_entry_bg_off_start)
                        adapterPosition == totalItemCount - 1 ->
                            context.getDrawable(SystemUiR.drawable.settingslib_entry_bg_off_end)
                        else ->
                            context.getDrawable(SystemUiR.drawable.settingslib_entry_bg_off_middle)
                    }

                setPadding(context, child)

                val titleTextView = entry.requireViewById<TextView>(R.id.text1)
                titleTextView.setTextAppearance(
                    SystemUiR.style.TextAppearance_TileDetailsEntryTitle
                )
                val subTitleTextView = entry.requireViewById<TextView>(R.id.text2)
                subTitleTextView.setTextAppearance(
                    SystemUiR.style.TextAppearance_TileDetailsEntrySubTitle
                )
            }
        }
    }
}

private fun setPadding(context: Context, targetBackgroundView: LinearLayout) {
    val horizontalPadding = context.resources.getDimensionPixelSize(TILE_DETAILS_HORIZONTAL_PADDING)
    targetBackgroundView.setPadding(
        horizontalPadding,
        targetBackgroundView.paddingTop,
        horizontalPadding,
        targetBackgroundView.paddingBottom,
    )
}
