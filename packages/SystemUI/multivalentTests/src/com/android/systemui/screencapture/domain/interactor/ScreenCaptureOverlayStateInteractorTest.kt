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

package com.android.systemui.screencapture.domain.interactor

import android.platform.test.annotations.EnableFlags
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.Flags
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.screencapture.common.data.repository.screenCaptureMarkupRepository
import com.android.systemui.screencapture.record.camera.data.repository.screenRecordCameraRepository
import com.android.systemui.screencapture.record.data.repository.screenCaptureRecordParametersRepository
import com.android.systemui.testKosmosNew
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
@EnableFlags(
    Flags.FLAG_NEW_SCREEN_RECORD_TOOLBAR,
    Flags.FLAG_NEW_SCREEN_RECORD_TOOLBAR_SELFIE,
    Flags.FLAG_NEW_SCREEN_RECORD_TOOLBAR_MARKUP,
)
class ScreenCaptureOverlayStateInteractorTest : SysuiTestCase() {

    private val kosmos: Kosmos = testKosmosNew()
    private val underTest: ScreenCaptureOverlayStateInteractor by lazy {
        kosmos.screenCaptureOverlayStateInteractor
    }

    @Test
    fun cameraConnectedAndEnabled_overlayVisible() =
        kosmos.runTest {
            val isOverlayVisible by collectLastValue(underTest.isVisible)

            screenRecordCameraRepository.connect()
            screenCaptureRecordParametersRepository.shouldShowFrontCamera = true

            assertThat(isOverlayVisible).isTrue()
        }

    @Test
    fun markupEnabled_overlayVisible() =
        kosmos.runTest {
            screenRecordCameraRepository.disconnect()
            val isOverlayVisible by collectLastValue(underTest.isVisible)

            screenCaptureMarkupRepository.setEnabled(true)

            assertThat(isOverlayVisible).isTrue()
        }

    @Test
    fun markupAndCameraNotVisible_overlayInvisible() =
        kosmos.runTest {
            screenRecordCameraRepository.disconnect()
            val isOverlayVisible by collectLastValue(underTest.isVisible)

            screenCaptureMarkupRepository.setEnabled(false)

            assertThat(isOverlayVisible).isFalse()
        }
}
