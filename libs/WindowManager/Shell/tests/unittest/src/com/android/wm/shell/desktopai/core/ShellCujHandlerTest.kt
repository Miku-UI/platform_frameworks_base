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

package com.android.wm.shell.desktopai.core

import android.testing.AndroidTestingRunner
import androidx.test.filters.SmallTest
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.desktopai.api.AggregatedContext
import com.android.wm.shell.desktopai.api.ContextQuery
import com.android.wm.shell.desktopai.api.ContextQueryFactory
import com.android.wm.shell.desktopai.api.IUserContextService
import com.android.wm.shell.desktopai.api.TriggerEvent
import com.android.wm.shell.desktopai.api.TriggerEventType
import com.android.wm.shell.desktopai.api.config.CujConfiguration
import com.android.wm.shell.desktopai.api.config.TriggerStrategy
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/** Tests for [ShellCujHandler]. Usage: atest WMShellUnitTests:ShellCujHandlerTest */
@SmallTest
@RunWith(AndroidTestingRunner::class)
class ShellCujHandlerTest : ShellTestCase() {

    private val contextService = mock<IUserContextService>()
    private lateinit var handler: ShellCujHandler

    @Before
    fun setUp() {
        handler = ShellCujHandler(contextService)
    }

    @Test
    fun handle_callsContextService() {
        val strategy = TriggerStrategy.SystemEvent("TEST_EVENT")
        val contextQuery = ContextQuery()
        val factory = mock<ContextQueryFactory>()
        whenever(factory.create(any())).thenReturn(contextQuery)
        val config = CujConfiguration("TEST_CUJ", strategy, factory, emptyList())
        val event = TriggerEvent(TriggerEventType.SYSTEM, "TEST_EVENT")

        whenever(contextService.getContext(any())).thenReturn(AggregatedContext())

        handler.handle(config, event)

        verify(factory).create(eq(event))
        verify(contextService).getContext(eq(contextQuery))
    }
}
