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

val PlayArrow: ImageVector
    get() {
        if (_PlayArrow != null) return _PlayArrow!!

        _PlayArrow =
            ImageVector.Builder(
                    name = "PlayArrow",
                    defaultWidth = 24.dp,
                    defaultHeight = 24.dp,
                    viewportWidth = 960f,
                    viewportHeight = 960f,
                )
                .apply {
                    path(fill = SolidColor(Color(0xFFe3e3e3))) {
                        moveTo(300.78f, 697.74f)
                        verticalLineToRelative(-435.48f)
                        quadToRelative(0f, -23.22f, 15.96f, -38.11f)
                        reflectiveQuadToRelative(37.04f, -14.89f)
                        quadToRelative(6.7f, 0f, 14.18f, 1.78f)
                        quadToRelative(7.47f, 1.78f, 14.17f, 5.92f)
                        lineToRelative(342.96f, 218.3f)
                        quadToRelative(11.82f, 7.7f, 18.02f, 19.8f)
                        quadToRelative(6.2f, 12.11f, 6.2f, 24.94f)
                        quadToRelative(0f, 12.83f, -6.2f, 24.94f)
                        quadToRelative(-6.2f, 12.1f, -18.02f, 19.8f)
                        lineToRelative(-342.96f, 218.3f)
                        quadToRelative(-6.7f, 4.14f, -14.17f, 5.92f)
                        quadToRelative(-7.48f, 1.78f, -14.18f, 1.78f)
                        quadToRelative(-21.08f, 0f, -37.04f, -14.89f)
                        reflectiveQuadToRelative(-15.96f, -38.11f)
                        close()
                    }
                }
                .build()

        return _PlayArrow!!
    }

private var _PlayArrow: ImageVector? = null
