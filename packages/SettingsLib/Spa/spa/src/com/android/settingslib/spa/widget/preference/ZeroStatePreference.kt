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

package com.android.settingslib.spa.widget.preference

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.star
import androidx.graphics.shapes.toPath
import com.android.settingslib.spa.framework.theme.SettingsSize
import com.android.settingslib.spa.framework.theme.SettingsSpace
import com.android.settingslib.spa.framework.theme.SettingsTheme

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ZeroStatePreference(icon: ImageVector, text: String = "", description: String = "") {
    val zeroStateShape = remember {
        RoundedPolygon.star(
            numVerticesPerRadius = 6,
            innerRadius = 0.8f,
            rounding = CornerRounding(0.3f),
        )
    }
    val clip = remember(zeroStateShape) { RoundedPolygonShape(polygon = zeroStateShape) }
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier =
                Modifier.clip(clip)
                    .background(MaterialTheme.colorScheme.surfaceBright)
                    .size(160.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(SettingsSize.large2),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        Column(
            modifier =
                Modifier.padding(
                    start = SettingsSpace.medium5,
                    top = SettingsSpace.small4,
                    end = SettingsSpace.medium5,
                    bottom = SettingsSpace.small1,
                ),
            verticalArrangement = Arrangement.spacedBy(SettingsSpace.extraSmall2),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (text.isNotEmpty()) {
                Text(
                    text = text,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleMediumEmphasized,
                )
            }
            if (description.isNotEmpty()) {
                Text(
                    text = description,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

private class RoundedPolygonShape(
    private val polygon: RoundedPolygon,
    private var matrix: Matrix = Matrix(),
) : Shape {
    private var path = Path()

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        path.rewind()
        path = polygon.toPath().asComposePath()

        matrix.reset()
        matrix.scale(size.width / 2f, size.height / 2f)
        matrix.translate(1f, 1f)
        matrix.rotateZ(30.0f)

        path.transform(matrix)
        return Outline.Generic(path)
    }
}

@Preview
@Composable
private fun ZeroStatePreferencePreview() {
    SettingsTheme {
        ZeroStatePreference(
            icon = Icons.Filled.History,
            text = "No recent search history",
            description = "Description",
        )
    }
}

@Preview
@Composable
private fun ZeroStatePreferenceMultiLinesPreview() {
    SettingsTheme {
        ZeroStatePreference(
            icon = Icons.Filled.History,
            text = "No recent search history No recent search history",
            description = "Description Description Description Description Description",
        )
    }
}
