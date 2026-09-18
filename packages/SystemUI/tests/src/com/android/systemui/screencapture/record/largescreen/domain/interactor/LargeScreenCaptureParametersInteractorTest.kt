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

package com.android.systemui.screencapture.record.largescreen.domain.interactor

import android.graphics.Rect
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runCurrent
import com.android.systemui.kosmos.runTest
import com.android.systemui.screencapture.record.largescreen.shared.model.ScreenCaptureRegion
import com.android.systemui.screencapture.record.largescreen.shared.model.ScreenCaptureType
import com.android.systemui.testKosmosNew
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class LargeScreenCaptureParametersInteractorTest : SysuiTestCase() {
    private val kosmos = testKosmosNew()
    private lateinit var underTest: LargeScreenCaptureParametersInteractor

    @Before
    fun setUp() {
        underTest = kosmos.largeScreenCaptureParametersInteractor
    }

    @Test
    fun customSaveLocationUriString_initialValue_isEmpty() =
        kosmos.runTest {
            val initialValue by collectLastValue(underTest.customSaveLocationUriString)
            assertThat(initialValue).isEmpty()
        }

    @Test
    fun isCustomSaveLocationActive_initialValue_isFalse() =
        kosmos.runTest {
            val initialValue by collectLastValue(underTest.isCustomSaveLocationActive)
            assertThat(initialValue).isFalse()
        }

    @Test
    fun setCustomSaveLocation_updatesValues() =
        kosmos.runTest {
            val latestUriValue by collectLastValue(underTest.customSaveLocationUriString)
            val latestActiveValue by collectLastValue(underTest.isCustomSaveLocationActive)
            val testUri =
                Uri.parse("content://com.android.externalstorage.documents/tree/primary%3ATest")

            assertThat(latestUriValue).isEmpty()
            assertThat(latestActiveValue).isFalse()

            underTest.setCustomSaveLocation(testUri)
            runCurrent()

            assertThat(latestUriValue).isEqualTo(testUri.toString())
            assertThat(latestActiveValue).isTrue()
        }

    @Test
    fun updateIsCustomSaveLocationActive_updatesValueTrue() =
        kosmos.runTest {
            val latestActiveValue by collectLastValue(underTest.isCustomSaveLocationActive)

            underTest.setIsCustomSaveLocationActive(true)
            runCurrent()

            assertThat(latestActiveValue).isTrue()
        }

    @Test
    fun updateIsCustomSaveLocationActive_updatesValueFalse() =
        kosmos.runTest {
            val latestActiveValue by collectLastValue(underTest.isCustomSaveLocationActive)

            underTest.setIsCustomSaveLocationActive(false)
            runCurrent()

            assertThat(latestActiveValue).isFalse()
        }

    @Test
    fun setCustomSaveLocation_keepsInitialUriAndInactivates_whenDefaultUri() =
        kosmos.runTest {
            val latestUriValue by collectLastValue(underTest.customSaveLocationUriString)
            val latestActiveValue by collectLastValue(underTest.isCustomSaveLocationActive)
            val initialUri =
                Uri.parse("content://com.android.externalstorage.documents/tree/primary%3ATest")

            underTest.setCustomSaveLocation(initialUri)
            runCurrent()
            assertThat(latestUriValue).isEqualTo(initialUri.toString())
            assertThat(latestActiveValue).isTrue()

            val defaultUri =
                Uri.parse(
                    "content://com.android.externalstorage.documents/tree/primary%3APictures%2FScreenshots"
                )

            underTest.setCustomSaveLocation(defaultUri)
            runCurrent()

            assertThat(latestUriValue).isEqualTo(initialUri.toString())
            assertThat(latestActiveValue).isFalse()
        }

    @Test
    fun setSelectedCaptureType_setAndGetCaptureType() =
        kosmos.runTest {
            underTest.setSelectedCaptureType(ScreenCaptureType.RECORDING)
            assertThat(underTest.getSelectedCaptureType()).isEqualTo(ScreenCaptureType.RECORDING)

            underTest.setSelectedCaptureType(ScreenCaptureType.SCREENSHOT)
            assertThat(underTest.getSelectedCaptureType()).isEqualTo(ScreenCaptureType.SCREENSHOT)
        }

    @Test
    fun setSelectedCaptureRegion_setAndGetCaptureRegion() =
        kosmos.runTest {
            underTest.setSelectedCaptureRegion(ScreenCaptureRegion.PARTIAL)
            assertThat(underTest.getSelectedCaptureRegion()).isEqualTo(ScreenCaptureRegion.PARTIAL)

            underTest.setSelectedCaptureRegion(ScreenCaptureRegion.APP_WINDOW)
            assertThat(underTest.getSelectedCaptureRegion())
                .isEqualTo(ScreenCaptureRegion.APP_WINDOW)

            underTest.setSelectedCaptureRegion(ScreenCaptureRegion.FULLSCREEN)
            assertThat(underTest.getSelectedCaptureRegion())
                .isEqualTo(ScreenCaptureRegion.FULLSCREEN)
        }

    @Test
    fun setSelectedCaptureRegionBox_setAndGetCaptureRegionBox() =
        kosmos.runTest {
            assertThat(underTest.getSelectedCaptureRegionBox()).isNull()

            underTest.setSelectedCaptureRegionBox(Rect(50, 50, 100, 100))
            assertThat(underTest.getSelectedCaptureRegionBox()).isEqualTo(Rect(50, 50, 100, 100))
        }
}
