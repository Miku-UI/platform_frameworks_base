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

val Undo: ImageVector
    get() {
        if (_Undo != null) {
            return _Undo!!
        }
        _Undo =
            ImageVector.Builder(
                    name = "Undo",
                    defaultWidth = 24.dp,
                    defaultHeight = 24.dp,
                    viewportWidth = 960f,
                    viewportHeight = 960f,
                    autoMirror = true,
                )
                .apply {
                    path(fill = SolidColor(Color.Black)) {
                        moveTo(320f, 760f)
                        quadTo(303f, 760f, 291.5f, 748.5f)
                        quadTo(280f, 737f, 280f, 720f)
                        quadTo(280f, 703f, 291.5f, 691.5f)
                        quadTo(303f, 680f, 320f, 680f)
                        lineTo(564f, 680f)
                        quadTo(627f, 680f, 673.5f, 640f)
                        quadTo(720f, 600f, 720f, 540f)
                        quadTo(720f, 480f, 673.5f, 440f)
                        quadTo(627f, 400f, 564f, 400f)
                        lineTo(312f, 400f)
                        lineTo(388f, 476f)
                        quadTo(399f, 487f, 399f, 504f)
                        quadTo(399f, 521f, 388f, 532f)
                        quadTo(377f, 543f, 360f, 543f)
                        quadTo(343f, 543f, 332f, 532f)
                        lineTo(188f, 388f)
                        quadTo(182f, 382f, 179.5f, 375f)
                        quadTo(177f, 368f, 177f, 360f)
                        quadTo(177f, 352f, 179.5f, 345f)
                        quadTo(182f, 338f, 188f, 332f)
                        lineTo(332f, 188f)
                        quadTo(343f, 177f, 360f, 177f)
                        quadTo(377f, 177f, 388f, 188f)
                        quadTo(399f, 199f, 399f, 216f)
                        quadTo(399f, 233f, 388f, 244f)
                        lineTo(312f, 320f)
                        lineTo(564f, 320f)
                        quadTo(661f, 320f, 730.5f, 383f)
                        quadTo(800f, 446f, 800f, 540f)
                        quadTo(800f, 634f, 730.5f, 697f)
                        quadTo(661f, 760f, 564f, 760f)
                        lineTo(320f, 760f)
                        close()
                    }
                }
                .build()
        return _Undo!!
    }

private var _Undo: ImageVector? = null
