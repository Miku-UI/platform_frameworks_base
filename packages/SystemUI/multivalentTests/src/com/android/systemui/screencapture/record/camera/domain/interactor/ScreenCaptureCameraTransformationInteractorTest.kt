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

package com.android.systemui.screencapture.record.camera.domain.interactor

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.runTest
import com.android.systemui.testKosmosNew
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class ScreenCaptureCameraTransformationInteractorTest : SysuiTestCase() {
    private val kosmos = testKosmosNew()

    private val underTest by lazy { kosmos.screenCaptureCameraTransformationInteractor }

    @Test
    fun scale_coercedInBounds() =
        kosmos.runTest {
            underTest.scale = 0.1f
            assertThat(underTest.scale).isEqualTo(0.3f)

            underTest.scale = 5f
            assertThat(underTest.scale).isEqualTo(3f)

            underTest.scale = 1.5f
            assertThat(underTest.scale).isEqualTo(1.5f)
        }
}
