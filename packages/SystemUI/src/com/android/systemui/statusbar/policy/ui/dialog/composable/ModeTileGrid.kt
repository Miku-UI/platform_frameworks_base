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

package com.android.systemui.statusbar.policy.ui.dialog.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.systemui.Flags
import com.android.systemui.common.ui.compose.PagerDots
import com.android.systemui.compose.modifiers.sysUiResTagContainer
import com.android.systemui.compose.modifiers.sysuiResTag
import com.android.systemui.statusbar.policy.ui.dialog.viewmodel.ModesDialogViewModel

@Composable
fun ModeTileGrid(
    viewModel: ModesDialogViewModel,
    modifier: Modifier = Modifier,
    inDetailsView: Boolean = false,
) {
    val tiles by viewModel.tiles.collectAsStateWithLifecycle(initialValue = emptyList())

    val verticalSpacing = if (inDetailsView) 2.dp else 8.dp

    if (Flags.modesUiDialogPaging()) {
        val tilesPerPage = 3
        val totalPages = { (tiles.size + tilesPerPage - 1) / tilesPerPage }
        val pagerState = rememberPagerState(initialPage = 0, pageCount = totalPages)

        Column {
            HorizontalPager(
                state = pagerState,
                modifier = modifier.fillMaxWidth(),
                pageSpacing = 16.dp,
                verticalAlignment = Alignment.Top,
                // Pre-emptively layout and compose the next page, to make sure the height stays
                // the same even if we have fewer than [tilesPerPage] tiles on the last page.
                beyondViewportPageCount = 1,
            ) { page ->
                Column(
                    modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                    verticalArrangement =
                        Arrangement.spacedBy(verticalSpacing, alignment = Alignment.Top),
                ) {
                    val startIndex = page * tilesPerPage
                    val endIndex = minOf((page + 1) * tilesPerPage, tiles.size)
                    for (index in startIndex until endIndex) {
                        ModeTile(
                            viewModel = tiles[index],
                            modifier = Modifier.fillMaxWidth(),
                            type = getModeTileType(inDetailsView, index, tiles.size),
                        )
                    }
                }
            }

            PagerDots(
                pagerState = pagerState,
                activeColor = MaterialTheme.colorScheme.primary,
                nonActiveColor = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp),
            )
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(1),
            modifier =
                modifier
                    .fillMaxWidth()
                    .heightIn(max = 280.dp)
                    .sysUiResTagContainer()
                    .sysuiResTag("scroll_view"),
            verticalArrangement = Arrangement.spacedBy(verticalSpacing),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(tiles.size, key = { index -> tiles[index].id }) { index ->
                ModeTile(
                    viewModel = tiles[index],
                    type = getModeTileType(inDetailsView, index, tiles.size),
                )
            }
        }
    }
}

fun getModeTileType(inDetailsView: Boolean, index: Int, tilesSize: Int): ModeTileType {
    return if (inDetailsView) {
        if (tilesSize == 1) return ModeTileType.ONLY_TILE

        when (index) {
            0 -> ModeTileType.START_TILE
            tilesSize - 1 -> ModeTileType.END_TILE
            else -> ModeTileType.MIDDLE_TILE
        }
    } else {
        ModeTileType.DEFAULT
    }
}
