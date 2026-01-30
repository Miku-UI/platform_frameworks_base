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

package com.android.systemui.ambient.lowlight

import android.os.PowerManager
import android.os.powerManager
import android.platform.test.annotations.EnableFlags
import android.testing.TestableLooper.RunWithLooper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.testKosmos
import kotlin.test.Test
import kotlinx.coroutines.launch
import org.junit.runner.RunWith
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify

@SmallTest
@RunWith(AndroidJUnit4::class)
@RunWithLooper
@EnableFlags(android.os.Flags.FLAG_LOW_LIGHT_DREAM_BEHAVIOR)
class AmbientSuppressionLowLightActionTest : SysuiTestCase() {
    val kosmos = testKosmos().useUnconfinedTestDispatcher()

    @Test
    fun testSuppressionInvokesPowerManager() =
        kosmos.runTest {
            val flags =
                PowerManager.FLAG_AMBIENT_SUPPRESSION_AOD or
                    PowerManager.FLAG_AMBIENT_SUPPRESSION_DREAM
            val suppression = AmbientSuppressionLowLightAction(powerManager = powerManager, flags)
            val job = testScope.backgroundScope.launch { suppression.activate() }

            val tagCaptor = argumentCaptor<String>()

            verify(powerManager).suppressAmbientDisplay(tagCaptor.capture(), eq(flags))

            clearInvocations(powerManager)
            job.cancel()

            verify(powerManager)
                .suppressAmbientDisplay(
                    eq(tagCaptor.lastValue),
                    eq(PowerManager.FLAG_AMBIENT_SUPPRESSION_NONE),
                )
        }
}
