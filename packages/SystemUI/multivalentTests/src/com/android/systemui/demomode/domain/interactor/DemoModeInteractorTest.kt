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

package com.android.systemui.demomode.domain.interactor

import android.content.Intent
import android.testing.TestableLooper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.broadcast.broadcastDispatcher
import com.android.systemui.demoModeController
import com.android.systemui.demomode.DemoMode
import com.android.systemui.demomode.DemoMode.ACTION_DEMO
import com.android.systemui.demomode.DemoModeController
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.testKosmosNew
import com.android.systemui.util.settings.fakeGlobalSettings
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@SmallTest
@RunWith(AndroidJUnit4::class)
@TestableLooper.RunWithLooper
class DemoModeInteractorTest : SysuiTestCase() {
    private val kosmos = testKosmosNew()
    private val Kosmos.underTest by
        Kosmos.Fixture {
            // Need to initialize demo mode before creating the instance so that
            // `DemoModeInteractor`'s registering callback would work properly.
            demoModeController.initialize()

            demoModeInteractor
        }

    @Before
    fun setUp() {
        allowTestableLooperAsMainThread()

        kosmos.fakeGlobalSettings.putInt(DemoModeController.DEMO_MODE_ALLOWED, 1)
    }

    @Test
    fun isInDemoMode_updatesValue() =
        kosmos.runTest {
            val isInDemoMode by collectLastValue(underTest.isInDemoMode)

            assertThat(isInDemoMode).isFalse()

            sendDemoCommand(DemoMode.COMMAND_ENTER)
            assertThat(isInDemoMode).isTrue()

            sendDemoCommand(DemoMode.COMMAND_EXIT)
            assertThat(isInDemoMode).isFalse()
        }

    private fun Kosmos.sendDemoCommand(command: String) {
        val intent = Intent(ACTION_DEMO)
        intent.putExtra("command", command)
        broadcastDispatcher.sendIntentToMatchingReceiversOnly(context, intent)
    }
}
