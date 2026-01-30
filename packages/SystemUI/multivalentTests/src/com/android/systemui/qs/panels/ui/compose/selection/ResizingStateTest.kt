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

package com.android.systemui.qs.panels.ui.compose.selection

import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.qs.pipeline.shared.TileSpec
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class ResizingStateTest : SysuiTestCase() {

    private val underTest =
        ResizingState(TileSpec.create("a"), startsAsIcon = true).apply {
            anchoredDraggableState.updateAnchors(
                DraggableAnchors {
                    QSDragAnchor.Icon at 10f
                    QSDragAnchor.Large at 20f
                }
            )
        }

    @Test
    fun newResizingState_setInitialValueCorrectly() {
        assertThat(underTest.anchoredDraggableState.currentValue).isEqualTo(QSDragAnchor.Icon)
    }

    @Test
    fun updateAnchors_onIconTile_setBoundsCorrectly() {
        // With an icon tile 10px wide, and a max span of 2 with a tile padding of 5
        underTest.updateAnchors(isIcon = true, 10, maxSpan = 2, padding = 5)

        // New bounds should be: 10 to 25
        // max = 10 * 2 + 5 * (2 - 1)
        assertThat(underTest.bounds).isEqualTo(10f to 25f)

        // With an icon tile 5px wide, and a max span of 10 with a tile padding of 15
        underTest.updateAnchors(isIcon = true, 5, maxSpan = 10, padding = 15)

        // New bounds should be: 5 to 185
        // max = 5 * 10 + 15 * (10 - 1)
        assertThat(underTest.bounds).isEqualTo(5f to 185f)
    }

    @Test
    fun updateAnchors_onLargeTile_setBoundsCorrectly() {
        // With a large tile 60px wide, and a max span of 2 with a tile padding of 20
        underTest.updateAnchors(isIcon = false, 60, maxSpan = 2, padding = 20)

        // New bounds should be: 20 to 60
        // min = (60 - (20 * (2 - 1))) / 2
        assertThat(underTest.bounds).isEqualTo(20f to 60f)

        // With a large tile 35px wide, and a max span of 5 with a tile padding of 2
        underTest.updateAnchors(isIcon = false, 36, maxSpan = 6, padding = 2)

        // New bounds should be: 4 to 36
        // min = (46 - (2 * (6 - 1))) / 6
        assertThat(underTest.bounds).isEqualTo(4f to 36f)
    }

    @Test
    fun dragOverThreshold_resizesToLarge() = runTest {
        underTest.anchoredDraggableState.anchoredDrag { dragTo(16f) }

        assertThat(underTest.temporaryResizeOperation.spec).isEqualTo(TileSpec.create("a"))
        assertThat(underTest.temporaryResizeOperation.toIcon).isFalse()
    }

    @Test
    fun dragUnderThreshold_staysIcon() = runTest {
        underTest.anchoredDraggableState.anchoredDrag { dragTo(12f) }

        assertThat(underTest.temporaryResizeOperation.spec).isEqualTo(TileSpec.create("a"))
        assertThat(underTest.temporaryResizeOperation.toIcon).isTrue()
    }
}
