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

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.res.R
import com.android.systemui.statusbar.policy.ui.dialog.viewmodel.ModesDialogViewModel
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

@SmallTest
@RunWith(AndroidJUnit4::class)
class ModesDetailsViewModelTest : SysuiTestCase() {

    private val onSettingsClick: () -> Unit = mock()
    private val modesDialogViewModel: ModesDialogViewModel = mock()

    private val underTest by lazy {
        ModesDetailsViewModel(context, onSettingsClick, modesDialogViewModel)
    }

    @Test
    fun clickOnSettingsButton_invokesOnSettingsClick() {
        underTest.clickOnSettingsButton()

        verify(onSettingsClick).invoke()
    }

    @Test
    fun title_isCorrect() {
        assertThat(underTest.title)
            .isEqualTo(context.getString(R.string.quick_settings_modes_label))
    }

    @Test
    fun subTitle_isEmpty() {
        assertThat(underTest.subTitle).isEmpty()
    }
}
