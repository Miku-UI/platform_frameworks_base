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

package com.android.systemui.statusbar.quickactions.ime.ui.viewmodel

import android.graphics.RectF
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.view.Display
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.Flags
import com.android.systemui.SysuiTestCase
import com.android.systemui.common.shared.model.ContentDescription
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.inputmethod.data.model.InputMethodModel
import com.android.systemui.inputmethod.data.repository.fakeInputMethodRepository
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.lifecycle.activateIn
import com.android.systemui.res.R
import com.android.systemui.scene.SceneHelper.setDeviceEntered
import com.android.systemui.scene.domain.interactor.sceneInteractor
import com.android.systemui.scene.shared.model.Overlays
import com.android.systemui.shade.domain.interactor.enableDualShade
import com.android.systemui.statusbar.policy.data.repository.fakeDeviceProvisioningRepository
import com.android.systemui.statusbar.policy.data.repository.fakeUserSetupRepository
import com.android.systemui.statusbar.quickactions.av.ui.viewmodel.avControlsPopupViewModelFactory
import com.android.systemui.statusbar.quickactions.domain.interactor.quickActionsInteractor
import com.android.systemui.statusbar.quickactions.media.ui.viewmodel.mediaControlPopupViewModelFactory
import com.android.systemui.statusbar.quickactions.screenrecord.ui.viewmodel.largeScreenStopRecordingPopupViewModel2Factory
import com.android.systemui.statusbar.quickactions.shared.model.ChipContent
import com.android.systemui.statusbar.quickactions.shared.model.QuickActionChipId
import com.android.systemui.statusbar.quickactions.shared.model.QuickActionChipModel
import com.android.systemui.statusbar.quickactions.shared.model.QuickActionPanelModel
import com.android.systemui.statusbar.quickactions.sharescreen.ui.viewmodel.fakeShareScreenPrivacyIndicatorPopupViewModelFactory
import com.android.systemui.testKosmosNew
import com.android.systemui.user.data.repository.fakeUserRepository
import com.google.common.truth.Truth.assertThat
import kotlin.test.assertIs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@SmallTest
@RunWith(AndroidJUnit4::class)
class ImeIndicatorChipViewModelTest : SysuiTestCase() {

    private val kosmos = testKosmosNew()
    private val fakeInputMethodRepository = kosmos.fakeInputMethodRepository
    private val fakeUserSetupRepository = kosmos.fakeUserSetupRepository
    private val fakeDeviceProvisioningRepository = kosmos.fakeDeviceProvisioningRepository
    private val fakeUserRepository = kosmos.fakeUserRepository
    private val Kosmos.underTest by
        Kosmos.Fixture {
            imeIndicatorChipViewModelFactory.create(Display.DEFAULT_DISPLAY).apply {
                activateIn(testScope)
            }
        }

    @Before
    fun setUp() {
        fakeUserSetupRepository.setUserSetUp(true)
        fakeDeviceProvisioningRepository.setDeviceProvisioned(true)
        fakeUserRepository.setUserManagerLogoutEnabled(true)
        kosmos.setDeviceEntered()
    }

    @Test
    @DisableFlags(Flags.FLAG_STATUS_BAR_IME_CHIP)
    fun chip_flagDisabled_isHidden() =
        kosmos.runTest { assertIs<QuickActionChipModel.Hidden>(underTest.chip) }

    @Test
    @EnableFlags(Flags.FLAG_STATUS_BAR_IME_CHIP)
    fun chip_multipleImesEnabled_isShown() =
        kosmos.runTest {
            fakeInputMethodRepository.selectedInputMethodSubtypes = INPUT_METHOD_SUBTYPES
            assertIs<QuickActionChipModel.LaunchChip>(underTest.chip)
        }

    @Test
    @EnableFlags(Flags.FLAG_STATUS_BAR_IME_CHIP)
    fun chip_selectedSubtypeWithIcon_showsIcon() =
        kosmos.runTest {
            val subtypeIcon =
                InputMethodModel.SubtypeIcon(
                    resId = R.drawable.ic_android,
                    packageName = context.packageName,
                )
            val subtype =
                InputMethodModel.Subtype(
                    subtypeId = 123,
                    isAuxiliary = false,
                    icon = subtypeIcon,
                    shortLabel = "EN",
                )
            fakeInputMethodRepository.selectedInputMethodSubtypes =
                listOf(subtype, InputMethodModel.Subtype(subtypeId = 1, isAuxiliary = false))
            fakeInputMethodRepository.setSelectedInputMethodSubtypeId(subtype.subtypeId)

            val chip = assertIs<QuickActionChipModel.LaunchChip>(underTest.chip)
            val content = assertIs<ChipContent.IconOnly>(chip.chipContent)
            val loadedIcon = assertIs<Icon.Loaded>(content.icon)

            assertThat(loadedIcon.resId).isEqualTo(subtypeIcon.resId)
            assertThat(loadedIcon.packageName).isEqualTo(subtypeIcon.packageName)
            assertThat(chip.contentDescription)
                .isEqualTo(
                    ContentDescription.Resource(
                        R.string.accessibility_status_bar_input_method_indicator
                    )
                )
        }

