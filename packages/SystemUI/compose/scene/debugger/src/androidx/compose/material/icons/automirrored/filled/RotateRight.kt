/*
 * Copyright 2024 The Android Open Source Project
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

package androidx.compose.material.icons.automirrored.filled

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.graphics.vector.ImageVector

public val Icons.AutoMirrored.Filled.RotateRight: ImageVector
    get() {
        if (_rotateRight != null) {
            return _rotateRight!!
        }
        _rotateRight =
            materialIcon(name = "AutoMirrored.Filled.RotateRight", autoMirror = true) {
                materialPath {
                    moveTo(15.55f, 5.55f)
                    lineTo(11.0f, 1.0f)
                    verticalLineToRelative(3.07f)
                    curveTo(7.06f, 4.56f, 4.0f, 7.92f, 4.0f, 12.0f)
                    reflectiveCurveToRelative(3.05f, 7.44f, 7.0f, 7.93f)
                    verticalLineToRelative(-2.02f)
                    curveToRelative(-2.84f, -0.48f, -5.0f, -2.94f, -5.0f, -5.91f)
                    reflectiveCurveToRelative(2.16f, -5.43f, 5.0f, -5.91f)
                    lineTo(11.0f, 10.0f)
                    lineToRelative(4.55f, -4.45f)
                    close()
                    moveTo(19.93f, 11.0f)
                    curveToRelative(-0.17f, -1.39f, -0.72f, -2.73f, -1.62f, -3.89f)
                    lineToRelative(-1.42f, 1.42f)
                    curveToRelative(0.54f, 0.75f, 0.88f, 1.6f, 1.02f, 2.47f)
                    horizontalLineToRelative(2.02f)
                    close()
                    moveTo(13.0f, 17.9f)
                    verticalLineToRelative(2.02f)
                    curveToRelative(1.39f, -0.17f, 2.74f, -0.71f, 3.9f, -1.61f)
                    lineToRelative(-1.44f, -1.44f)
                    curveToRelative(-0.75f, 0.54f, -1.59f, 0.89f, -2.46f, 1.03f)
                    close()
                    moveTo(16.89f, 15.48f)
                    lineToRelative(1.42f, 1.41f)
                    curveToRelative(0.9f, -1.16f, 1.45f, -2.5f, 1.62f, -3.89f)
                    horizontalLineToRelative(-2.02f)
                    curveToRelative(-0.14f, 0.87f, -0.48f, 1.72f, -1.02f, 2.48f)
                    close()
                }
            }
        return _rotateRight!!
    }

private var _rotateRight: ImageVector? = null
