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

package com.android.systemui.bluetooth.qsdialog

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.bluetooth.ui.viewModel.BluetoothDetailsContentViewModel
import com.android.systemui.bluetooth.ui.viewModel.BluetoothDetailsViewModel
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
class BluetoothDetailsViewModelTest : SysuiTestCase() {

    private val onSettingsClick: () -> Unit = mock()
    private val detailsContentViewModel: BluetoothDetailsContentViewModel = mock()

    private val underTest by lazy {
        BluetoothDetailsViewModel(onSettingsClick, detailsContentViewModel)
    }

    @Test
    fun clickOnSettingsButton_invokesOnSettingsClick() {
        underTest.clickOnSettingsButton()

        verify(onSettingsClick).invoke()
    }

    @Test
    fun title_returnsContentViewModelTitle() {
        val title = "Test Title"
        whenever(detailsContentViewModel.title).thenReturn(title)

        assertThat(underTest.title).isEqualTo(title)
    }

    @Test
    fun subTitle_returnsContentViewModelSubTitle() {
        val subTitle = "Test SubTitle"
        whenever(detailsContentViewModel.subTitle).thenReturn(subTitle)

        assertThat(underTest.subTitle).isEqualTo(subTitle)
    }
}
