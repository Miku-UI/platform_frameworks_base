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

package com.android.systemui.deviceentry.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runCurrent
import com.android.systemui.kosmos.runTest
import com.android.systemui.statusbar.phone.keyguardBypassController
import com.android.systemui.testKosmos
import com.android.systemui.util.mockito.withArgCaptor
import com.google.common.truth.Truth.assertThat
import kotlin.test.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
class DeviceEntryBypassRepositoryTest : SysuiTestCase() {

    private val kosmos = testKosmos()
    private val underTest = kosmos.deviceEntryBypassRepositoryImpl

    @Test
    fun isBypassEnabled_enabledInController() =
        kosmos.runTest {
            whenever(keyguardBypassController.isBypassEnabled).thenAnswer { true }
            whenever(keyguardBypassController.bypassEnabled).thenAnswer { true }
            val isBypassEnabled by collectLastValue(underTest.isBypassEnabled)
            runCurrent()
            withArgCaptor {
                    verify(keyguardBypassController).registerOnBypassStateChangedListener(capture())
                }
                .onBypassStateChanged(true)
            assertThat(isBypassEnabled).isTrue()
        }

    @Test
    fun isBypassEnabled_disabledInController() =
        kosmos.runTest {
            whenever(keyguardBypassController.isBypassEnabled).thenAnswer { false }
            whenever(keyguardBypassController.bypassEnabled).thenAnswer { false }
            val isBypassEnabled by collectLastValue(underTest.isBypassEnabled)
            runCurrent()
            withArgCaptor {
                    verify(keyguardBypassController).registerOnBypassStateChangedListener(capture())
                }
                .onBypassStateChanged(false)
            runCurrent()
            assertThat(isBypassEnabled).isFalse()
        }
}
