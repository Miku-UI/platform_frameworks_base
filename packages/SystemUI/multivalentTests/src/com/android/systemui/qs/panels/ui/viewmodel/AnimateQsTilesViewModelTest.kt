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

package com.android.systemui.qs.panels.ui.viewmodel

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.flags.EnableSceneContainer
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.lifecycle.activateIn
import com.android.systemui.media.controls.shared.model.MediaData
import com.android.systemui.media.remedia.data.repository.mediaPipelineRepository
import com.android.systemui.qs.composefragment.dagger.usingMediaInComposeFragment
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SmallTest
@EnableSceneContainer
class AnimateQsTilesViewModelTest : SysuiTestCase() {

    private val kosmos =
        testKosmos().useUnconfinedTestDispatcher().apply { usingMediaInComposeFragment = true }

    private val Kosmos.underTest by
        Kosmos.Fixture { animateQsTilesViewModelFactory.create().apply { activateIn(testScope) } }

    @Test
    fun inFirstPage_noMedia_animate() =
        kosmos.runTest {
            inFirstPageViewModel.inFirstPage = true
            setMediaState(MediaState.NO_MEDIA)

            assertThat(underTest.animateQsTiles).isTrue()
        }

    @Test
    fun notInFirstPage_noMedia_noAnimate() =
        kosmos.runTest {
            inFirstPageViewModel.inFirstPage = false
            setMediaState(MediaState.NO_MEDIA)

            assertThat(underTest.animateQsTiles).isFalse()
        }

    @Test
    fun inFirstPage_activeMediaInLandscape_animate() =
        kosmos.runTest {
            inFirstPageViewModel.inFirstPage = true
            setConfigurationForMediaInRow(true)
            setMediaState(MediaState.ACTIVE_MEDIA)

            assertThat(underTest.animateQsTiles).isTrue()
        }

    @Test
    fun inFirstPage_mediaNotActive_inLandscape_noAnimate() =
        kosmos.runTest {
            inFirstPageViewModel.inFirstPage = true
            setConfigurationForMediaInRow(true)
            setMediaState(MediaState.NOT_ACTIVE_MEDIA)

            assertThat(underTest.animateQsTiles).isFalse()
        }

    private fun Kosmos.setMediaState(state: MediaState) {
        if (state != MediaState.NO_MEDIA) {
            mediaPipelineRepository.addCurrentUserMediaEntry(
                MediaData(active = state == MediaState.ACTIVE_MEDIA)
            )
        } else {
            mediaPipelineRepository.clearCurrentUserMedia()
        }
    }
}

private enum class MediaState {
    ACTIVE_MEDIA,
    NOT_ACTIVE_MEDIA,
    NO_MEDIA,
}
