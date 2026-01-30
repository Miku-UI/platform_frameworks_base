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

package com.android.systemui.statusbar.featurepods.av.domain.interactor

import android.platform.test.annotations.DisableFlags
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.Flags.FLAG_EXPANDED_PRIVACY_INDICATORS_ON_LARGE_SCREEN
import com.android.systemui.kosmos.runTest
import com.android.systemui.shade.data.repository.fakePrivacyChipRepository
import com.android.systemui.statusbar.data.repository.fakeStatusBarModeRepository
import com.google.common.truth.Truth.assertThat
import kotlin.test.Test
import kotlinx.coroutines.flow.first
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class NoOpAvControlsChipInteractorTest() : AvControlsChipInteractorTestBase() {

    @Test
    @DisableFlags(FLAG_EXPANDED_PRIVACY_INDICATORS_ON_LARGE_SCREEN)
    fun isCorrectSubclass() =
        kosmos.runTest {
            assertThat(underTest::class.java).isEqualTo(NoOpAvControlsChipInteractor::class.java)
        }

    @Test
    @DisableFlags(FLAG_EXPANDED_PRIVACY_INDICATORS_ON_LARGE_SCREEN)
    fun disabled() = kosmos.runTest { assertThat(underTest.isEnabled.value).isEqualTo(false) }

    @Test
    @DisableFlags(FLAG_EXPANDED_PRIVACY_INDICATORS_ON_LARGE_SCREEN)
    fun cameraActive_InactiveModel() =
        kosmos.runTest {
            fakePrivacyChipRepository.setPrivacyItems(listOf(cameraItem))
            assertThat(lastModel()).isEqualTo(inactiveModel())
        }

    @Test
    @DisableFlags(FLAG_EXPANDED_PRIVACY_INDICATORS_ON_LARGE_SCREEN)
    fun microphoneActive_InactiveModel() =
        kosmos.runTest {
            fakePrivacyChipRepository.setPrivacyItems(listOf(microphoneItem))
            assertThat(lastModel()).isEqualTo(inactiveModel())
        }

    @Test
    @DisableFlags(FLAG_EXPANDED_PRIVACY_INDICATORS_ON_LARGE_SCREEN)
    fun cameraAndMicrophoneActive_InactiveModel() =
        kosmos.runTest {
            fakePrivacyChipRepository.setPrivacyItems(listOf(cameraItem, microphoneItem))
            assertThat(lastModel()).isEqualTo(inactiveModel())
        }

    @Test
    @DisableFlags(FLAG_EXPANDED_PRIVACY_INDICATORS_ON_LARGE_SCREEN)
    fun cameraActive_noFullscreen_shouldNotSuppressDot() =
        kosmos.runTest {
            fakePrivacyChipRepository.setPrivacyItems(listOf(cameraItem))
            fakeStatusBarModeRepository.defaultDisplay.isInFullscreenMode.value = false
            assertThat(underTest.isShowingAvChip.first()).isEqualTo(false)
        }

    @Test
    @DisableFlags(FLAG_EXPANDED_PRIVACY_INDICATORS_ON_LARGE_SCREEN)
    fun cameraActive_fullscreen_shouldNotSuppressDot() =
        kosmos.runTest {
            fakePrivacyChipRepository.setPrivacyItems(listOf(cameraItem))
            fakeStatusBarModeRepository.defaultDisplay.isInFullscreenMode.value = true
            assertThat(underTest.isShowingAvChip.first()).isEqualTo(false)
        }
}
