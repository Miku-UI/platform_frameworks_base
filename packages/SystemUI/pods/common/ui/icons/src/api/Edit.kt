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
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Edit: ImageVector
    get() {
        if (_Edit != null) {
            return _Edit!!
        }
        _Edit =
            ImageVector.Builder(
                    name = "Edit",
                    defaultWidth = 24.dp,
                    defaultHeight = 24.dp,
                    viewportWidth = 960f,
                    viewportHeight = 960f,
                )
                .apply {
                    path(
                        fill = SolidColor(Color(0xFFE8EAED)),
                        fillAlpha = 1.0f,
                        stroke = null,
                        strokeAlpha = 1.0f,
                        strokeLineWidth = 1.0f,
                        strokeLineCap = StrokeCap.Butt,
                        strokeLineJoin = StrokeJoin.Miter,
                        strokeLineMiter = 1.0f,
                        pathFillType = PathFillType.NonZero,
                    ) {
                        moveTo(160f, 840f)
                        quadToRelative(-17f, 0f, -28.5f, -11.5f)
                        reflectiveQuadTo(120f, 800f)
                        verticalLineToRelative(-97f)
                        quadToRelative(0f, -16f, 6f, -30.5f)
                        reflectiveQuadToRelative(17f, -25.5f)
                        lineToRelative(505f, -504f)
                        quadToRelative(12f, -11f, 26.5f, -17f)
                        reflectiveQuadToRelative(30.5f, -6f)
                        quadToRelative(16f, 0f, 31f, 6f)
                        reflectiveQuadToRelative(26f, 18f)
                        lineToRelative(55f, 56f)
                        quadToRelative(12f, 11f, 17.5f, 26f)
                        reflectiveQuadToRelative(5.5f, 30f)
                        quadToRelative(0f, 16f, -5.5f, 30.5f)
                        reflectiveQuadTo(817f, 313f)
                        lineTo(313f, 817f)
                        quadToRelative(-11f, 11f, -25.5f, 17f)
                        reflectiveQuadToRelative(-30.5f, 6f)
                        horizontalLineToRelative(-97f)
                        close()
                        moveToRelative(544f, -528f)
                        lineToRelative(56f, -56f)
                        lineToRelative(-56f, -56f)
                        lineToRelative(-56f, 56f)
                        lineToRelative(56f, 56f)
                        close()
                    }
                }
                .build()
        return _Edit!!
    }

private var _Edit: ImageVector? = null
