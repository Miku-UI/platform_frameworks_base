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

package com.android.systemui.statusbar.quickactions.domain.interactor

import android.graphics.RectF
import android.platform.test.annotations.EnableFlags
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.runTest
import com.android.systemui.scene.domain.interactor.sceneInteractor
import com.android.systemui.scene.shared.model.Overlays
import com.android.systemui.statusbar.quickactions.av.ui.viewmodel.avControlsPopupViewModelFactory
import com.android.systemui.statusbar.quickactions.media.ui.viewmodel.mediaControlPopupViewModelFactory
import com.android.systemui.statusbar.quickactions.popups.StatusBarPopupChips
import com.android.systemui.statusbar.quickactions.shared.model.QuickActionChipId
import com.android.systemui.statusbar.quickactions.shared.model.QuickActionPanelModel
import com.android.systemui.testKosmosNew
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@EnableFlags(StatusBarPopupChips.FLAG_NAME)
@RunWith(AndroidJUnit4::class)
class QuickActionsInteractorTest : SysuiTestCase() {
    private val kosmos = testKosmosNew()
    private val underTest = kosmos.quickActionsInteractor

    private val panel1 =
        QuickActionPanelModel(
            chipId = QuickActionChipId.MediaControl,
            anchorBounds = RectF(),
            panelContentViewModelFactory = kosmos.mediaControlPopupViewModelFactory,
        )

    private val panel2 =
        QuickActionPanelModel(
            chipId = QuickActionChipId.AvControlsIndicator,
            anchorBounds = RectF(),
            panelContentViewModelFactory = kosmos.avControlsPopupViewModelFactory,
        )

    private val panel3_differentDisplay =
        QuickActionPanelModel(
            chipId = QuickActionChipId.MediaControl,
            anchorBounds = RectF(),
            panelContentViewModelFactory = kosmos.mediaControlPopupViewModelFactory,
        )

    @Test
    fun toggle_whenNoActivePanel_activatesPanelAndShowsOverlay() =
        kosmos.runTest {
            underTest.toggle(panel1)

            assertThat(underTest.activePanel).isEqualTo(panel1)
            assertThat(underTest.activePanel!!.chipId).isEqualTo(QuickActionChipId.MediaControl)
            assertThat(sceneInteractor.transitionState.currentOverlays)
                .contains(Overlays.QuickActions)
        }

    @Test
    fun toggle_whenSamePanelActive_deactivatesPanelAndHidesOverlay() =
        kosmos.runTest {
            underTest.toggle(panel1)
            assertThat(sceneInteractor.transitionState.currentOverlays)
                .contains(Overlays.QuickActions)

            underTest.toggle(panel1)

            assertThat(underTest.activePanel).isNull()
            assertThat(sceneInteractor.transitionState.currentOverlays)
                .doesNotContain(Overlays.QuickActions)
        }

    @Test
    fun toggle_whenDifferentPanel_switchesPanelAndShowsOverlay() =
        kosmos.runTest {
            underTest.toggle(panel1)
            assertThat(underTest.activePanel).isEqualTo(panel1)

            underTest.toggle(panel2)

            assertThat(underTest.activePanel).isEqualTo(panel2)
            assertThat(sceneInteractor.transitionState.currentOverlays)
                .contains(Overlays.QuickActions)
        }

    @Test
    fun close_deactivatesPanelAndHidesOverlay() =
        kosmos.runTest {
            underTest.toggle(panel1)
            assertThat(sceneInteractor.transitionState.currentOverlays)
                .contains(Overlays.QuickActions)

            underTest.close()

            assertThat(underTest.activePanel).isNull()
            assertThat(sceneInteractor.transitionState.currentOverlays)
                .doesNotContain(Overlays.QuickActions)
        }

    @Test
    fun close_noActivePanelButOtherOverlayVisible_onlyHidesQuickActions() =
        kosmos.runTest {
            sceneInteractor.showOverlay(Overlays.Bouncer, "test")
            assertThat(sceneInteractor.transitionState.currentOverlays).contains(Overlays.Bouncer)

            underTest.close()

            assertThat(underTest.activePanel).isNull()
            assertThat(sceneInteractor.transitionState.currentOverlays).contains(Overlays.Bouncer)
            assertThat(sceneInteractor.transitionState.currentOverlays)
                .doesNotContain(Overlays.QuickActions)
        }
}
