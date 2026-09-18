/*
 * Copyright (C) 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.statusbar.quickactions.ui.viewmodel

import android.graphics.Rect
import android.graphics.RectF
import androidx.compose.ui.geometry.Rect as ComposeRect
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.runTest
import com.android.systemui.shade.domain.interactor.shadeInteractor
import com.android.systemui.statusbar.quickactions.av.ui.viewmodel.avControlsPopupViewModelFactory
import com.android.systemui.statusbar.quickactions.domain.interactor.quickActionsInteractor
import com.android.systemui.statusbar.quickactions.shared.model.QuickActionChipId
import com.android.systemui.statusbar.quickactions.shared.model.QuickActionPanelModel
import com.android.systemui.testKosmosNew
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class QuickActionOverlayViewModelTest : SysuiTestCase() {
    private val kosmos = testKosmosNew()

    private val Kosmos.underTest by Kosmos.Fixture { quickActionOverlayViewModelFactory.create() }

    private val panel =
        QuickActionPanelModel(
            chipId = QuickActionChipId.AvControlsIndicator,
            anchorBounds = RectF(),
            panelContentViewModelFactory = kosmos.avControlsPopupViewModelFactory,
        )

    @Test
    fun activePanel_returnsInteractorActivePanel() =
        kosmos.runTest {
            quickActionsInteractor.toggle(panel)

            assertThat(underTest.activePanel).isEqualTo(panel)
        }

    @Test
    fun onShadeOverlayBoundsChanged_withValidBounds_updatesShadeInteractor() =
        kosmos.runTest {
            var shadeBounds: Rect? = null
            shadeInteractor.addShadeOverlayBoundsListener { shadeBounds = it }
            assertThat(shadeBounds).isNull()

            val composeRect = ComposeRect(left = 10.1f, top = 20.2f, right = 30.8f, bottom = 40.9f)
            underTest.onShadeOverlayBoundsChanged(composeRect)

            val expectedAndroidRect = Rect(10, 20, 31, 41)
            assertThat(shadeBounds).isEqualTo(expectedAndroidRect)
        }

    @Test
    fun onShadeOverlayBoundsChanged_withNullBounds_clearsShadeInteractorBounds() =
        kosmos.runTest {
            var shadeBounds: Rect? = Rect(0, 0, 10, 10)
            shadeInteractor.addShadeOverlayBoundsListener { shadeBounds = it }

            underTest.onShadeOverlayBoundsChanged(null)

            assertThat(shadeBounds).isNull()
        }

    @Test
    fun close_callsInteractorClose() =
        kosmos.runTest {
            quickActionsInteractor.toggle(panel)
            assertThat(underTest.activePanel).isEqualTo(panel)

            underTest.close()

            assertThat(underTest.activePanel).isNull()
        }
}
