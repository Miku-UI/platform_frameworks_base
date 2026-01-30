/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.statusbar.policy.domain.interactor

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.statusbar.policy.fakeHotspotController
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class HotspotStatusInteractorTest : SysuiTestCase() {

    private val kosmos = testKosmos().useUnconfinedTestDispatcher()

    private val fakeController = kosmos.fakeHotspotController
    private val underTest = kosmos.hotspotStatusInteractor

    @Test
    fun isEnabled_initial_matchesFakeControllerDefaults() =
        kosmos.runTest {
            val enabled by collectLastValue(underTest.isEnabled)
            assertThat(enabled).isEqualTo(fakeController.isHotspotEnabled)
        }

    @Test
    fun isEnabled_updatesOnHotspotChanged() =
        kosmos.runTest {
            val state by collectLastValue(underTest.isEnabled)

            fakeController.isHotspotEnabled = true
            assertThat(state).isEqualTo(true)

            fakeController.isHotspotEnabled = false
            assertThat(state).isEqualTo(false)
        }
}