    @Test
    @EnableFlags(Flags.FLAG_STATUS_BAR_IME_CHIP)
    fun chip_selectedSubtypeWithShortLabelButNoIcon_showsShortLabel() =
        kosmos.runTest {
            val subtype =
                InputMethodModel.Subtype(
                    subtypeId = 123,
                    isAuxiliary = false,
                    icon = null,
                    shortLabel = "EN",
                )
            fakeInputMethodRepository.selectedInputMethodSubtypes =
                listOf(subtype, InputMethodModel.Subtype(subtypeId = 1, isAuxiliary = false))
            fakeInputMethodRepository.setSelectedInputMethodSubtypeId(subtype.subtypeId)

            val chip = assertIs<QuickActionChipModel.LaunchChip>(underTest.chip)
            val content = assertIs<ChipContent.Text>(chip.chipContent)

            assertThat(content.text).isEqualTo("EN")
        }

    @Test
    @EnableFlags(Flags.FLAG_STATUS_BAR_IME_CHIP)
    fun chip_selectedSubtypeWithNoIconOrShortLabel_showsDefaultKeyboardIcon() =
        kosmos.runTest {
            val subtype =
                InputMethodModel.Subtype(
                    subtypeId = 123,
                    isAuxiliary = false,
                    icon = null,
                    shortLabel = null,
                )
            fakeInputMethodRepository.selectedInputMethodSubtypes =
                listOf(subtype, InputMethodModel.Subtype(subtypeId = 1, isAuxiliary = false))
            fakeInputMethodRepository.setSelectedInputMethodSubtypeId(subtype.subtypeId)

            val chip = assertIs<QuickActionChipModel.LaunchChip>(underTest.chip)
            val content = assertIs<ChipContent.IconOnly>(chip.chipContent)

            assertThat(content.icon)
                .isEqualTo(
                    Icon.Resource(
                        R.drawable.ic_keyboard,
                        ContentDescription.Resource(
                            R.string.accessibility_status_bar_input_method_indicator
                        ),
                    )
                )
        }

    @Test
    @EnableFlags(Flags.FLAG_STATUS_BAR_IME_CHIP)
    fun chip_noSubtypeSelected_showsDefaultKeyboardIcon() =
        kosmos.runTest {
            fakeInputMethodRepository.selectedInputMethodSubtypes = INPUT_METHOD_SUBTYPES

            val chip = assertIs<QuickActionChipModel.LaunchChip>(underTest.chip)
            val content = assertIs<ChipContent.IconOnly>(chip.chipContent)

            assertThat(content.icon)
                .isEqualTo(
                    Icon.Resource(
                        R.drawable.ic_keyboard,
                        ContentDescription.Resource(
                            R.string.accessibility_status_bar_input_method_indicator
                        ),
                    )
                )
        }

    @Test
    @EnableFlags(Flags.FLAG_STATUS_BAR_IME_CHIP)
    fun chip_onClick_togglesInputMethodPicker() =
        kosmos.runTest {
            fakeInputMethodRepository.selectedInputMethodSubtypes = INPUT_METHOD_SUBTYPES
            val chip = assertIs<QuickActionChipModel.LaunchChip>(underTest.chip)
            assertThat(fakeInputMethodRepository.inputMethodPickerShownDisplayId).isNull()

            chip.onClick(context)
            testScope.runCurrent()

            assertThat(fakeInputMethodRepository.inputMethodPickerShownDisplayId)
                .isEqualTo(Display.DEFAULT_DISPLAY)

            chip.onClick(context)
            testScope.runCurrent()

            assertThat(fakeInputMethodRepository.inputMethodPickerShownDisplayId).isNull()
        }

