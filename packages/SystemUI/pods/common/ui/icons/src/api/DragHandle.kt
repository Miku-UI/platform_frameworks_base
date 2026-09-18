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

package com.android.systemui.common.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val DragHandle: ImageVector
    get() {
        if (_DragHandle != null) return _DragHandle!!

        _DragHandle =
            ImageVector.Builder(
                    name = "DragHandle",
                    defaultWidth = 24.dp,
                    defaultHeight = 24.dp,
                    viewportWidth = 960f,
                    viewportHeight = 960f,
                )
                .apply {
                    path(fill = SolidColor(Color(0xFF000000))) {
                        moveTo(197.37f, 611f)
                        quadTo(178.22f, 611f, 165.04f, 597.83f)
                        quadTo(151.87f, 584.65f, 151.87f, 565.5f)
                        quadTo(151.87f, 546.35f, 165.04f, 533.17f)
                        quadTo(178.22f, 520f, 197.37f, 520f)
                        lineTo(762.63f, 520f)
                        quadTo(781.78f, 520f, 794.96f, 533.17f)
                        quadTo(808.13f, 546.35f, 808.13f, 565.5f)
                        quadTo(808.13f, 584.65f, 794.96f, 597.83f)
                        quadTo(781.78f, 611f, 762.63f, 611f)
                        lineTo(197.37f, 611f)
                        close()
                        moveTo(197.37f, 440f)
                        quadTo(178.22f, 440f, 165.04f, 426.83f)
                        quadTo(151.87f, 413.65f, 151.87f, 394.5f)
                        quadTo(151.87f, 375.35f, 165.04f, 362.17f)
                        quadTo(178.22f, 349f, 197.37f, 349f)
                        lineTo(762.63f, 349f)
                        quadTo(781.78f, 349f, 794.96f, 362.17f)
                        quadTo(808.13f, 375.35f, 808.13f, 394.5f)
                        quadTo(808.13f, 413.65f, 794.96f, 426.83f)
                        quadTo(781.78f, 440f, 762.63f, 440f)
                        lineTo(197.37f, 440f)
                        close()
                    }
                }
                .build()

        return _DragHandle!!
    }

private var _DragHandle: ImageVector? = null
