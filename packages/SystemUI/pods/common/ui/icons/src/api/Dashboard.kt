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

val Dashboard: ImageVector
    get() {
        if (_Dashboard != null) return _Dashboard!!

        _Dashboard =
            ImageVector.Builder(
                    name = "Dashboard",
                    defaultWidth = 24.dp,
                    defaultHeight = 24.dp,
                    viewportWidth = 960f,
                    viewportHeight = 960f,
                )
                .apply {
                    path(fill = SolidColor(Color(0xFF000000))) {
                        moveTo(563.59f, 356.65f)
                        quadTo(544.43f, 356.65f, 531.26f, 343.48f)
                        quadTo(518.09f, 330.3f, 518.09f, 311.15f)
                        lineTo(518.09f, 151.15f)
                        quadTo(518.09f, 132f, 531.26f, 118.83f)
                        quadTo(544.43f, 105.65f, 563.59f, 105.65f)
                        lineTo(808.85f, 105.65f)
                        quadTo(828f, 105.65f, 841.17f, 118.83f)
                        quadTo(854.35f, 132f, 854.35f, 151.15f)
                        lineTo(854.35f, 311.15f)
                        quadTo(854.35f, 330.3f, 841.17f, 343.48f)
                        quadTo(828f, 356.65f, 808.85f, 356.65f)
                        lineTo(563.59f, 356.65f)
                        close()
                        moveTo(151.15f, 526.22f)
                        quadTo(132f, 526.22f, 118.83f, 513.04f)
                        quadTo(105.65f, 499.87f, 105.65f, 480.72f)
                        lineTo(105.65f, 151.15f)
                        quadTo(105.65f, 132f, 118.83f, 118.83f)
                        quadTo(132f, 105.65f, 151.15f, 105.65f)
                        lineTo(396.41f, 105.65f)
                        quadTo(415.57f, 105.65f, 428.74f, 118.83f)
                        quadTo(441.91f, 132f, 441.91f, 151.15f)
                        lineTo(441.91f, 480.72f)
                        quadTo(441.91f, 499.87f, 428.74f, 513.04f)
                        quadTo(415.57f, 526.22f, 396.41f, 526.22f)
                        lineTo(151.15f, 526.22f)
                        close()
                        moveTo(563.59f, 853.63f)
                        quadTo(544.43f, 853.63f, 531.26f, 840.46f)
                        quadTo(518.09f, 827.28f, 518.09f, 808.13f)
                        lineTo(518.09f, 478.57f)
                        quadTo(518.09f, 459.41f, 531.26f, 446.24f)
                        quadTo(544.43f, 433.07f, 563.59f, 433.07f)
                        lineTo(808.85f, 433.07f)
                        quadTo(828f, 433.07f, 841.17f, 446.24f)
                        quadTo(854.35f, 459.41f, 854.35f, 478.57f)
                        lineTo(854.35f, 808.13f)
                        quadTo(854.35f, 827.28f, 841.17f, 840.46f)
                        quadTo(828f, 853.63f, 808.85f, 853.63f)
                        lineTo(563.59f, 853.63f)
                        close()
                        moveTo(151.15f, 853.63f)
                        quadTo(132f, 853.63f, 118.83f, 840.46f)
                        quadTo(105.65f, 827.28f, 105.65f, 808.13f)
                        lineTo(105.65f, 648.13f)
                        quadTo(105.65f, 628.98f, 118.83f, 615.8f)
                        quadTo(132f, 602.63f, 151.15f, 602.63f)
                        lineTo(396.41f, 602.63f)
                        quadTo(415.57f, 602.63f, 428.74f, 615.8f)
                        quadTo(441.91f, 628.98f, 441.91f, 648.13f)
                        lineTo(441.91f, 808.13f)
                        quadTo(441.91f, 827.28f, 428.74f, 840.46f)
                        quadTo(415.57f, 853.63f, 396.41f, 853.63f)
                        lineTo(151.15f, 853.63f)
                        close()
                    }
                }
                .build()

        return _Dashboard!!
    }

private var _Dashboard: ImageVector? = null