    @Test
    @EnableFlags(Flags.FLAG_STATUS_BAR_IME_CHIP, Flags.FLAG_DUAL_SHADE)
    fun chip_onNotificationsShadeShown_hidesInputMethodPicker() =
        kosmos.runTest {
            enableDualShade()
            fakeInputMethodRepository.selectedInputMethodSubtypes = INPUT_METHOD_SUBTYPES
            val chip = assertIs<QuickActionChipModel.LaunchChip>(underTest.chip)
            assertThat(fakeInputMethodRepository.inputMethodPickerShownDisplayId).isNull()

            sceneInteractor.showOverlay(Overlays.NotificationsShade, LOGGING_REASON)
            testScope.runCurrent()

            assertThat(fakeInputMethodRepository.inputMethodPickerShownDisplayId).isNull()

            sceneInteractor.hideOverlay(Overlays.NotificationsShade, LOGGING_REASON)

            chip.onClick(context)
            testScope.runCurrent()
            assertThat(fakeInputMethodRepository.inputMethodPickerShownDisplayId).isNotNull()

            sceneInteractor.showOverlay(Overlays.NotificationsShade, LOGGING_REASON)
            testScope.runCurrent()

            assertThat(fakeInputMethodRepository.inputMethodPickerShownDisplayId).isNull()
        }

    @Test
    @EnableFlags(Flags.FLAG_STATUS_BAR_IME_CHIP, Flags.FLAG_DUAL_SHADE)
    fun chip_onQuickSettingsShadeShown_hidesInputMethodPicker() =
        kosmos.runTest {
            enableDualShade()
            fakeInputMethodRepository.selectedInputMethodSubtypes = INPUT_METHOD_SUBTYPES
            val chip = assertIs<QuickActionChipModel.LaunchChip>(underTest.chip)
            assertThat(fakeInputMethodRepository.inputMethodPickerShownDisplayId).isNull()

            sceneInteractor.showOverlay(Overlays.QuickSettingsShade, LOGGING_REASON)
            testScope.runCurrent()

            assertThat(fakeInputMethodRepository.inputMethodPickerShownDisplayId).isNull()

            sceneInteractor.hideOverlay(Overlays.QuickSettingsShade, LOGGING_REASON)

            chip.onClick(context)
            testScope.runCurrent()
            assertThat(fakeInputMethodRepository.inputMethodPickerShownDisplayId).isNotNull()

            sceneInteractor.showOverlay(Overlays.QuickSettingsShade, LOGGING_REASON)
            testScope.runCurrent()

            assertThat(fakeInputMethodRepository.inputMethodPickerShownDisplayId).isNull()
        }

    @Test
    @EnableFlags(Flags.FLAG_STATUS_BAR_IME_CHIP)
    fun chip_onAssistantChipShown_hidesInputMethodPicker() =
        kosmos.runTest {
            // TODO: b/478352392 - Add test coverage.
        }

    @Test
    @EnableFlags(Flags.FLAG_STATUS_BAR_IME_CHIP)
    fun chip_onMediaControlQuickActionPanelShown_hidesInputMethodPicker() =
        kosmos.runTest {
            fakeInputMethodRepository.selectedInputMethodSubtypes = INPUT_METHOD_SUBTYPES
            val chip = assertIs<QuickActionChipModel.LaunchChip>(underTest.chip)
            assertThat(fakeInputMethodRepository.inputMethodPickerShownDisplayId).isNull()

            quickActionsInteractor.expand(
                QuickActionPanelModel(
                    QuickActionChipId.MediaControl,
                    ANCHOR_BOUNDS,
                    mediaControlPopupViewModelFactory,
                )
            )
            testScope.runCurrent()

            assertThat(fakeInputMethodRepository.inputMethodPickerShownDisplayId).isNull()

            chip.onClick(context)
            testScope.runCurrent()
            assertThat(fakeInputMethodRepository.inputMethodPickerShownDisplayId).isNotNull()

            quickActionsInteractor.expand(
                QuickActionPanelModel(
                    QuickActionChipId.MediaControl,
                    ANCHOR_BOUNDS,
                    mediaControlPopupViewModelFactory,
                )
            )
            testScope.runCurrent()

            assertThat(fakeInputMethodRepository.inputMethodPickerShownDisplayId).isNull()
        }

