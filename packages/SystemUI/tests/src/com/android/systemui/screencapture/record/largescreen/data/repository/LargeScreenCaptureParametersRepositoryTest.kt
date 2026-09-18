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

package com.android.systemui.screencapture.record.largescreen.data.repository

import android.content.pm.UserInfo
import android.graphics.Rect
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.screencapture.record.largescreen.shared.model.ScreenCaptureRegion
import com.android.systemui.screencapture.record.largescreen.shared.model.ScreenCaptureType
import com.android.systemui.testKosmosNew
import com.android.systemui.user.data.repository.fakeUserRepository
import com.google.common.truth.Truth.assertThat
import java.time.Duration
import java.time.Instant
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class LargeScreenCaptureParametersRepositoryTest : SysuiTestCase() {
    private val kosmos = testKosmosNew()
    private lateinit var underTest: LargeScreenCaptureParametersRepository

    @Before
    fun setUp() {
        kosmos.fakeUserRepository.setUserInfos(listOf(PRIMARY_USER, ANOTHER_USER))
        underTest = kosmos.largeScreenCaptureParametersRepository
    }

    @Test
    fun customSaveLocationUriString_initialValueIsEmpty() =
        kosmos.runTest {
            val initialValue by collectLastValue(underTest.customSaveLocationUriString)
            assertThat(initialValue).isEmpty()
        }

    @Test
    fun isCustomSaveLocationActive_initialValueIsFalse() =
        kosmos.runTest {
            val initialValue by collectLastValue(underTest.isCustomSaveLocationActive)
            assertThat(initialValue).isFalse()
        }

    @Test
    fun updateCustomSaveLocation_updatesUriandActivates() =
        kosmos.runTest {
            val latestUriValue by collectLastValue(underTest.customSaveLocationUriString)
            val latestActiveValue by collectLastValue(underTest.isCustomSaveLocationActive)

            val testUri =
                Uri.parse("content://com.android.externalstorage.documents/tree/primary%3ATest")

            assertThat(latestUriValue).isEmpty()

            underTest.updateCustomSaveLocationUriString(testUri)

            assertThat(latestUriValue).isEqualTo(testUri.toString())
            assertThat(latestActiveValue).isTrue()
        }

    @Test
    fun updateCustomSaveLocation_keepsInitialUriAndInactivates_whenDefaultFolderIsPassed() =
        kosmos.runTest {
            val latestUriValue by collectLastValue(underTest.customSaveLocationUriString)
            val latestActiveValue by collectLastValue(underTest.isCustomSaveLocationActive)

            val initialUri =
                Uri.parse("content://com.android.externalstorage.documents/tree/primary%3ATest")

            underTest.updateCustomSaveLocationUriString(initialUri)
            assertThat(latestUriValue).isEqualTo(initialUri.toString())
            assertThat(latestActiveValue).isTrue()

            val defaultUri =
                Uri.parse(
                    "content://com.android.externalstorage.documents/tree/primary%3APictures%2FScreenshots"
                )

            underTest.updateCustomSaveLocationUriString(defaultUri)

            assertThat(latestUriValue).isEqualTo(initialUri.toString())
            assertThat(latestActiveValue).isFalse()
        }

    @Test
    fun updateCustomSaveLocation_doesNothing_whenInitiallyDefaultFolderIsPassed() =
        kosmos.runTest {
            val latestUriValue by collectLastValue(underTest.customSaveLocationUriString)
            val latestActiveValue by collectLastValue(underTest.isCustomSaveLocationActive)

            val defaultUri =
                Uri.parse(
                    "content://com.android.externalstorage.documents/tree/primary%3APictures%2FScreenshots"
                )

            underTest.updateCustomSaveLocationUriString(defaultUri)

            assertThat(latestUriValue).isEmpty()
            assertThat(latestActiveValue).isFalse()
        }

    @Test
    fun updateIsCustomSaveLocationActive_setsActiveValueFalse() =
        kosmos.runTest {
            val latestActiveValue by collectLastValue(underTest.isCustomSaveLocationActive)

            underTest.updateIsCustomSaveLocationActive(false)

            assertThat(latestActiveValue).isFalse()
        }

    @Test
    fun updateIsCustomSaveLocationActive_setsActiveValueTrue() =
        kosmos.runTest {
            val latestActiveValue by collectLastValue(underTest.isCustomSaveLocationActive)

            underTest.updateIsCustomSaveLocationActive(true)

            assertThat(latestActiveValue).isTrue()
        }

    @Test
    fun customSaveLocationValues_areScopedPerUser() =
        kosmos.runTest {
            val latestUriValue by collectLastValue(underTest.customSaveLocationUriString)
            val latestActiveValue by collectLastValue(underTest.isCustomSaveLocationActive)

            fakeUserRepository.setSelectedUserInfo(PRIMARY_USER)
            assertThat(latestUriValue).isEmpty()
            assertThat(latestActiveValue).isFalse()
            underTest.updateCustomSaveLocationUriString(PRIMARY_USER_URI)
            assertThat(latestUriValue).isEqualTo(PRIMARY_USER_URI.toString())
            assertThat(latestActiveValue).isTrue()

            fakeUserRepository.setSelectedUserInfo(ANOTHER_USER)
            assertThat(latestUriValue).isEmpty()
            assertThat(latestActiveValue).isFalse()
            underTest.updateCustomSaveLocationUriString(ANOTHER_USER_URI)
            assertThat(latestUriValue).isEqualTo(ANOTHER_USER_URI.toString())
            assertThat(latestActiveValue).isTrue()

            fakeUserRepository.setSelectedUserInfo(PRIMARY_USER)
            assertThat(latestUriValue).isEqualTo(PRIMARY_USER_URI.toString())
            assertThat(latestActiveValue).isTrue()
        }

    @Test
    fun getSelectedCaptureType_screenshotByDefault() =
        kosmos.runTest {
            assertThat(underTest.getSelectedCaptureType()).isEqualTo(ScreenCaptureType.SCREENSHOT)
        }

    @Test
    fun getSelectedCaptureType_whenOutdated_returnsDefaultValue() =
        kosmos.runTest {
            underTest.updateSelectedCaptureTypeString(ScreenCaptureType.RECORDING)
            assertThat(underTest.getSelectedCaptureType()).isEqualTo(ScreenCaptureType.RECORDING)

            // Manually change the time of selected options.
            val now = Instant.now()
            val invalidDuration = Duration.ofMinutes(20)
            val outdatedTime = now.minus(invalidDuration)
            underTest.saveSelectedCaptureTypeRegionTime(outdatedTime)

            assertThat(underTest.getSelectedCaptureType()).isEqualTo(ScreenCaptureType.SCREENSHOT)
        }

    @Test
    fun updateSelectedCaptureTypeString_setCaptureType() =
        kosmos.runTest {
            underTest.updateSelectedCaptureTypeString(ScreenCaptureType.RECORDING)
            assertThat(underTest.getSelectedCaptureType()).isEqualTo(ScreenCaptureType.RECORDING)

            underTest.updateSelectedCaptureTypeString(ScreenCaptureType.SCREENSHOT)
            assertThat(underTest.getSelectedCaptureType()).isEqualTo(ScreenCaptureType.SCREENSHOT)
        }

    @Test
    fun getSelectedCaptureRegion_partialByDefault() =
        kosmos.runTest {
            assertThat(underTest.getSelectedCaptureRegion()).isEqualTo(ScreenCaptureRegion.PARTIAL)
        }

    @Test
    fun getSelectedCaptureRegion_whenOutdated_returnsDefaultValue() =
        kosmos.runTest {
            underTest.updateSelectedCaptureRegionString(ScreenCaptureRegion.APP_WINDOW)
            assertThat(underTest.getSelectedCaptureRegion())
                .isEqualTo(ScreenCaptureRegion.APP_WINDOW)

            // Manually change the time of selected options.
            val now = Instant.now()
            val invalidDuration = Duration.ofMinutes(20)
            val outdatedTime = now.minus(invalidDuration)
            underTest.saveSelectedCaptureTypeRegionTime(outdatedTime)

            assertThat(underTest.getSelectedCaptureRegion()).isEqualTo(ScreenCaptureRegion.PARTIAL)
        }

    @Test
    fun updateSelectedCaptureRegionString_setCaptureRegion() =
        kosmos.runTest {
            underTest.updateSelectedCaptureRegionString(ScreenCaptureRegion.PARTIAL)
            assertThat(underTest.getSelectedCaptureRegion()).isEqualTo(ScreenCaptureRegion.PARTIAL)

            underTest.updateSelectedCaptureRegionString(ScreenCaptureRegion.APP_WINDOW)
            assertThat(underTest.getSelectedCaptureRegion())
                .isEqualTo(ScreenCaptureRegion.APP_WINDOW)

            underTest.updateSelectedCaptureRegionString(ScreenCaptureRegion.FULLSCREEN)
            assertThat(underTest.getSelectedCaptureRegion())
                .isEqualTo(ScreenCaptureRegion.FULLSCREEN)
        }

    @Test
    fun updateSelectedCaptureRegionBoxString_setCaptureRegionBox() =
        kosmos.runTest {
            assertThat(underTest.getSelectedCaptureRegionBox()).isNull()

            underTest.updateSelectedCaptureRegionBoxString(Rect(50, 50, 100, 100))
            assertThat(underTest.getSelectedCaptureRegionBox()).isEqualTo(Rect(50, 50, 100, 100))
        }

    @Test
    fun getSelectedCaptureRegionBox_whenOutdated_returnsNull() =
        kosmos.runTest {
            underTest.updateSelectedCaptureRegionBoxString(Rect(50, 50, 100, 100))
            assertThat(underTest.getSelectedCaptureRegionBox()).isEqualTo(Rect(50, 50, 100, 100))

            // Manually change the time of selected options.
            val now = Instant.now()
            val invalidDuration = Duration.ofMinutes(20)
            val outdatedTime = now.minus(invalidDuration)
            underTest.saveSelectedCaptureRegionBoxTime(outdatedTime)

            assertThat(underTest.getSelectedCaptureRegionBox()).isNull()
        }

    companion object {
        private val PRIMARY_USER =
            UserInfo(/* id= */ 0, /* name= */ "primary user", /* flags= */ UserInfo.FLAG_PRIMARY)

        private val ANOTHER_USER = UserInfo(/* id= */ 1, /* name= */ "another user", /* flags= */ 0)

        private val PRIMARY_USER_URI =
            Uri.parse("content://com.android.externalstorage.documents/tree/primary%3AUser1")
        private val ANOTHER_USER_URI =
            Uri.parse("content://com.android.externalstorage.documents/tree/primary%3AUser2")
    }
}
