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

package com.android.systemui.dreams.domain.interactor

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.complication.Complication.COMPLICATION_TYPE_TIME
import com.android.systemui.dreams.dreamOverlayStateController
import com.android.systemui.flags.Flags
import com.android.systemui.flags.fake
import com.android.systemui.flags.featureFlagsClassic
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class DreamOverlayInteractorTest : SysuiTestCase() {
    private val kosmos = testKosmos().useUnconfinedTestDispatcher()

    private val Kosmos.underTest by Kosmos.Fixture { dreamOverlayInteractor }

    @Before
    fun setup() {
        kosmos.featureFlagsClassic.fake.set(Flags.ALWAYS_SHOW_HOME_CONTROLS_ON_DREAMS, false)
    }

    @Test
    fun testAvailableTypes() =
        kosmos.runTest {
            val lastValue by collectLastValue(underTest.availableComplicationTypes)

            dreamOverlayStateController.availableComplicationTypes = COMPLICATION_TYPE_TIME

            assertThat(lastValue).isEqualTo(COMPLICATION_TYPE_TIME)
        }
}
