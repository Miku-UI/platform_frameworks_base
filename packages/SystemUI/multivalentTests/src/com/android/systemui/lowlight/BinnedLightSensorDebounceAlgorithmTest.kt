/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.systemui.lowlight

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

@SmallTest
@RunWith(AndroidJUnit4::class)
class BinnedLightSensorDebounceAlgorithmTest : SysuiTestCase() {
    @Test
    fun testThresholds() {
        val callback: AmbientLightModeMonitor.Callback = mock()
        val underTest = BinnedLightSensorAlgorithm()
        underTest.start(callback)

        underTest.onUpdateLightSensorEvent(BinnedLightSensorAlgorithm.BINNED_THRESHOLD - 1)
        verify(callback).onChange(AmbientLightModeMonitor.AMBIENT_LIGHT_MODE_DARK)
        underTest.onUpdateLightSensorEvent(BinnedLightSensorAlgorithm.BINNED_THRESHOLD + 1)
        verify(callback).onChange(AmbientLightModeMonitor.AMBIENT_LIGHT_MODE_LIGHT)
    }
}
