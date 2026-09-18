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

package com.android.wm.shell.compatui.api.events

import android.testing.AndroidTestingRunner
import androidx.test.filters.SmallTest
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.common.ShellExecutor
import com.android.wm.shell.compatui.api.CompatUIHandler
import com.android.wm.shell.compatui.api.CompatUIInfo
import java.util.Optional
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Tests for [CompatUIHandlerEventSink].
 *
 * Build/Install/Run: atest WMShellUnitTests:CompatUIHandlerEventSinkTest
 */
@RunWith(AndroidTestingRunner::class)
@SmallTest
class CompatUIHandlerEventSinkTest : ShellTestCase() {

    private val compatUIHandler = mock<CompatUIHandler>()
    private val mainExecutor = mock<ShellExecutor>()

    @Test
    fun `test sendEventToCompatUIHandler sends event when handler is present`() {
        // Given an event sink with a present CompatUIHandler
        val eventSink = CompatUIHandlerEventSink(Optional.of(compatUIHandler), mainExecutor)

        // When an event is sent
        val compatUIInfo = mock<CompatUIInfo>()
        eventSink.sendEventToCompatUIHandler(compatUIInfo)

        // Then the event is executed on the main executor
        verify(mainExecutor).execute(any())
    }

    @Test
    fun `test onCompatInfoChanged is called when handler is present`() {
        // Given an event sink with a present CompatUIHandler
        val eventSink = CompatUIHandlerEventSink(Optional.of(compatUIHandler), mainExecutor)
        whenever(mainExecutor.execute(any())).doAnswer {
            val runnable = it.getArgument<Runnable>(0)
            runnable.run()
        }

        // When an event is sent
        val compatUIInfo = mock<CompatUIInfo>()
        eventSink.sendEventToCompatUIHandler(compatUIInfo)

        // Then onCompatInfoChanged is called on the handler
        verify(compatUIHandler).onCompatInfoChanged(compatUIInfo)
    }

    @Test
    fun `test sendEventToCompatUIHandler does nothing when handler is not present`() {
        // Given an event sink with a non-present CompatUIHandler
        val eventSink = CompatUIHandlerEventSink(Optional.empty(), mainExecutor)

        // When an event is sent
        val compatUIInfo = mock<CompatUIInfo>()
        eventSink.sendEventToCompatUIHandler(compatUIInfo)

        // Then nothing is executed on the main executor
        verify(mainExecutor, never()).execute(any())
    }
}
