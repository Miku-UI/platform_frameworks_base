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

import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.Flags.FLAG_EXPANDED_PRIVACY_INDICATORS_ON_LARGE_SCREEN
import com.android.systemui.SysuiTestCase
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.lifecycle.activateIn
import com.android.systemui.privacy.PrivacyApplication
import com.android.systemui.privacy.PrivacyItem
import com.android.systemui.privacy.PrivacyType
import com.android.systemui.shade.data.repository.fakePrivacyChipRepository
import com.android.systemui.statusbar.quickactions.shared.model.ChipIcon
import com.android.systemui.statusbar.quickactions.shared.model.QuickActionChipId
import com.android.systemui.statusbar.quickactions.shared.model.QuickActionChipModel
import com.android.systemui.testKosmosNew
import com.google.common.truth.Truth.assertThat
import kotlin.test.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class AvControlsChipViewModelTest() : SysuiTestCase() {
    private val kosmos = testKosmosNew()
    private val Kosmos.underTest by
        Kosmos.Fixture {
            val res = avControlsChipViewModelFactory.create()
            res.activateIn(testScope)
            res
        }
    private val cameraItem =
        PrivacyItem(PrivacyType.TYPE_CAMERA, PrivacyApplication("fakepackage", 0))
    private val microphoneItem =
        PrivacyItem(PrivacyType.TYPE_MICROPHONE, PrivacyApplication("fakepackage", 0))

    @Test
    @EnableFlags(FLAG_EXPANDED_PRIVACY_INDICATORS_ON_LARGE_SCREEN)
    fun avControlsChip_initialState_isHidden() = kosmos.runTest { underTest.chip.verifyHidden() }

    @Test
    @EnableFlags(FLAG_EXPANDED_PRIVACY_INDICATORS_ON_LARGE_SCREEN)
    fun avControlsChip_showingCamera_chipVisible() =
        kosmos.runTest {
            fakePrivacyChipRepository.setPrivacyItems(listOf(cameraItem))
            underTest.chip.verifyShown().verifyIsCameraOnlyChip()
        }

    @Test
    @EnableFlags(FLAG_EXPANDED_PRIVACY_INDICATORS_ON_LARGE_SCREEN)
    fun avControlsChip_showingMicrophone_chipVisible() =
        kosmos.runTest {
            fakePrivacyChipRepository.setPrivacyItems(listOf(microphoneItem))
            underTest.chip.verifyShown().verifyIsMicrophoneOnlyChip()
        }

    @Test
    @EnableFlags(FLAG_EXPANDED_PRIVACY_INDICATORS_ON_LARGE_SCREEN)
    fun avControlsChip_showingCameraAndMicrophone_chipVisible() =
        kosmos.runTest {
            fakePrivacyChipRepository.setPrivacyItems(listOf(cameraItem, microphoneItem))
            underTest.chip.verifyShown().verifyIsCameraAndMicrophoneChip()
        }

    @Test
    @EnableFlags(FLAG_EXPANDED_PRIVACY_INDICATORS_ON_LARGE_SCREEN)
    fun avControlsChip_chipUpdates() =
        kosmos.runTest {
            underTest.chip.verifyHidden()

            fakePrivacyChipRepository.setPrivacyItems(listOf(cameraItem))
            underTest.chip.verifyShown().verifyIsCameraOnlyChip()

            fakePrivacyChipRepository.setPrivacyItems(listOf())
            underTest.chip.verifyHidden()

            fakePrivacyChipRepository.setPrivacyItems(listOf(microphoneItem))
            underTest.chip.verifyShown().verifyIsMicrophoneOnlyChip()

            fakePrivacyChipRepository.setPrivacyItems(listOf(microphoneItem, cameraItem))
            underTest.chip.verifyShown().verifyIsCameraAndMicrophoneChip()
        }

    @Test
    @DisableFlags(FLAG_EXPANDED_PRIVACY_INDICATORS_ON_LARGE_SCREEN)
    fun avControlsChip_flagNotEnabled_isHidden() =
        kosmos.runTest {
            underTest.chip.verifyHidden()
            fakePrivacyChipRepository.setPrivacyItems(listOf(cameraItem))

            underTest.chip.verifyHidden()
            fakePrivacyChipRepository.setPrivacyItems(listOf())

            underTest.chip.verifyHidden()
            fakePrivacyChipRepository.setPrivacyItems(listOf(microphoneItem))

            underTest.chip.verifyHidden()
            fakePrivacyChipRepository.setPrivacyItems(listOf(microphoneItem, cameraItem))

            underTest.chip.verifyHidden()
        }
}

private fun QuickActionChipModel.verifyHidden(): QuickActionChipModel.Hidden {
    assertThat(this.chipId).isEqualTo(QuickActionChipId.AvControlsIndicator)
    assertThat(this).isInstanceOf(QuickActionChipModel.Hidden::class.java)
    return this as QuickActionChipModel.Hidden
}

private fun QuickActionChipModel.verifyShown(): QuickActionChipModel.PopupChip {
    assertThat(this.chipId).isEqualTo(QuickActionChipId.AvControlsIndicator)
    assertThat(this).isInstanceOf(QuickActionChipModel.PopupChip::class.java)
    return this as QuickActionChipModel.PopupChip
}

private fun QuickActionChipModel.PopupChip.verifyHasNoContent() {
    assertThat(this.chipContent).isEqualTo(null)
}

private fun QuickActionChipModel.PopupChip.verifyHasIcon(res: Int) {
    assertThat(this.icons).contains(ChipIcon(Icon.Resource(resId = res, contentDescription = null)))
}

private fun QuickActionChipModel.PopupChip.verifyNumberOfIcons(num: Int) {
    assertThat(this.icons.size).isEqualTo(num)
}

private fun QuickActionChipModel.PopupChip.verifyIsCameraOnlyChip() {
    verifyNumberOfIcons(1)
    verifyHasIcon(AvControlsChipViewModel.CAMERA_DRAWABLE)
    verifyHasNoContent()
}

private fun QuickActionChipModel.PopupChip.verifyIsMicrophoneOnlyChip() {
    verifyNumberOfIcons(1)
    verifyHasIcon(AvControlsChipViewModel.MICROPHONE_DRAWABLE)
    verifyHasNoContent()
}

private fun QuickActionChipModel.PopupChip.verifyIsCameraAndMicrophoneChip() {
    verifyNumberOfIcons(2)
    verifyHasIcon(AvControlsChipViewModel.CAMERA_DRAWABLE)
    verifyHasIcon(AvControlsChipViewModel.MICROPHONE_DRAWABLE)
    verifyHasNoContent()
}
