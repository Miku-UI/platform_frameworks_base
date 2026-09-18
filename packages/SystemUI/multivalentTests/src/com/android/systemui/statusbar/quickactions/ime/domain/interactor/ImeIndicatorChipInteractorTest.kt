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

package com.android.systemui.statusbar.quickactions.ime.domain.interactor

import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.Flags
import com.android.systemui.SysuiTestCase
import com.android.systemui.deviceentry.data.repository.fakeDeviceEntryRepository
import com.android.systemui.inputmethod.data.model.InputMethodModel
import com.android.systemui.inputmethod.data.repository.fakeInputMethodRepository
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.backgroundScope
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.scene.SceneHelper.setDeviceEntered
import com.android.systemui.statusbar.policy.data.repository.fakeDeviceProvisioningRepository
import com.android.systemui.statusbar.policy.data.repository.fakeUserSetupRepository
import com.android.systemui.testKosmosNew
import com.android.systemui.user.data.repository.FakeUserRepository
import com.android.systemui.user.data.repository.fakeUserRepository
import com.android.systemui.util.settings.fakeSettings
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@SmallTest
@RunWith(AndroidJUnit4::class)
class ImeIndicatorChipInteractorTest : SysuiTestCase() {

    private val kosmos = testKosmosNew()
    private val fakeInputMethodRepository = kosmos.fakeInputMethodRepository
    private val fakeUserRepository = kosmos.fakeUserRepository
    private val fakeUserSetupRepository = kosmos.fakeUserSetupRepository
    private val fakeDeviceProvisioningRepository = kosmos.fakeDeviceProvisioningRepository
    private val fakeDeviceEntryRepository = kosmos.fakeDeviceEntryRepository
    private val Kosmos.underTest by Kosmos.Fixture { kosmos.imeIndicatorChipInteractor }

    @Before
    fun setUp() {
        fakeUserSetupRepository.setUserSetUp(true)
        fakeDeviceProvisioningRepository.setDeviceProvisioned(true)
        fakeUserRepository.setUserManagerLogoutEnabled(true)
        kosmos.setDeviceEntered()
        setUpTwoImes()
    }

    @Test
    @DisableFlags(Flags.FLAG_STATUS_BAR_IME_CHIP)
    fun chip_flagDisabled_isNotVisible() =
        kosmos.runTest {
            backgroundScope.launch { underTest.chipModel.collect {} }
            assertThat(underTest.chipModel.value.isVisible).isFalse()
        }

    @Test
    @EnableFlags(Flags.FLAG_STATUS_BAR_IME_CHIP)
    fun chip_visibility_updatesOnImeCountChange() =
        kosmos.runTest {
            backgroundScope.launch { underTest.chipModel.collect {} }

            // One IME, one subtype.
            val subtype1 = InputMethodModel.Subtype(subtypeId = 1, isAuxiliary = false)
            val ime1 = InputMethodModel(USER_ID, "ime1", listOf(subtype1))
            fakeInputMethodRepository.setEnabledInputMethods(USER_ID, ime1)
            fakeInputMethodRepository.selectedInputMethodSubtypes = listOf(subtype1)
            kosmos.fakeSettings.putStringForUser(
                Settings.Secure.ENABLED_INPUT_METHODS,
                "ime1",
                USER_ID,
            )
            testScope.runCurrent()

            assertThat(underTest.chipModel.value.isVisible).isFalse()

            // Two IMEs, one subtype each.
            val subtype2 = InputMethodModel.Subtype(subtypeId = 2, isAuxiliary = false)
            val ime2 = InputMethodModel(USER_ID, "ime2", listOf(subtype2))
            fakeInputMethodRepository.setEnabledInputMethods(USER_ID, ime1, ime2)
            fakeInputMethodRepository.selectedInputMethodSubtypes = listOf(subtype1)
            kosmos.fakeSettings.putStringForUser(
                Settings.Secure.ENABLED_INPUT_METHODS,
                "ime1:ime2",
                USER_ID,
            )
            testScope.runCurrent()

            assertThat(underTest.chipModel.value.isVisible).isTrue()
        }

