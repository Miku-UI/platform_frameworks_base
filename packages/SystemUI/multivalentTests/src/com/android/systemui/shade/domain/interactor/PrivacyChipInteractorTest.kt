/*
 * Copyright (C) 2023 The Android Open Source Project
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

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.testScope
import com.android.systemui.privacy.OngoingPrivacyChip
import com.android.systemui.privacy.privacyDialogController
import com.android.systemui.privacy.privacyDialogControllerV2
import com.android.systemui.shade.data.repository.fakePrivacyChipRepository
import com.android.systemui.testKosmos
import com.android.systemui.util.mockito.any
import com.android.systemui.util.mockito.whenever
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations.initMocks

@SmallTest
@RunWith(AndroidJUnit4::class)
class PrivacyChipInteractorTest : SysuiTestCase() {
    private val kosmos = testKosmos()
    private val testScope = kosmos.testScope
    private val privacyChipRepository = kosmos.fakePrivacyChipRepository
    private val privacyDialogController = kosmos.privacyDialogController
    private val privacyDialogControllerV2 = kosmos.privacyDialogControllerV2
    @Mock private lateinit var privacyChip: OngoingPrivacyChip

    val underTest = kosmos.privacyChipInteractor

    @Before
    fun setUp() {
        initMocks(this)
        whenever(privacyChip.context).thenReturn(this.context)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun onPrivacyChipClicked_safetyCenterEnabled() =
        testScope.runTest {
            privacyChipRepository.setIsSafetyCenterEnabled(true)

            underTest.onPrivacyChipClicked(privacyChip)
            runCurrent()

            verify(privacyDialogControllerV2).showDialog(any(), any())
            verify(privacyDialogController, never()).showDialog(any())
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun onPrivacyChipClicked_safetyCenterDisabled() =
        testScope.runTest {
            privacyChipRepository.setIsSafetyCenterEnabled(false)

            underTest.onPrivacyChipClicked(privacyChip)
            runCurrent()

            verify(privacyDialogController).showDialog(any())
            verify(privacyDialogControllerV2, never()).showDialog(any(), any())
        }
}
