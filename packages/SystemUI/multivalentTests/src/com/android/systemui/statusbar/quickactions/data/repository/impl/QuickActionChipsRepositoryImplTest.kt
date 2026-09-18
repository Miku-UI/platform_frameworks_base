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

package com.android.systemui.statusbar.quickactions.data.repository.impl

import android.graphics.RectF
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.runTest
import com.android.systemui.statusbar.quickactions.data.repository.realQuickActionChipsRepository
import com.android.systemui.statusbar.quickactions.media.ui.viewmodel.mediaControlPopupViewModelFactory
import com.android.systemui.statusbar.quickactions.shared.model.QuickActionChipId
import com.android.systemui.statusbar.quickactions.shared.model.QuickActionPanelModel
import com.android.systemui.testKosmosNew
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class QuickActionChipsRepositoryImplTest : SysuiTestCase() {
    private val kosmos = testKosmosNew()

    private val Kosmos.underTest by Kosmos.Fixture { kosmos.realQuickActionChipsRepository }

    private val panelModel =
        QuickActionPanelModel(
            chipId = QuickActionChipId.MediaControl,
            anchorBounds = RectF(),
            panelContentViewModelFactory = kosmos.mediaControlPopupViewModelFactory,
        )

    @Test
    fun initialState_activePanelIsNull() =
        kosmos.runTest { assertThat(underTest.activePanel.value).isNull() }

    @Test
    fun setActivePanel_updatesState() =
        kosmos.runTest {
            underTest.setActivePanel(panelModel)

            assertThat(underTest.activePanel.value).isEqualTo(panelModel)
        }

    @Test
    fun setActivePanel_toNull_clearsState() =
        kosmos.runTest {
            underTest.setActivePanel(panelModel)
            underTest.setActivePanel(null)

            assertThat(underTest.activePanel.value).isNull()
        }
}
