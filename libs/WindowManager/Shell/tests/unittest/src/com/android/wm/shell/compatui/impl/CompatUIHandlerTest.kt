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

package com.android.wm.shell.compatui.impl

import android.testing.AndroidTestingRunner
import androidx.test.filters.SmallTest
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.compatui.api.CompatUIHandler
import com.android.wm.shell.compatui.api.CompatUIInfo
import com.android.wm.shell.compatui.api.CompatUIRequest
import com.android.wm.shell.compatui.api.append
import java.util.function.Consumer
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

/**
 * Tests for [CompatUIHandler].
 *
 * Build/Install/Run: atest WMShellUnitTests:CompatUIHandlerTest
 */
@RunWith(AndroidTestingRunner::class)
@SmallTest
class CompatUIHandlerTest : ShellTestCase() {

    @Test
    fun `Appended CompatUIHandler have onCompatInfoChanged invoked`() {
        runTestScenario { r ->
            r.invokeOnCompatInfoChanged()

            r.verifyOnCompatInfoChanged(target = r.firstCompatUIHandler)
            r.verifyOnCompatInfoChanged(target = r.secondCompatUIHandler)
        }
    }

    @Test
    fun `Appended CompatUIHandler have sendCompatUIRequest invoked`() {
        runTestScenario { r ->
            r.invokeSendCompatUIRequest()

            r.verifySendCompatUIRequest(target = r.firstCompatUIHandler)
            r.verifySendCompatUIRequest(target = r.secondCompatUIHandler)
        }
    }

    @Test
    fun `Appended CompatUIHandler have setCallback invoked`() {
        runTestScenario { r ->
            r.invokeSetCallback()

            r.verifySetCallback(target = r.firstCompatUIHandler)
            r.verifySetCallback(target = r.secondCompatUIHandler)
        }
    }

    /** Runs a test scenario providing a Robot. */
    fun runTestScenario(consumer: Consumer<AppendCompatUIHandlerRobotTest>) {
        consumer.accept(AppendCompatUIHandlerRobotTest())
    }

    class AppendCompatUIHandlerRobotTest {

        val firstCompatUIHandler = mock<CompatUIHandler>()
        val secondCompatUIHandler = mock<CompatUIHandler>()
        val compatUIInfo = mock<CompatUIInfo>()
        val compatUIRequest = mock<CompatUIRequest>()

        fun invokeOnCompatInfoChanged() {
            appendCompatUIHandler().onCompatInfoChanged(compatUIInfo)
        }

        fun invokeSendCompatUIRequest() {
            appendCompatUIHandler().sendCompatUIRequest(compatUIRequest)
        }

        fun invokeSetCallback() {
            appendCompatUIHandler().setCallback {}
        }

        fun verifyOnCompatInfoChanged(target: CompatUIHandler, times: Int = 1) {
            verify(target, times(times)).onCompatInfoChanged(any())
        }

        fun verifySendCompatUIRequest(target: CompatUIHandler, times: Int = 1) {
            verify(target, times(times)).sendCompatUIRequest(any())
        }

        fun verifySetCallback(target: CompatUIHandler, times: Int = 1) {
            verify(target, times(times)).setCallback(any())
        }

        private fun appendCompatUIHandler() = firstCompatUIHandler.append(secondCompatUIHandler)
    }
}