    @Test
    @EnableFlags(Flags.FLAG_STATUS_BAR_IME_CHIP)
    fun chip_isVisible_whenUserNotSetup() =
        kosmos.runTest {
            backgroundScope.launch { underTest.chipModel.collect {} }
            fakeUserSetupRepository.setUserSetUp(false)
            testScope.runCurrent()

            assertThat(underTest.chipModel.value.isVisible).isTrue()
        }

    @Test
    @EnableFlags(Flags.FLAG_STATUS_BAR_IME_CHIP)
    fun chip_isVisible_whenDeviceNotProvisioned() =
        kosmos.runTest {
            backgroundScope.launch { underTest.chipModel.collect {} }
            fakeDeviceProvisioningRepository.setDeviceProvisioned(false)
            testScope.runCurrent()

            assertThat(underTest.chipModel.value.isVisible).isTrue()
        }

    @Test
    @EnableFlags(Flags.FLAG_STATUS_BAR_IME_CHIP)
    fun chip_notVisible_whenUserNotLoggedIn() =
        kosmos.runTest {
            backgroundScope.launch { underTest.chipModel.collect {} }
            fakeUserRepository.setUserManagerLogoutEnabled(false)
            testScope.runCurrent()

            assertThat(underTest.chipModel.value.isVisible).isFalse()
        }

    @Test
    @EnableFlags(Flags.FLAG_STATUS_BAR_IME_CHIP)
    fun chip_selectedSubtypeUpdated_updatesChipModel() =
        kosmos.runTest {
            backgroundScope.launch { underTest.chipModel.collect {} }
            assertThat(underTest.chipModel.value.selectedSubtype).isNull()

            val subtype = InputMethodModel.Subtype(subtypeId = 123, isAuxiliary = false)
            fakeInputMethodRepository.selectedInputMethodSubtypes = listOf(subtype)
            fakeInputMethodRepository.setSelectedInputMethodSubtypeId(subtype.subtypeId)

            assertThat(underTest.chipModel.value.selectedSubtype).isEqualTo(subtype)
        }

    @Test
    @EnableFlags(Flags.FLAG_STATUS_BAR_IME_CHIP)
    fun toggleInputMethodPicker_togglesPicker() =
        kosmos.runTest {
            underTest.toggleInputMethodPicker(DISPLAY_ID)
            testScope.runCurrent()

            assertThat(fakeInputMethodRepository.inputMethodPickerShownEntryPoint)
                .isEqualTo(InputMethodManager.IM_PICKER_ENTRY_POINT_STATUS_BAR_CHIP)
            assertThat(fakeInputMethodRepository.inputMethodPickerShownDisplayId)
                .isEqualTo(DISPLAY_ID)
        }

    @Test
    @EnableFlags(Flags.FLAG_STATUS_BAR_IME_CHIP)
    fun hideInputMethodPicker_hidesPicker() =
        kosmos.runTest {
            underTest.hideInputMethodPicker(DISPLAY_ID)
            testScope.runCurrent()

            assertThat(fakeInputMethodRepository.inputMethodPickerShownEntryPoint).isNull()
            assertThat(fakeInputMethodRepository.inputMethodPickerShownDisplayId).isNull()
        }

    private fun setUpTwoImes() {
        val subtype1 = InputMethodModel.Subtype(subtypeId = 1, isAuxiliary = false)
        val subtype2 = InputMethodModel.Subtype(subtypeId = 2, isAuxiliary = false)
        val ime1 = InputMethodModel(USER_ID, "ime1", listOf(subtype1))
        val ime2 = InputMethodModel(USER_ID, "ime2", listOf(subtype2))
        fakeInputMethodRepository.setEnabledInputMethods(USER_ID, ime1, ime2)
        kosmos.fakeSettings.putStringForUser(
            Settings.Secure.ENABLED_INPUT_METHODS,
            "ime1:ime2",
            USER_ID,
        )
    }

    companion object {
        const val USER_ID = FakeUserRepository.DEFAULT_SELECTED_USER
        const val DISPLAY_ID = 1
    }
}
