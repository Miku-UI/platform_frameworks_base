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

package com.android.systemui.statusbar.quickactions.sharescreen.domain.interactor

import android.Manifest
import android.content.Intent
import android.content.packageManager
import android.content.pm.PackageManager
import android.view.accessibility.accessibilityManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.currentValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.mediaprojection.data.model.MediaProjectionState
import com.android.systemui.mediaprojection.data.repository.fakeMediaProjectionRepository
import com.android.systemui.res.R
import com.android.systemui.screenrecord.data.model.ScreenRecordModel
import com.android.systemui.screenrecord.data.repository.screenRecordRepository
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.eq
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
@SmallTest
@RunWith(AndroidJUnit4::class)
class ShareScreenPrivacyIndicatorInteractorTest : SysuiTestCase() {

    private val kosmos = testKosmos().useUnconfinedTestDispatcher()
    private val Kosmos.underTest by Kosmos.Fixture { kosmos.shareScreenPrivacyIndicatorInteractor }

    @Before
    fun setUp() {
        mContext.orCreateTestableResources.addOverride(
            R.bool.config_largeScreenPrivacyIndicator,
            true,
        )

        // Ensure packages are not treated as casting providers by default
        whenever(kosmos.packageManager.checkPermission(any<String>(), any<String>()))
            .thenReturn(PackageManager.PERMISSION_DENIED)

        kosmos.fakeMediaProjectionRepository.mediaProjectionState.value =
            MediaProjectionState.NotProjecting
        kosmos.screenRecordRepository.screenRecordState.value = ScreenRecordModel.DoingNothing

        kosmos.underTest.start()
    }

    @Test
    fun isChipVisible_initiallyFalse() =
        kosmos.runTest { assertThat(currentValue(underTest.isChipVisible)).isFalse() }

    @Test
    fun announceStoppedSharing_onMediaProjectionStopped_announces() =
        kosmos.runTest {
            whenever(kosmos.accessibilityManager.isEnabled).thenReturn(true)
            underTest.assignSharingInfo(
                ShareScreenPrivacyIndicatorInteractor.SharingType.DISPLAY,
                "Test Display",
            )

            // Start projecting
            kosmos.fakeMediaProjectionRepository.mediaProjectionState.value =
                MediaProjectionState.Projecting.EntireScreen(
                    hostPackage = "test",
                    hostDeviceName = null,
                )

            // Stop projecting
            kosmos.fakeMediaProjectionRepository.mediaProjectionState.value =
                MediaProjectionState.NotProjecting

            verify(kosmos.accessibilityManager).sendAccessibilityEvent(any())
        }

    @Test
    fun announceStoppedSharing_notStarted_noAnnouncement() =
        kosmos.runTest {
            // This test is to verify it doesn't announce if it was never projecting.
            whenever(kosmos.accessibilityManager.isEnabled).thenReturn(true)
            underTest.assignSharingInfo(
                ShareScreenPrivacyIndicatorInteractor.SharingType.APP,
                "Test App",
            )

            // No projection state change to stopped from started.
            kosmos.fakeMediaProjectionRepository.mediaProjectionState.value =
                MediaProjectionState.NotProjecting

            verify(kosmos.accessibilityManager, never()).sendAccessibilityEvent(any())
        }

    @Test
    fun isChipVisible_onMediaProjectionStarted_true() =
        kosmos.runTest {
            val isChipVisible by collectLastValue(underTest.isChipVisible)
            assertThat(isChipVisible).isFalse()

            // Simulate media projection starting.
            kosmos.fakeMediaProjectionRepository.mediaProjectionState.value =
                MediaProjectionState.Projecting.EntireScreen(
                    hostPackage = "test",
                    hostDeviceName = null,
                )
            assertThat(isChipVisible).isTrue()
        }

    @Test
    fun isChipVisible_onMediaProjectionStopped_false() =
        kosmos.runTest {
            // Start with a projecting state
            kosmos.fakeMediaProjectionRepository.mediaProjectionState.value =
                MediaProjectionState.Projecting.EntireScreen(
                    hostPackage = "test",
                    hostDeviceName = null,
                )
            val isChipVisible by collectLastValue(underTest.isChipVisible)
            assertThat(isChipVisible).isTrue()

            // Simulate media projection stopping.
            kosmos.fakeMediaProjectionRepository.mediaProjectionState.value =
                MediaProjectionState.NotProjecting
            assertThat(isChipVisible).isFalse()
        }