    @Test
    @EnableFlags(Flags.FLAG_STATUS_BAR_IME_CHIP)
    fun chip_onAvControlsIndicatorQuickActionPanelShown_hidesInputMethodPicker() =
        kosmos.runTest {
            fakeInputMethodRepository.selectedInputMethodSubtypes = INPUT_METHOD_SUBTYPES
            val chip = assertIs<QuickActionChipModel.LaunchChip>(underTest.chip)
            assertThat(fakeInputMethodRepository.inputMethodPickerShownDisplayId).isNull()

            quickActionsInteractor.expand(
                QuickActionPanelModel(
                    QuickActionChipId.AvControlsIndicator,
                    ANCHOR_BOUNDS,
                    avControlsPopupViewModelFactory,
                )
            )
            testScope.runCurrent()

            assertThat(fakeInputMethodRepository.inputMethodPickerShownDisplayId).isNull()

            chip.onClick(context)
            testScope.runCurrent()
            assertThat(fakeInputMethodRepository.inputMethodPickerShownDisplayId).isNotNull()

            quickActionsInteractor.expand(
                QuickActionPanelModel(
                    QuickActionChipId.AvControlsIndicator,
                    ANCHOR_BOUNDS,
                    avControlsPopupViewModelFactory,
                )
            )
            testScope.runCurrent()

            assertThat(fakeInputMethodRepository.inputMethodPickerShownDisplayId).isNull()
        }

    @Test
    @EnableFlags(Flags.FLAG_STATUS_BAR_IME_CHIP)
    fun chip_onShareScreenPrivacyIndicatorQuickActionPanelShown_hidesInputMethodPicker() =
        kosmos.runTest {
            fakeInputMethodRepository.selectedInputMethodSubtypes = INPUT_METHOD_SUBTYPES
            val chip = assertIs<QuickActionChipModel.LaunchChip>(underTest.chip)
            assertThat(fakeInputMethodRepository.inputMethodPickerShownDisplayId).isNull()

            quickActionsInteractor.expand(
                QuickActionPanelModel(
                    QuickActionChipId.ShareScreenPrivacyIndicator,
                    ANCHOR_BOUNDS,
                    fakeShareScreenPrivacyIndicatorPopupViewModelFactory,
                )
            )
            testScope.runCurrent()

            assertThat(fakeInputMethodRepository.inputMethodPickerShownDisplayId).isNull()

            chip.onClick(context)
            testScope.runCurrent()
            assertThat(fakeInputMethodRepository.inputMethodPickerShownDisplayId).isNotNull()

            quickActionsInteractor.expand(
                QuickActionPanelModel(
                    QuickActionChipId.ShareScreenPrivacyIndicator,
                    ANCHOR_BOUNDS,
                    fakeShareScreenPrivacyIndicatorPopupViewModelFactory,
                )
            )
            testScope.runCurrent()

            assertThat(fakeInputMethodRepository.inputMethodPickerShownDisplayId).isNull()
        }

    @Test
    @EnableFlags(Flags.FLAG_STATUS_BAR_IME_CHIP)
    fun chip_onScreenRecordingQuickActionPanelShown_hidesInputMethodPicker() =
        kosmos.runTest {
            fakeInputMethodRepository.selectedInputMethodSubtypes = INPUT_METHOD_SUBTYPES
            val chip = assertIs<QuickActionChipModel.LaunchChip>(underTest.chip)
            assertThat(fakeInputMethodRepository.inputMethodPickerShownDisplayId).isNull()

            quickActionsInteractor.expand(
                QuickActionPanelModel(
                    QuickActionChipId.ScreenRecording,
                    ANCHOR_BOUNDS,
                    largeScreenStopRecordingPopupViewModel2Factory,
                )
            )
            testScope.runCurrent()

            assertThat(fakeInputMethodRepository.inputMethodPickerShownDisplayId).isNull()

            chip.onClick(context)
            testScope.runCurrent()
            assertThat(fakeInputMethodRepository.inputMethodPickerShownDisplayId).isNotNull()

            quickActionsInteractor.expand(
                QuickActionPanelModel(
                    QuickActionChipId.ScreenRecording,
                    ANCHOR_BOUNDS,
                    largeScreenStopRecordingPopupViewModel2Factory,
                )
            )
            testScope.runCurrent()

            assertThat(fakeInputMethodRepository.inputMethodPickerShownDisplayId).isNull()
        }

    companion object {
        private val INPUT_METHOD_SUBTYPES =
            listOf(
                InputMethodModel.Subtype(subtypeId = 1, isAuxiliary = false),
                InputMethodModel.Subtype(subtypeId = 2, isAuxiliary = false),
            )
        private val LOGGING_REASON = "test"
        private val ANCHOR_BOUNDS = RectF()
    }
}
