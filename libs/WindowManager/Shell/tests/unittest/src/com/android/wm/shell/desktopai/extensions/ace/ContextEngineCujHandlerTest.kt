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

package com.android.wm.shell.desktopai.extensions.ace

import android.service.personalcontext.PersonalContextManager
import android.service.personalcontext.RenderToken
import android.service.personalcontext.hint.ContextHint
import androidx.test.filters.SmallTest
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.desktopai.api.ContextQuery
import com.android.wm.shell.desktopai.api.TriggerEvent
import com.android.wm.shell.desktopai.api.TriggerEventType
import com.android.wm.shell.desktopai.api.config.CujConfiguration
import com.android.wm.shell.desktopai.api.config.TriggerStrategy
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Tests for [ContextEngineCujHandler]. Usage: atest WMShellUnitTests:ContextEngineCujHandlerTest
 */
@SmallTest
@RunWith(JUnit4::class)
class ContextEngineCujHandlerTest : ShellTestCase() {

    private val personalContextManager = mock<PersonalContextManager>()
    private val hintMapperRegistry = mock<HintMapperRegistry>()
    private lateinit var handler: ContextEngineCujHandler

    @Before
    fun setUp() {
        handler = ContextEngineCujHandler(personalContextManager, hintMapperRegistry)
    }

    @Test
    fun testHandle_reportsHint_whenMappingSucceeds() {
        val event = TriggerEvent(TriggerEventType.SYSTEM, "test_event")
        val config =
            CujConfiguration(
                "test_cuj",
                TriggerStrategy.SystemEvent("test_event"),
                contextQueryFactory = { ContextQuery() },
                emptyList(),
            )
        val mockHint = mock<ContextHint>()
        whenever(hintMapperRegistry.map(event)).thenReturn(mockHint)

        handler.handle(config, event)

        verify(personalContextManager).publishTriggeringHint(listOf(mockHint), null)
    }

    @Test
    fun testHandle_doesNotReportHint_whenMappingFails() {
        val event = TriggerEvent(TriggerEventType.SYSTEM, "test_event")
        val config =
            CujConfiguration(
                "test_cuj",
                TriggerStrategy.SystemEvent("test_event"),
                contextQueryFactory = { ContextQuery() },
                emptyList(),
            )
        whenever(hintMapperRegistry.map(event)).thenReturn(null)

        handler.handle(config, event)

        verify(personalContextManager, never())
            .publishTriggeringHint(any<List<ContextHint>>(), any<List<RenderToken>>())
    }
}
