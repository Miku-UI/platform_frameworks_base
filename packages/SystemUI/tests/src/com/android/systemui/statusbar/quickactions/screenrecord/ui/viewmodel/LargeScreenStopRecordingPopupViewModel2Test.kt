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

package com.android.systemui.statusbar.quickactions.screenrecord.ui.viewmodel

import android.media.projection.StopReason
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.testScope
import com.android.systemui.screencapture.common.shared.model.ScreenCaptureType
import com.android.systemui.screencapture.domain.interactor.ScreenCaptureUiInteractor
import com.android.systemui.screencapture.domain.interactor.screenCaptureUiInteractor
import com.android.systemui.screenrecord.domain.interactor.ScreenRecordingServiceInteractor
import com.android.systemui.screenrecord.domain.interactor.screenRecordingServiceInteractor
import com.android.systemui.testKosmos
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

@OptIn(ExperimentalCoroutinesApi::class)
@SmallTest
@RunWith(AndroidJUnit4::class)
class LargeScreenStopRecordingPopupViewModel2Test : SysuiTestCase() {
    private val kosmos = testKosmos()
    private val testScope = kosmos.testScope
    private val screenRecordingServiceInteractor = mock<ScreenRecordingServiceInteractor>()
    private val screenCaptureUiInteractor = mock<ScreenCaptureUiInteractor>()

    private lateinit var underTest: LargeScreenStopRecordingPopupViewModel2

    @Before
    fun setUp() {
        kosmos.screenRecordingServiceInteractor = screenRecordingServiceInteractor
        kosmos.screenCaptureUiInteractor = screenCaptureUiInteractor
        underTest = kosmos.largeScreenStopRecordingPopupViewModel2Factory.create()
    }

    @Test
    fun dismiss_hidesScreenCaptureUi() =
        testScope.runTest {
            underTest.dismiss()

            verify(screenCaptureUiInteractor).hide(ScreenCaptureType.RECORD)
        }

    @Test
    fun onStopButtonTapped_stopsRecordingAndDismisses() =
        testScope.runTest {
            underTest.onStopButtonTapped()

            verify(screenRecordingServiceInteractor).stopRecording(StopReason.STOP_HOST_APP)
            verify(screenCaptureUiInteractor).hide(ScreenCaptureType.RECORD)
        }
}
