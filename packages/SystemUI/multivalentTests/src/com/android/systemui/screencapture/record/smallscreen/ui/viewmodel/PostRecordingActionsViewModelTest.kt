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

package com.android.systemui.screencapture.record.smallscreen.ui.viewmodel

import android.net.Uri
import android.view.Display
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.internal.logging.uiEventLoggerFake
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.runTest
import com.android.systemui.screencapture.record.shared.model.ScreenRecordEvent
import com.android.systemui.screenrecord.ui.postRecordingActionsViewModelFactory
import com.android.systemui.testKosmosNew
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock

@SmallTest
@RunWith(AndroidJUnit4::class)
class PostRecordingActionsViewModelTest : SysuiTestCase() {

    private val kosmos = testKosmosNew()
    private val underTest by lazy {
        kosmos.postRecordingActionsViewModelFactory.create(
            videoUri = mock(Uri::class.java),
            displayId = Display.DEFAULT_DISPLAY,
        )
    }

    @Test
    fun new_logsEvent() =
        kosmos.runTest {
            underTest.new()

            assertThat(uiEventLoggerFake.eventId(0))
                .isEqualTo(ScreenRecordEvent.SCREEN_RECORD_POST_RECORDING_NEW.id)
        }

    @Test
    fun edit_logsEvent() =
        kosmos.runTest {
            underTest.edit()

            assertThat(uiEventLoggerFake.eventId(0))
                .isEqualTo(ScreenRecordEvent.SCREEN_RECORD_POST_RECORDING_EDIT.id)
        }

    @Test
    fun share_logsEvent() =
        kosmos.runTest {
            underTest.share()

            assertThat(uiEventLoggerFake.eventId(0))
                .isEqualTo(ScreenRecordEvent.SCREEN_RECORD_POST_RECORDING_SHARE.id)
        }
}
