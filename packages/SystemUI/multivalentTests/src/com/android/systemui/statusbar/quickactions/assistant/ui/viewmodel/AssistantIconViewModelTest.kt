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

package com.android.systemui.statusbar.quickactions.assistant.ui.viewmodel

import android.content.ComponentName
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.lifecycle.activateIn
import com.android.systemui.res.R
import com.android.systemui.statusbar.quickactions.assistant.domain.interactor.fakeAssistantIconInteractor
import com.android.systemui.statusbar.quickactions.assistant.shared.model.AssistantIconSharedModel
import com.android.systemui.statusbar.quickactions.shared.model.ChipContent
import com.android.systemui.statusbar.quickactions.shared.model.QuickActionChipId
import com.android.systemui.statusbar.quickactions.shared.model.QuickActionChipModel
import com.android.systemui.testKosmosNew
import com.google.common.truth.Truth.assertThat
import kotlin.test.Test
import org.junit.Before
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class AssistantIconViewModelTest : SysuiTestCase() {

    private val kosmos = testKosmosNew().useUnconfinedTestDispatcher()

    private val interactor = kosmos.fakeAssistantIconInteractor
    private val Kosmos.underTest by Kosmos.Fixture { AssistantIconViewModel(interactor) }

    @Before
    fun setUp() {
        with(kosmos) { underTest.activateIn(kosmos.testScope) }
    }

    @Test
    fun assistantIcon_initialState_isHidden() = kosmos.runTest { underTest.chip.assertIsHidden() }

    @Test
    fun assistantIcon_isHidden_whenAssistInfoIsNull() =
        kosmos.runTest {
            interactor.setAssistantIconSharedModel(
                AssistantIconSharedModel(assistInfo = null, isStatusBarAssistantPackage = true)
            )

            val sharedModel by collectLastValue(interactor.assistantIconSharedModel)
            assertThat(sharedModel!!.isStatusBarAssistantPackage).isTrue()
            underTest.chip.assertIsHidden()
        }

    @Test
    fun assistantIcon_isHidden_whenAssistInfoNotMatch() =
        kosmos.runTest {
            interactor.setAssistantIconSharedModel(
                AssistantIconSharedModel(
                    assistInfo = DEFAULT_COMPONENT_NAME,
                    isStatusBarAssistantPackage = false,
                )
            )

            val sharedModel by collectLastValue(interactor.assistantIconSharedModel)
            assertThat(sharedModel!!.isStatusBarAssistantPackage).isFalse()
            assertThat(sharedModel!!.assistInfo).isNotNull()
            underTest.chip.assertIsHidden()
        }

    @Test
    fun assistantIcon_isShown() =
        kosmos.runTest {
            interactor.setAssistantIconSharedModel(
                AssistantIconSharedModel(
                    assistInfo = DEFAULT_COMPONENT_NAME,
                    isStatusBarAssistantPackage = true,
                )
            )

            underTest.chip.assertIsShown().verifyIcon(RES_ID)
        }

    private companion object {
        private val RES_ID = R.drawable.ic_assistant_icon
        private val DEFAULT_COMPONENT_NAME = ComponentName("test.package", "test.package.class")
    }
}

private fun QuickActionChipModel.assertIsHidden(): QuickActionChipModel.Hidden {
    assertThat(this.chipId).isEqualTo(QuickActionChipId.AssistantIcon)
    assertThat(this).isInstanceOf(QuickActionChipModel.Hidden::class.java)
    return this as QuickActionChipModel.Hidden
}

private fun QuickActionChipModel.assertIsShown(): QuickActionChipModel.LaunchChip {
    assertThat(this.chipId).isEqualTo(QuickActionChipId.AssistantIcon)
    assertThat(this).isInstanceOf(QuickActionChipModel.LaunchChip::class.java)
    return this as QuickActionChipModel.LaunchChip
}

private fun QuickActionChipModel.LaunchChip.verifyIcon(res: Int) {
    assertThat(this.chipContent)
        .isEqualTo(ChipContent.IconOnly(Icon.Resource(resId = res, contentDescription = null)))
}
