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
package com.android.systemui.screencapture

import android.content.Intent
import android.content.applicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.runTest
import com.android.systemui.screencapture.common.shared.model.ScreenCaptureType
import com.android.systemui.screencapture.common.shared.model.ScreenCaptureUiParameters
import com.android.systemui.screencapture.common.shared.model.ScreenCaptureUiSource
import com.android.systemui.screencapture.domain.interactor.screenCaptureUiInteractor
import com.android.systemui.testKosmosNew
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class ScreenCaptureUiReceiverTest : SysuiTestCase() {

    private val kosmos = testKosmosNew()
    private val underTest: ScreenCaptureUiReceiver by lazy { kosmos.screenCaptureUiReceiver }

    @Test
    fun onReceive_showScreenCaptureAction_withParamsAndSource_callsInteractorShow() =
        kosmos.runTest {
            val params = ScreenCaptureUiParameters.Record()
            val source = ScreenCaptureUiSource.QUICK_SETTINGS_TILE
            val intent = ScreenCaptureUiReceiver.showScreenCapture(params, source)

            underTest.onReceive(applicationContext, intent)

            assertThat(screenCaptureUiInteractor.isVisible(ScreenCaptureType.RECORD)).isTrue()
        }

    @Test
    fun onReceive_showScreenCaptureAction_withParamsNoSource_callsInteractorShowWithNullSource() =
        kosmos.runTest {
            val params = ScreenCaptureUiParameters.Record()
            val intent = ScreenCaptureUiReceiver.showScreenCapture(params)

            underTest.onReceive(applicationContext, intent)

            assertThat(screenCaptureUiInteractor.isVisible(ScreenCaptureType.RECORD)).isTrue()
        }

    @Test
    fun onReceive_showScreenCaptureAction_withoutParams_doesNothing() =
        kosmos.runTest {
            val intent =
                Intent(mContext, ScreenCaptureUiReceiver::class.java).apply {
                    action = "com.android.systemui.screencapture.show_screen_capture"
                }

            underTest.onReceive(applicationContext, intent)

            assertThat(screenCaptureUiInteractor.isVisible(ScreenCaptureType.RECORD)).isFalse()
        }

    @Test
    fun onReceive_differentAction_doesNothing() =
        kosmos.runTest {
            val intent = Intent("test.action")

            underTest.onReceive(applicationContext, intent)

            assertThat(screenCaptureUiInteractor.isVisible(ScreenCaptureType.RECORD)).isFalse()
        }
}
