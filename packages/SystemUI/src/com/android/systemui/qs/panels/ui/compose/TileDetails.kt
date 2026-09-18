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

package com.android.systemui.qs.panels.ui.compose

import android.os.Trace
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.trace
import com.android.systemui.bluetooth.qsdialog.BluetoothDetailsContent
import com.android.systemui.bluetooth.ui.viewModel.BluetoothDetailsViewModel
import com.android.systemui.plugins.qs.TileDetailsViewModel
import com.android.systemui.qs.flags.QsDetailedView
import com.android.systemui.qs.panels.ui.compose.infinitegrid.CommonTileDefaults.TileDetailsEntryWideCornerRadius
import com.android.systemui.qs.panels.ui.viewmodel.DetailsViewModel
import com.android.systemui.qs.tiles.dialog.AudioDetailsContent
import com.android.systemui.qs.tiles.dialog.AudioDetailsViewModel
import com.android.systemui.qs.tiles.dialog.CastDetailsContent
import com.android.systemui.qs.tiles.dialog.CastDetailsViewModel
import com.android.systemui.qs.tiles.dialog.InternetDetailsContent
import com.android.systemui.qs.tiles.dialog.InternetDetailsViewModel
import com.android.systemui.qs.tiles.dialog.ModesDetailsContent
import com.android.systemui.qs.tiles.dialog.ModesDetailsViewModel
import com.android.systemui.qs.ui.compose.borderOnFocus
import com.android.systemui.res.R

private val TileDetailsViewModel.traceName
    get() =
        ((this::class.simpleName ?: "TileDetailsViewModel") + "#title#" + title).takeLast(
            Trace.MAX_SECTION_NAME_LEN
        )

/**
 * Renders the detailed view for a Quick Settings tile.
 *
 * This composable displays the title, subtitle, and specific content for the currently active tile.
 * It also provides navigation buttons to close the details view or open the corresponding settings
 * page.
 *
 * @param modifier Modifier to be applied to the layout.
 * @param detailsViewModel ViewModel managing the state of the tile details view.
 * @param initialHeight Optional initial height for the detailed view.
 */
