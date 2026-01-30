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
package com.android.systemui.shade.domain.interactor

import android.platform.test.annotations.EnableFlags
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.shade.data.repository.fakeShadeDisplaysRepository
import com.android.systemui.shade.shared.flag.ShadeWindowGoesAround
import com.android.systemui.statusbar.phone.SystemUIDialogManager
import com.android.systemui.statusbar.phone.mockSystemUIDialogManager
import com.android.systemui.testKosmos
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify

@RunWith(AndroidJUnit4::class)
@SmallTest
@EnableFlags(ShadeWindowGoesAround.FLAG_NAME)
class ShadeDisplayDialogInteractorTest : SysuiTestCase() {
    private val kosmos = testKosmos().useUnconfinedTestDispatcher()
    private val dialogManager: SystemUIDialogManager = kosmos.mockSystemUIDialogManager
    private val shadeDisplaysRepository = kosmos.fakeShadeDisplaysRepository

    private val underTest = kosmos.shadeDisplayDialogInteractor

    @Before
    fun setup() {
        underTest.start()
    }

    @Test
    fun displayIdChanges_previousOneDismissed() =
        kosmos.testScope.runTest {
            shadeDisplaysRepository.setPendingDisplayId(0)
            shadeDisplaysRepository.setPendingDisplayId(1)

            verify(dialogManager).dismissDialogsForDisplayId(eq(0))

            shadeDisplaysRepository.setPendingDisplayId(2)

            verify(dialogManager).dismissDialogsForDisplayId(eq(1))
        }
}
