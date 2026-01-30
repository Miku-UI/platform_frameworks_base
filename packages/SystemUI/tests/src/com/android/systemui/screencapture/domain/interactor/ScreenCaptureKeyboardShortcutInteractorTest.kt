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
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.screencapture.common.shared.model.ScreenCaptureType
import com.android.systemui.screencapture.common.shared.model.ScreenCaptureUiState
import com.android.systemui.screencapture.record.largescreen.shared.model.ScreenCaptureRegion
import com.android.systemui.screencapture.record.largescreen.shared.model.ScreenCaptureType as LargeScreenCaptureType
import com.android.systemui.testKosmosNew
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
@EnableFlags(Flags.FLAG_LARGE_SCREEN_SCREENCAPTURE)
class ScreenCaptureKeyboardShortcutInteractorTest : SysuiTestCase() {
    private val kosmos = testKosmosNew()

    private val underTest: ScreenCaptureKeyboardShortcutInteractor by lazy {
        kosmos.screenCaptureKeyboardShortcutInteractor
    }

    @Test
    fun attemptPartialRegionScreenshot_showsScreenCaptureUi() =
        kosmos.runTest {
            val uiState by
                collectLastValue(screenCaptureUiInteractor.uiState(ScreenCaptureType.RECORD))
            assertThat(uiState).isInstanceOf(ScreenCaptureUiState.Invisible::class.java)

            underTest.attemptPartialRegionScreenshot()

            assertThat(uiState).isInstanceOf(ScreenCaptureUiState.Visible::class.java)
        }

    @Test
    fun attemptPartialRegionScreenshot_setsLargeScreenCaptureParameters() =
        kosmos.runTest {
            underTest.attemptPartialRegionScreenshot()
            val uiState by
                collectLastValue(screenCaptureUiInteractor.uiState(ScreenCaptureType.RECORD))

            val largeScreenParams =
                (uiState as ScreenCaptureUiState.Visible).parameters.largeScreenParameters
            assertThat(largeScreenParams).isNotNull()
            assertThat(largeScreenParams?.defaultCaptureType)
                .isEqualTo(LargeScreenCaptureType.SCREENSHOT)
            assertThat(largeScreenParams?.defaultCaptureRegion)
                .isEqualTo(ScreenCaptureRegion.PARTIAL)
        }
}
