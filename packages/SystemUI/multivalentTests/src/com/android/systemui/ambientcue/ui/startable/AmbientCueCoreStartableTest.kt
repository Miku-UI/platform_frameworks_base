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

package com.android.systemui.ambientcue.ui.startable

import android.os.UserHandle
import android.platform.test.annotations.EnableFlags
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.Flags
import com.android.systemui.SysuiTestCase
import com.android.systemui.ambientcue.data.repository.ambientCueRepository
import com.android.systemui.ambientcue.data.repository.fake
import com.android.systemui.ambientcue.ui.startable.AmbientCueCoreStartable.Companion.AMBIENT_CUE_OVERLAY_PACKAGE
import com.android.systemui.kosmos.runCurrent
import com.android.systemui.kosmos.runTest
import com.android.systemui.testKosmos
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.verify

@RunWith(AndroidJUnit4::class)
@SmallTest
class AmbientCueCoreStartableTest : SysuiTestCase() {
    private val kosmos = testKosmos()

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_UNDERLAY)
    fun isAmbientCueEnabled_setFalse_disableOverlay() =
        kosmos.runTest {
            ambientCueCoreStartable.start()
            ambientCueRepository.fake.setAmbientCueEnabled(false)
            runCurrent()

            verify(mockOverlayManager)
                .setEnabled(AMBIENT_CUE_OVERLAY_PACKAGE, false, UserHandle.SYSTEM)
        }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_UNDERLAY)
    fun isAmbientCueEnabled_setTrue_enableOverlay() =
        kosmos.runTest {
            ambientCueCoreStartable.start()
            ambientCueRepository.fake.setAmbientCueEnabled(true)
            runCurrent()

            verify(mockOverlayManager)
                .setEnabled(AMBIENT_CUE_OVERLAY_PACKAGE, true, UserHandle.SYSTEM)
        }
}
