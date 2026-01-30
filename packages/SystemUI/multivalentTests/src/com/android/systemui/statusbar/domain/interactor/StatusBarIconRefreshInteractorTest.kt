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

package com.android.systemui.statusbar.domain.interactor

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.statusbar.core.statusBarIconRefreshInteractor
import com.android.systemui.statusbar.phone.ui.statusBarIconController
import com.android.systemui.statusbar.policy.fakeConfigurationController
import com.android.systemui.testKosmos
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.never

@SmallTest
@RunWith(AndroidJUnit4::class)
class StatusBarIconRefreshInteractorTest : SysuiTestCase() {

    private val kosmos = testKosmos()

    private val fakeConfigurationController = kosmos.fakeConfigurationController
    private val mockIconController = kosmos.statusBarIconController
    private val underTest = kosmos.statusBarIconRefreshInteractor

    @Test
    fun start_propagatesConfigChanges() {
        underTest.start()

        fakeConfigurationController.notifyDensityOrFontScaleChanged()

        verify(mockIconController).refreshIconGroups(any())
    }

    @Test
    fun stop_doesNotPropagatesConfigChanges() {
        underTest.start()
        underTest.stop()

        fakeConfigurationController.notifyDensityOrFontScaleChanged()

        verify(mockIconController, never()).refreshIconGroups(any())
    }
}
