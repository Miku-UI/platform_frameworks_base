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

import android.service.personalcontext.hint.ContextHint
import androidx.test.filters.SmallTest
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.desktopai.api.TriggerEvent
import com.android.wm.shell.desktopai.api.TriggerEventType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/** Tests for [HintMapperRegistry]. Usage: atest WMShellUnitTests:HintMapperRegistryTest */
@SmallTest
@RunWith(JUnit4::class)
class HintMapperRegistryTest : ShellTestCase() {

    private lateinit var registry: HintMapperRegistry

    @Before
    fun setUp() {
        registry = HintMapperRegistry()
    }

    @Test
    fun testMap_returnsHint_whenMapperIsRegistered() {
        val eventId = "test_event"
        val event = TriggerEvent(TriggerEventType.SYSTEM, eventId)
        val mockHint = mock<ContextHint>()
        val mockMapper = mock<TriggerEventHintMapper>()
        whenever(mockMapper.map(event)).thenReturn(mockHint)

        registry.register(eventId, mockMapper)

        val result = registry.map(event)
        assertEquals(mockHint, result)
    }

    @Test
    fun testMap_returnsNull_whenMapperIsNotRegistered() {
        val event = TriggerEvent(TriggerEventType.SYSTEM, "unknown_event")

        val result = registry.map(event)
        assertNull(result)
    }

    @Test
    fun testRegister_overwritesExistingMapper() {
        val eventId = "test_event"
        val event = TriggerEvent(TriggerEventType.SYSTEM, eventId)
        val mapper1 = mock<TriggerEventHintMapper>()
        val mapper2 = mock<TriggerEventHintMapper>()
        val hint2 = mock<ContextHint>()
        whenever(mapper2.map(event)).thenReturn(hint2)

        registry.register(eventId, mapper1)
        registry.register(eventId, mapper2)

        val result = registry.map(event)
        assertEquals(hint2, result)
    }
}