    @Test
    fun isChipVisible_largeScreenFlagFalse_alwaysFalse() =
        kosmos.runTest {
            mContext.orCreateTestableResources.addOverride(
                R.bool.config_largeScreenPrivacyIndicator,
                false,
            )
            val isChipVisible by collectLastValue(underTest.isChipVisible)
            assertThat(isChipVisible).isFalse()

            // Simulate media projection starting.
            kosmos.fakeMediaProjectionRepository.mediaProjectionState.value =
                MediaProjectionState.Projecting.EntireScreen(
                    hostPackage = "test",
                    hostDeviceName = null,
                )
            // Chip should still be invisible.
            assertThat(isChipVisible).isFalse()
        }

    @Test
    fun isChipVisible_whileRecording_false() =
        kosmos.runTest {
            val isChipVisible by collectLastValue(underTest.isChipVisible)

            // Simulate media projection starting.
            kosmos.fakeMediaProjectionRepository.mediaProjectionState.value =
                MediaProjectionState.Projecting.EntireScreen(
                    hostPackage = "test",
                    hostDeviceName = null,
                )
            assertThat(isChipVisible).isTrue()

            // Simulate screen recording starting.
            kosmos.screenRecordRepository.screenRecordState.value = ScreenRecordModel.Recording
            assertThat(isChipVisible).isFalse()
        }

    @Test
    fun isChipVisible_systemUIHostPackage_false() =
        kosmos.runTest {
            val isChipVisible by collectLastValue(underTest.isChipVisible)

            // Simulate media projection starting where SystemUI is the host (e.g. recording)
            kosmos.fakeMediaProjectionRepository.mediaProjectionState.value =
                MediaProjectionState.Projecting.EntireScreen(
                    hostPackage = context.packageName,
                    hostDeviceName = null,
                )

            // Even if recording state is DoingNothing (the "ghost" state),
            // the chip should be hidden because it's hosted by SystemUI.
            kosmos.screenRecordRepository.screenRecordState.value = ScreenRecordModel.DoingNothing
            assertThat(isChipVisible).isFalse()
        }

    @Test
    fun isChipVisible_whileCasting_false() =
        kosmos.runTest {
            val isChipVisible by collectLastValue(underTest.isChipVisible)

            // Mock casting capabilities for the host package
            whenever(
                    kosmos.packageManager.checkPermission(
                        eq(Manifest.permission.REMOTE_DISPLAY_PROVIDER),
                        eq("cast.package"),
                    )
                )
                .thenReturn(PackageManager.PERMISSION_GRANTED)
            whenever(kosmos.packageManager.queryIntentActivities(any<Intent>(), any<Int>()))
                .thenReturn(emptyList())

            // Simulate media projection starting with casting package.
            kosmos.fakeMediaProjectionRepository.mediaProjectionState.value =
                MediaProjectionState.Projecting.EntireScreen(
                    hostPackage = "cast.package",
                    hostDeviceName = null,
                )

            assertThat(isChipVisible).isFalse()
        }

    @Test
    fun stopShare_hidesChipAndStopsProjection() =
        kosmos.runTest {
            kosmos.fakeMediaProjectionRepository.mediaProjectionState.value =
                MediaProjectionState.Projecting.EntireScreen(
                    hostPackage = "test",
                    hostDeviceName = null,
                )
            val isChipVisible by collectLastValue(underTest.isChipVisible)
            assertThat(isChipVisible).isTrue()

            underTest.stopShare()

            // Stopping projection is not synchronous, so we need to manually update the state
            // in the fake repository to simulate the projection ending.
            kosmos.fakeMediaProjectionRepository.mediaProjectionState.value =
                MediaProjectionState.NotProjecting

            assertThat(kosmos.fakeMediaProjectionRepository.stopProjectingInvoked).isTrue()
            assertThat(isChipVisible).isFalse()
        }
}
