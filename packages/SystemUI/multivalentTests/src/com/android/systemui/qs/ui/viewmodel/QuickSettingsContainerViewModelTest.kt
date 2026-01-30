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

package com.android.systemui.qs.ui.viewmodel

import android.platform.test.annotations.EnableFlags
import android.testing.TestableLooper
import android.view.Display
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.Flags.FLAG_SHADE_WINDOW_GOES_AROUND
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.lifecycle.activateIn
import com.android.systemui.media.controls.shared.model.MediaData
import com.android.systemui.media.remedia.data.repository.mediaPipelineRepository
import com.android.systemui.qs.composefragment.dagger.usingMediaInComposeFragment
import com.android.systemui.shade.data.repository.fakeShadeDisplaysRepository
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
@TestableLooper.RunWithLooper
class QuickSettingsContainerViewModelTest : SysuiTestCase() {

    private val kosmos =
        testKosmos().useUnconfinedTestDispatcher().apply {
            usingMediaInComposeFragment = false // This is not for the compose fragment
        }
    private val testScope = kosmos.testScope

    private val underTest by lazy {
        kosmos.quickSettingsContainerViewModelFactory.create(supportsBrightnessMirroring = false)
    }

    @Before
    fun setUp() {
        underTest.activateIn(testScope)
    }

    @Test
    fun isBrightnessSliderVisible_defaultDisplay_isVisible() =
        with(kosmos) {
            testScope.runTest {
                fakeShadeDisplaysRepository.setPendingDisplayId(Display.DEFAULT_DISPLAY)

                assertThat(underTest.isBrightnessSliderVisible).isTrue()
            }
        }

    @Test
    fun addAndRemoveMedia_mediaVisibilityIsUpdated() =
        kosmos.runTest {
            val userMedia = MediaData(active = true)

            assertThat(underTest.showMedia).isFalse()

            mediaPipelineRepository.addCurrentUserMediaEntry(userMedia)

            assertThat(underTest.showMedia).isTrue()

            mediaPipelineRepository.removeCurrentUserMediaEntry(userMedia.instanceId)

            assertThat(underTest.showMedia).isFalse()
        }

    @Test
    fun addInactiveMedia_mediaVisibilityIsUpdated() =
        kosmos.runTest {
            val userMedia = MediaData(active = false)

            assertThat(underTest.showMedia).isFalse()

            mediaPipelineRepository.addCurrentUserMediaEntry(userMedia)

            assertThat(underTest.showMedia).isTrue()
        }

    @Test
    @EnableFlags(FLAG_SHADE_WINDOW_GOES_AROUND)
    fun isBrightnessSliderVisible_externalDisplay_isInvisible() =
        with(kosmos) {
            testScope.runTest {
                fakeShadeDisplaysRepository.setPendingDisplayId(
                    Display.DEFAULT_DISPLAY + 1
                ) // Not default.

                assertThat(underTest.isBrightnessSliderVisible).isFalse()
            }
        }
}
