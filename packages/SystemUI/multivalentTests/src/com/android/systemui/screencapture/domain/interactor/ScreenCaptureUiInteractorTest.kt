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

import android.content.pm.UserInfo
import android.os.UserHandle
import android.platform.test.flag.junit.SetFlagsRule
import android.testing.TestableLooper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.internal.logging.uiEventLoggerFake
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.mediaprojection.devicepolicy.mockDevicePolicyResolver
import com.android.systemui.screencapture.ScreenCaptureEvent
import com.android.systemui.screencapture.common.shared.model.ScreenCaptureType
import com.android.systemui.screencapture.common.shared.model.ScreenCaptureUiParameters
import com.android.systemui.screencapture.common.shared.model.ScreenCaptureUiSource
import com.android.systemui.screencapture.common.shared.model.ScreenCaptureUiState
import com.android.systemui.testKosmos
import com.android.systemui.user.data.repository.fakeUserRepository
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.`when` as whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
@TestableLooper.RunWithLooper(setAsMainLooper = true)
class ScreenCaptureUiInteractorTest : SysuiTestCase() {

    @get:Rule val setFlagsRule = SetFlagsRule()

    private val kosmos = testKosmos()

    private val underTest by lazy { kosmos.screenCaptureUiInteractor }

    @Test
    fun show_whenScreenCaptureDisabledByPolicy_doesNotUpdateVisibility() =
        kosmos.runTest {
            val uiState by collectLastValue(underTest.uiState(ScreenCaptureType.RECORD))

            whenever(
                    mockDevicePolicyResolver.isScreenCaptureCompletelyDisabled(
                        UserHandle.of(USER_ID)
                    )
                )
                .thenReturn(true)

            val userInfo = UserInfo(USER_ID, "test user", 0)
            fakeUserRepository.setUserInfos(listOf(userInfo))
            fakeUserRepository.setSelectedUserInfo(userInfo)

            underTest.show(ScreenCaptureUiParameters.Record())
            assertThat(uiState).isInstanceOf(ScreenCaptureUiState.Invisible::class.java)
        }

    @Test
    fun show_whenSourceArgumentProvided_logsEvent() =
        kosmos.runTest {
            val source = ScreenCaptureUiSource.QUICK_SETTINGS_TILE

            underTest.show(ScreenCaptureUiParameters.Record(), source)

            assertThat(uiEventLoggerFake.numLogs()).isEqualTo(1)
            val event = ScreenCaptureEvent.SCREEN_CAPTURE_UI_SOURCE_QUICK_SETTINGS
            assertThat(uiEventLoggerFake.eventId(0)).isEqualTo(event.id)
        }

    @Test
    fun show_whenSourceArgumentNotProvided_doesNotLogEvent() =
        kosmos.runTest {
            underTest.show(ScreenCaptureUiParameters.Record())

            assertThat(uiEventLoggerFake.numLogs()).isEqualTo(0)
        }

    @Test
    fun isVisible_returnsTrueWhenVisible() =
        kosmos.runTest {
            assertThat(underTest.isVisible(ScreenCaptureType.RECORD)).isFalse()

            underTest.show(ScreenCaptureUiParameters.Record())

            assertThat(underTest.isVisible(ScreenCaptureType.RECORD)).isTrue()
        }

    companion object {
        private const val USER_ID = 1
    }
}