@Composable
fun TileDetails(
    modifier: Modifier = Modifier,
    detailsViewModel: DetailsViewModel,
    initialHeight: () -> Int? = { null },
) {

    if (!QsDetailedView.isEnabled) {
        throw IllegalStateException("QsDetailedView should be enabled")
    }

    val tileDetailedViewModel = detailsViewModel.activeTileDetails ?: return

    // State to track if the detailed content has finished loading.
    // If the tile requires async loading, it starts as false and should be set to true
    // once the content is ready; Otherwise, it is always true.
    var isContentReady by
        remember(tileDetailedViewModel) {
            mutableStateOf(!tileDetailedViewModel.requiresAsyncLoading)
        }

    trace(tileDetailedViewModel.traceName) {
        DisposableEffect(Unit) { onDispose { detailsViewModel.closeDetailedView() } }

        val title = tileDetailedViewModel.title
        val subTitle = tileDetailedViewModel.subTitle
        val colors = MaterialTheme.colorScheme

        Column(
            modifier =
                modifier
                    .fillMaxWidth()
                    .animateContentSize()
                    .then(
                        // Keep the detailed view at its initial height while async content loads.
                        // This prevents the view from briefly collapsing before the content is
                        // fully rendered.
                        if (tileDetailedViewModel.requiresAsyncLoading) {
                            initialHeight()?.let { height ->
                                Modifier.lockHeightUntilLoaded(
                                    targetHeight = height,
                                    isLoaded = isContentReady,
                                )
                            } ?: Modifier
                        } else {
                            Modifier
                        }
                    )
                    .heightIn(
                        min = TileDetailsDefaults.DetailsMinHeight,
                        max = TileDetailsDefaults.DetailsMaxHeight,
                    )
        ) {
            CompositionLocalProvider(
                value = LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant
            ) {
                Row(
                    modifier =
                        Modifier.fillMaxWidth()
                            .padding(
                                start = TileDetailsDefaults.TitleRowStart,
                                top = TileDetailsDefaults.TitleRowTop,
                                end = TileDetailsDefaults.TitleRowEnd,
                                bottom = TileDetailsDefaults.TitleRowBottom,
                            ),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = { detailsViewModel.closeDetailedView() },
                        colors =
                            IconButtonDefaults.iconButtonColors(contentColor = colors.onSurface),
                        modifier =
                            Modifier.borderOnFocus(
                                    MaterialTheme.colorScheme.secondary,
                                    CornerSize(TileDetailsEntryWideCornerRadius),
                                )
                                .size(TileDetailsDefaults.TitleRowButtonSize)
                                .align(Alignment.CenterVertically),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            // Description is TBD
                            contentDescription = "Back to QS panel",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Text(
                        text = title,
                        modifier = Modifier.align(Alignment.CenterVertically),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.onSurface,
                    )
                    IconButton(
                        onClick = {
                            tileDetailedViewModel.clickOnSettingsButton()
                            detailsViewModel.logOnSettingsClicked()
                        },
                        colors =
                            IconButtonDefaults.iconButtonColors(contentColor = colors.onSurface),
                        modifier =
                            Modifier.borderOnFocus(
                                    MaterialTheme.colorScheme.secondary,
                                    CornerSize(TileDetailsEntryWideCornerRadius),
                                )
                                .size(TileDetailsDefaults.TitleRowButtonSize)
                                .align(Alignment.CenterVertically),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            // Description is TBD
                            contentDescription = "Go to Settings",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                if (subTitle.isNotEmpty()) {
                    Text(
                        text = subTitle,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
            }

            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                MapTileDetailsContent(tileDetailedViewModel) { isContentReady = true }
            }
        }
    }
}

@Composable
private fun MapTileDetailsContent(
    tileDetailsViewModel: TileDetailsViewModel,
    onContentReady: () -> Unit,
) {
    when (tileDetailsViewModel) {
        is InternetDetailsViewModel -> InternetDetailsContent(tileDetailsViewModel, onContentReady)
        is BluetoothDetailsViewModel ->
            BluetoothDetailsContent(tileDetailsViewModel.detailsContentViewModel, onContentReady)

        is ModesDetailsViewModel -> ModesDetailsContent(tileDetailsViewModel)
        is CastDetailsViewModel -> CastDetailsContent(tileDetailsViewModel)
        is AudioDetailsViewModel -> AudioDetailsContent(tileDetailsViewModel)
    }
}

/**
 * A [Modifier] that locks the layout to a specific [targetHeight] until [isLoaded] becomes true.
 *
 * This is useful for maintaining a consistent size while asynchronous content (like an inflated
 * AndroidView) is loading, preventing the UI from jumping or flickering. Once [isLoaded] is true,
 * it allows the layout to size itself normally based on its content.
 */
private fun Modifier.lockHeightUntilLoaded(targetHeight: Int, isLoaded: Boolean): Modifier =
    this.layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)

        val lockedHeight =
            if (!isLoaded) {
                // If we have a target height and we aren't loaded yet, force the height.
                // We respect constraints.maxHeight (from heightIn) to avoid breaking the layout
                // rules.
                targetHeight.coerceAtMost(constraints.maxHeight)
            } else {
                placeable.height
            }

        layout(placeable.width, lockedHeight) { placeable.place(0, 0) }
    }

private object TileDetailsDefaults {
    val TitleRowButtonSize: Dp
        @Composable
        @ReadOnlyComposable
        get() = dimensionResource(id = R.dimen.tile_details_title_row_button_size)

    val TitleRowStart: Dp
        @Composable
        @ReadOnlyComposable
        get() = dimensionResource(id = R.dimen.tile_details_title_row_start)

    val TitleRowEnd: Dp
        @Composable
        @ReadOnlyComposable
        get() = dimensionResource(id = R.dimen.tile_details_title_row_end)

    val TitleRowTop = 14.dp
    val TitleRowBottom = 2.dp
    val DetailsMaxHeight = 680.dp
    val DetailsMinHeight = 120.dp
}
