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

package com.android.systemui.shared.recents.utilities

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import androidx.core.graphics.values
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.shared.recents.model.ThumbnailData
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@SmallTest
class PreviewPositionHelperTest : SysuiTestCase() {

    private val systemUnderTest = PreviewPositionHelper()

    @Test
    fun unequalDisplayAndBitmapDensity_contributesToScale() {
        systemUnderTest.updateThumbnailMatrix(
            Rect(0, 0, THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT),
            getThumbnailData(bitmapDensityDpi = THUMBNAIL_DENSITY / 2),
            /* canvasWidth = */ CANVAS_WIDTH,
            /* canvasHeight = */ CANVAS_HEIGHT,
            /* isLargeScreen = */ false,
            /* currentRotation = */ 0,
            /* isRtl = */ false,
            /* deviceDensityDpi = */ THUMBNAIL_DENSITY,
        )

        assertThat(systemUnderTest.matrix.values()[Matrix.MSCALE_X]).isEqualTo(0.5f)
        assertThat(systemUnderTest.matrix.values()[Matrix.MSCALE_Y]).isEqualTo(0.5f)
    }

    @Test
    fun equalDisplayAndBitmapDensity_scaleIsOne() {
        systemUnderTest.updateThumbnailMatrix(
            Rect(0, 0, THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT),
            getThumbnailData(bitmapDensityDpi = THUMBNAIL_DENSITY),
            /* canvasWidth = */ CANVAS_WIDTH,
            /* canvasHeight = */ CANVAS_HEIGHT,
            /* isLargeScreen = */ false,
            /* currentRotation = */ 0,
            /* isRtl = */ false,
            /* deviceDensityDpi = */ THUMBNAIL_DENSITY,
        )

        assertThat(systemUnderTest.matrix.values()[Matrix.MSCALE_X]).isEqualTo(1f)
        assertThat(systemUnderTest.matrix.values()[Matrix.MSCALE_Y]).isEqualTo(1f)
    }

    @Test
    fun nullBitmap_scaleIsOne() {
        systemUnderTest.updateThumbnailMatrix(
            Rect(0, 0, THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT),
            getThumbnailData().copy(thumbnail = null),
            /* canvasWidth = */ CANVAS_WIDTH,
            /* canvasHeight = */ CANVAS_HEIGHT,
            /* isLargeScreen = */ false,
            /* currentRotation = */ 0,
            /* isRtl = */ false,
            /* deviceDensityDpi = */ THUMBNAIL_DENSITY,
        )

        assertThat(systemUnderTest.matrix.values()[Matrix.MSCALE_X]).isEqualTo(1f)
        assertThat(systemUnderTest.matrix.values()[Matrix.MSCALE_Y]).isEqualTo(1f)
    }

    private fun getThumbnailData(
        bitmapWidth: Int = THUMBNAIL_WIDTH,
        bitmapHeight: Int = THUMBNAIL_HEIGHT,
        bitmapDensityDpi: Int = THUMBNAIL_DENSITY,
    ) =
        ThumbnailData(
            thumbnail =
                mock<Bitmap>().apply {
                    whenever(width).thenReturn(bitmapWidth)
                    whenever(height).thenReturn(bitmapHeight)
                    whenever(density).thenReturn(bitmapDensityDpi)
                }
        )

    companion object {
        private const val CANVAS_WIDTH = 100
        private const val CANVAS_HEIGHT = 200
        private const val THUMBNAIL_WIDTH = CANVAS_WIDTH
        private const val THUMBNAIL_HEIGHT = CANVAS_HEIGHT
        private const val THUMBNAIL_DENSITY = 320
    }
}
