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

package com.android.systemui.statusbar.featurepods.popups.ui.viewmodel

import android.platform.test.annotations.EnableFlags
import androidx.compose.runtime.snapshots.Snapshot
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.lifecycle.activateIn
import com.android.systemui.media.controls.shared.model.MediaData
import com.android.systemui.media.remedia.data.repository.mediaPipelineRepository
import com.android.systemui.statusbar.featurepods.popups.StatusBarPopupChips
import com.android.systemui.statusbar.featurepods.popups.ui.model.PopupChipId
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@EnableFlags(StatusBarPopupChips.FLAG_NAME)
@RunWith(AndroidJUnit4::class)
class StatusBarPopupChipsViewModelTest : SysuiTestCase() {
    private val kosmos = testKosmos().useUnconfinedTestDispatcher()
    private val underTest = kosmos.statusBarPopupChipsViewModelFactory.create()

    @Before
    fun setUp() {
        underTest.activateIn(kosmos.testScope)
    }

    @Test
    fun shownPopupChips_allHidden_empty() =
        kosmos.runTest {
            val shownPopupChips = underTest.shownPopupChips
            assertThat(shownPopupChips).isEmpty()
        }

    @Test
    fun shownPopupChips_activeMedia_restHidden_mediaControlChipShown() =
        kosmos.runTest {
            val shownPopupChips = underTest.shownPopupChips
            val userMedia = MediaData(active = true, song = "test")

            mediaPipelineRepository.addCurrentUserMediaEntry(userMedia)

            Snapshot.takeSnapshot {
                assertThat(shownPopupChips).hasSize(1)
                assertThat(shownPopupChips.first().chipId).isEqualTo(PopupChipId.MediaControl)
            }
        }

    @Test
    fun shownPopupChips_mediaChipToggled_popupShown() =
        kosmos.runTest {
            val shownPopupChips = underTest.shownPopupChips

            val userMedia = MediaData(active = true, song = "test")

            mediaPipelineRepository.addCurrentUserMediaEntry(userMedia)

            Snapshot.takeSnapshot {
                assertThat(shownPopupChips).hasSize(1)
                val mediaChip = shownPopupChips.first()
                assertThat(mediaChip.isPopupShown).isFalse()

                mediaChip.showPopup.invoke()
                assertThat(shownPopupChips.first().isPopupShown).isTrue()
            }
        }

    // TODO(b/444459963) Add a test for the share screen privacy indicator.
}
