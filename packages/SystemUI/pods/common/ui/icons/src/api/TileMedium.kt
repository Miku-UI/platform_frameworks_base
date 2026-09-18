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

package com.android.systemui.common.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val TileMedium: ImageVector
    get() {
        if (_TileMedium != null) {
            return _TileMedium!!
        }
        _TileMedium =
            ImageVector.Builder(
                    name = "TileMedium",
                    defaultWidth = 24.dp,
                    defaultHeight = 24.dp,
                    viewportWidth = 960f,
                    viewportHeight = 960f,
                )
                .apply {
                    path(fill = SolidColor(Color.Black)) {
                        moveTo(120f, 720f)
                        lineTo(120f, 560f)
                        quadTo(120f, 543f, 131.5f, 531.5f)
                        quadTo(143f, 520f, 160f, 520f)
                        lineTo(400f, 520f)
                        quadTo(417f, 520f, 428.5f, 531.5f)
                        quadTo(440f, 543f, 440f, 560f)
                        lineTo(440f, 720f)
                        quadTo(440f, 737f, 428.5f, 748.5f)
                        quadTo(417f, 760f, 400f, 760f)
                        lineTo(160f, 760f)
                        quadTo(143f, 760f, 131.5f, 748.5f)
                        quadTo(120f, 737f, 120f, 720f)
                        close()
                        moveTo(520f, 720f)
                        lineTo(520f, 560f)
                        quadTo(520f, 543f, 531.5f, 531.5f)
                        quadTo(543f, 520f, 560f, 520f)
                        lineTo(800f, 520f)
                        quadTo(817f, 520f, 828.5f, 531.5f)
                        quadTo(840f, 543f, 840f, 560f)
                        lineTo(840f, 720f)
                        quadTo(840f, 737f, 828.5f, 748.5f)
                        quadTo(817f, 760f, 800f, 760f)
                        lineTo(560f, 760f)
                        quadTo(543f, 760f, 531.5f, 748.5f)
                        quadTo(520f, 737f, 520f, 720f)
                        close()
                        moveTo(120f, 400f)
                        lineTo(120f, 240f)
                        quadTo(120f, 223f, 131.5f, 211.5f)
                        quadTo(143f, 200f, 160f, 200f)
                        lineTo(800f, 200f)
                        quadTo(817f, 200f, 828.5f, 211.5f)
                        quadTo(840f, 223f, 840f, 240f)
                        lineTo(840f, 400f)
                        quadTo(840f, 417f, 828.5f, 428.5f)
                        quadTo(817f, 440f, 800f, 440f)
                        lineTo(160f, 440f)
                        quadTo(143f, 440f, 131.5f, 428.5f)
                        quadTo(120f, 417f, 120f, 400f)
                        close()
                    }
                }
                .build()
        return _TileMedium!!
    }

private var _TileMedium: ImageVector? = null
