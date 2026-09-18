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

package com.android.systemui.statusbar.quickactions.av.ui.viewmodel

import android.platform.test.annotations.EnableFlags
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.Flags.FLAG_EXPANDED_PRIVACY_INDICATORS_ON_LARGE_SCREEN
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.lifecycle.activateIn
import com.android.systemui.privacy.PrivacyApplication
import com.android.systemui.privacy.PrivacyItem
import com.android.systemui.privacy.PrivacyType
import com.android.systemui.shade.data.repository.fakePrivacyChipRepository
import com.android.systemui.testKosmosNew
import com.google.common.truth.Truth.assertThat
import kotlin.test.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class AvControlsPanelContentViewModelTest() : SysuiTestCase() {
    private val kosmos = testKosmosNew()
    private val Kosmos.underTest by
        Kosmos.Fixture {
            val res = avControlsPanelContentViewModelFactory.create {}
            res.activateIn(testScope)
            res
        }
    private val cameraItem =
        PrivacyItem(PrivacyType.TYPE_CAMERA, PrivacyApplication("fakepackage", 0))
    private val microphoneItem =
        PrivacyItem(PrivacyType.TYPE_MICROPHONE, PrivacyApplication("fakepackage", 0))

    @Test
    @EnableFlags(FLAG_EXPANDED_PRIVACY_INDICATORS_ON_LARGE_SCREEN)
    fun contentComposition_allSensors() =
        kosmos.runTest {
            fakePrivacyChipRepository.setPrivacyItems(listOf(cameraItem, microphoneItem))

            assertThat(underTest.showStudioLookControls).isTrue()
            assertThat(underTest.showBlurControls).isTrue()
            assertThat(underTest.showCameraFramingButton).isTrue()
            assertThat(underTest.showStudioMicButton).isTrue()
            assertThat(underTest.showLiveCaptionsButton).isTrue()
        }

    @Test
    @EnableFlags(FLAG_EXPANDED_PRIVACY_INDICATORS_ON_LARGE_SCREEN)
    fun contentComposition_cameraOnly() =
        kosmos.runTest {
            fakePrivacyChipRepository.setPrivacyItems(listOf(cameraItem))

            assertThat(underTest.showStudioLookControls).isTrue()
            assertThat(underTest.showBlurControls).isTrue()
            assertThat(underTest.showCameraFramingButton).isTrue()
            assertThat(underTest.showStudioMicButton).isFalse()
            assertThat(underTest.showLiveCaptionsButton).isTrue()
        }

    @Test
    @EnableFlags(FLAG_EXPANDED_PRIVACY_INDICATORS_ON_LARGE_SCREEN)
    fun contentComposition_microphoneOnly() =
        kosmos.runTest {
            fakePrivacyChipRepository.setPrivacyItems(listOf(microphoneItem))

            assertThat(underTest.showStudioLookControls).isFalse()
            assertThat(underTest.showBlurControls).isFalse()
            assertThat(underTest.showCameraFramingButton).isFalse()
            assertThat(underTest.showStudioMicButton).isTrue()
            assertThat(underTest.showLiveCaptionsButton).isTrue()
        }

    @Test
    @EnableFlags(FLAG_EXPANDED_PRIVACY_INDICATORS_ON_LARGE_SCREEN)
    fun contentComposition_noSensors() =
        kosmos.runTest {
            fakePrivacyChipRepository.setPrivacyItems(listOf())

            assertThat(underTest.showStudioLookControls).isFalse()
            assertThat(underTest.showBlurControls).isFalse()
            assertThat(underTest.showCameraFramingButton).isFalse()
            assertThat(underTest.showStudioMicButton).isFalse()
            // Live captions is always shown
            assertThat(underTest.showLiveCaptionsButton).isTrue()
        }
}
