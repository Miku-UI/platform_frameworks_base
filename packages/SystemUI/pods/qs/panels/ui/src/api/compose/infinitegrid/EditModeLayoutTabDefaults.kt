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

package com.android.systemui.qs.panels.ui.compose.infinitegrid

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.systemui.common.ui.icons.PlayArrow

object EditModeLayoutTabDefaults {
    @Composable
    fun Brightness(modifier: Modifier = Modifier) {
        Slider(
            value = 5f,
            valueRange = 0f..10f,
            modifier = modifier.fillMaxWidth(),
            enabled = false,
            onValueChange = {},
        )
    }

    @Composable
    fun TilesGrid(modifier: Modifier = Modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(TileArrangement), modifier = modifier) {
            TilesRow(2)
            TilesRow(2)
            TilesRow(4)
            TilesRow(4)
        }
    }

    @Composable
    fun Media(modifier: Modifier = Modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(24.dp),
                    )
                    .padding(16.dp),
        ) {
            // TODO(b/485262315): Use resource
            Text("Media")

            Spacer(Modifier.weight(1f))

            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier.padding(horizontal = 16.dp)
                        .height(48.dp)
                        .aspectRatio(3 / 2f)
                        .background(
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(16.dp),
                        ),
            ) {
                Icon(
                    PlayArrow,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}

@Composable
private fun TilesRow(count: Int, modifier: Modifier = Modifier) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(TileArrangement),
        modifier = modifier.fillMaxWidth(),
    ) {
        repeat(count) { Tile(Modifier.weight(1f)) }
    }
}

@Composable
private fun Tile(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(72.dp)
            .background(
                color = MaterialTheme.colorScheme.secondary.copy(alpha = .3f),
                shape = RoundedCornerShape(50.dp),
            )
    )
}

private val TileArrangement = 6.dp
