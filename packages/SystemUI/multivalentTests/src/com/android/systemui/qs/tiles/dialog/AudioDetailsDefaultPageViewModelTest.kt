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

package com.android.systemui.qs.tiles.dialog

import android.testing.TestableLooper.RunWithLooper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.lifecycle.activateIn
import com.android.systemui.res.R
import com.android.systemui.testKosmosNew
import com.android.systemui.volume.dialog.data.repository.volumeDialogStateRepository
import com.android.systemui.volume.panel.shared.model.VolumePanelComponentKey
import com.android.systemui.volume.panel.shared.model.mockVolumePanelUiComponentProvider
import com.android.systemui.volume.panel.ui.composable.componentByKey
import com.android.systemui.volume.panel.ui.layout.DefaultComponentsLayoutManager
import com.android.systemui.volume.panel.ui.layout.componentsLayoutManager
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWithLooper(setAsMainLooper = true)
@RunWith(AndroidJUnit4::class)
class AudioDetailsDefaultPageViewModelTest : SysuiTestCase() {
    private val kosmos = testKosmosNew()
    private val Kosmos.underTest by Kosmos.Fixture { audioDetailsDefaultPageViewModel }

    @Test
    fun footerComponents() =
        kosmos.runTest {
            componentsLayoutManager =
                DefaultComponentsLayoutManager(
                    bottomBar = BOTTOM_BAR,
                    footerComponents = listOf(COMPONENT_1, COMPONENT_2),
                )
            componentByKey =
                mapOf(
                    COMPONENT_1 to mockVolumePanelUiComponentProvider,
                    COMPONENT_2 to mockVolumePanelUiComponentProvider,
                    BOTTOM_BAR to mockVolumePanelUiComponentProvider,
                )

            underTest.activateIn(kosmos.testScope)

            assertThat(underTest.footerComponents).hasSize(2)
            assertThat(checkNotNull(underTest.footerComponents)[0].key).isEqualTo(COMPONENT_1)
            assertThat(checkNotNull(underTest.footerComponents)[1].key).isEqualTo(COMPONENT_2)
        }

    @Test
    fun volumeSliderViewModel_usesStreamMusic() =
        kosmos.runTest {
            underTest.activateIn(kosmos.testScope)
            val slider by collectLastValue(checkNotNull(underTest.volumeSliderViewModel).slider)

            // Slider should be associated with `AudioStream(AudioManager.STREAM_MUSIC)`.
            assertThat(checkNotNull(slider).label)
                .isEqualTo(context.getString(R.string.stream_music))
        }

    @Test
    fun a11yVolumeSliderViewModel_isAvailable_whenA11ySliderIsShown() =
        kosmos.runTest {
            underTest.activateIn(kosmos.testScope)

            assertThat(underTest.a11yVolumeSliderViewModel.value).isNull()

            // Show a11y slider
            volumeDialogStateRepository.updateState { it.copy(shouldShowA11ySlider = true) }

            val a11yViewModel = underTest.a11yVolumeSliderViewModel.value
            assertThat(a11yViewModel).isNotNull()
            val slider by collectLastValue(checkNotNull(a11yViewModel).slider)
            assertThat(checkNotNull(slider).label)
                .isEqualTo(context.getString(R.string.stream_accessibility))

            // Hide a11y slider
            volumeDialogStateRepository.updateState { it.copy(shouldShowA11ySlider = false) }

            assertThat(underTest.a11yVolumeSliderViewModel.value).isNull()
        }

    private companion object {
        const val BOTTOM_BAR: VolumePanelComponentKey = "test_bottom_bar"
        const val COMPONENT_1: VolumePanelComponentKey = "test_component:1"
        const val COMPONENT_2: VolumePanelComponentKey = "test_component:2"
    }
}
