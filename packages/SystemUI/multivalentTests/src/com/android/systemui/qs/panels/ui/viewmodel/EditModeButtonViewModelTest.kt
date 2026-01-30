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

package com.android.systemui.qs.panels.ui.viewmodel

import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.Flags
import com.android.systemui.SysuiTestCase
import com.android.systemui.classifier.fakeFalsingManager
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.lifecycle.activateIn
import com.android.systemui.plugins.activityStarter
import com.android.systemui.qs.panels.data.repository.qsPreferencesRepository
import com.android.systemui.qs.panels.ui.viewmodel.toolbar.EditModeButtonViewModel
import com.android.systemui.qs.panels.ui.viewmodel.toolbar.editModeButtonViewModelFactory
import com.android.systemui.testKosmos
import com.android.systemui.user.domain.interactor.fakeHeadlessSystemUserMode
import com.android.systemui.user.domain.interactor.headlessSystemUserMode
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
@SmallTest
class EditModeButtonViewModelTest : SysuiTestCase() {
    val kosmos = testKosmos().useUnconfinedTestDispatcher()

    // NOTE: cannot instantiate EditModeButtonViewModel here because it would hydrate
    // showEditButton before the value on fakeHeadlessSystemUserMode is set

    @Before
    fun setUp() {
        with(kosmos) {
            whenever(activityStarter.postQSRunnableDismissingKeyguard(any())).doAnswer {
                (it.getArgument(0) as Runnable).run()
            }
        }
    }

    @Test
    fun falsingFalseTap_editModeDoesntStart() =
        kosmos.runTest {
            val underTest = createEditModeButtonViewModel()
            val isEditing by collectLastValue(editModeViewModel.isEditing)

            fakeFalsingManager.setFalseTap(true)

            underTest.onButtonClick()

            assertThat(isEditing).isFalse()
        }

    @Test
    fun falsingNotFalseTap_editModeStarted() =
        kosmos.runTest {
            val underTest = createEditModeButtonViewModel()
            val isEditing by collectLastValue(editModeViewModel.isEditing)

            fakeFalsingManager.setFalseTap(false)

            underTest.onButtonClick()

            assertThat(isEditing).isTrue()
        }

    @Test
    @DisableFlags(Flags.FLAG_HSU_QS_CHANGES)
    fun isEditButtonVisibleTrue_nonHsu_flagDisabled() =
        kosmos.runTest {
            val underTest = createEditModeButtonViewModel(false)

            assertThat(underTest.isEditButtonVisible).isTrue()
        }

    @Test
    @DisableFlags(Flags.FLAG_HSU_QS_CHANGES)
    fun isEditButtonVisibleTrue_hsu_flagDisabled() =
        kosmos.runTest {
            val underTest = createEditModeButtonViewModel(true)

            assertThat(underTest.isEditButtonVisible).isTrue()
        }

    @Test
    @EnableFlags(Flags.FLAG_HSU_QS_CHANGES)
    fun isEditButtonVisibleTrue_nonHsu_flagEnabled() =
        kosmos.runTest {
            val underTest = createEditModeButtonViewModel(false)

            assertThat(underTest.isEditButtonVisible).isTrue()
        }

    @Test
    @EnableFlags(Flags.FLAG_HSU_QS_CHANGES)
    fun isEditButtonVisibleFalse_hsu_flagEnabled() =
        kosmos.runTest {
            val underTest = createEditModeButtonViewModel(true)

            assertThat(underTest.isEditButtonVisible).isFalse()
        }

    @Test
    @EnableFlags(Flags.FLAG_QS_EDIT_MODE_TOOLTIP)
    fun showTooltip_tooltipWasAlreadyShown_shouldNotBeVisible() =
        kosmos.runTest {
            val underTest = createEditModeButtonViewModel()
            qsPreferencesRepository.writeEditTooltipShown(true)

            assertThat(underTest.showTooltip).isFalse()
        }

    @Test
    @EnableFlags(Flags.FLAG_QS_EDIT_MODE_TOOLTIP)
    fun showTooltip_tooltipWasNotShown_shouldBeVisible() =
        kosmos.runTest {
            val underTest = createEditModeButtonViewModel()
            qsPreferencesRepository.writeEditTooltipShown(false)

            assertThat(underTest.showTooltip).isTrue()
        }

    @Test
    @EnableFlags(Flags.FLAG_QS_EDIT_MODE_TOOLTIP)
    fun showTooltip_tooltipWasDismissed_shouldBeMarkedAsShown() =
        kosmos.runTest {
            val underTest = createEditModeButtonViewModel()
            qsPreferencesRepository.writeEditTooltipShown(false)

            assertThat(underTest.showTooltip).isTrue()

            underTest.onTooltipDisposed()

            assertThat(underTest.showTooltip).isFalse()
        }

    @Test
    @DisableFlags(Flags.FLAG_QS_EDIT_MODE_TOOLTIP)
    fun showTooltip_flagDisabled_shouldNotBeVisible() =
        kosmos.runTest {
            val underTest = createEditModeButtonViewModel()
            qsPreferencesRepository.writeEditTooltipShown(false)

            assertThat(underTest.showTooltip).isFalse()
        }

    private fun Kosmos.createEditModeButtonViewModel(
        isHeadlessSystemUser: Boolean = false
    ): EditModeButtonViewModel {
        headlessSystemUserMode = fakeHeadlessSystemUserMode
        fakeHeadlessSystemUserMode.setIsHeadlessSystemUser(isHeadlessSystemUser)
        return editModeButtonViewModelFactory.create(ignoreTestHarness = true).apply {
            activateIn(testScope)
        }
    }
}
