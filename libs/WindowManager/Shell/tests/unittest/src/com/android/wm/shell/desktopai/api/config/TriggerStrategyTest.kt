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

package com.android.wm.shell.desktopai.api.config

import androidx.test.filters.SmallTest
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.desktopai.api.TriggerEvent
import com.android.wm.shell.desktopai.api.TriggerEventType
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/** Test for [TriggerStrategy]. Usage: atest WMShellUnitTests:TriggerStrategyTest */
@SmallTest
@RunWith(JUnit4::class)
class TriggerStrategyTest : ShellTestCase() {

    @Test
    fun testSystemEvent_matches_correctEvent() {
        val strategy = TriggerStrategy.SystemEvent("OVERVIEW_SHOWN", mapOf("displayId" to 0))
        val event = TriggerEvent(TriggerEventType.SYSTEM, "OVERVIEW_SHOWN", mapOf("displayId" to 0))

        assertThat(strategy.matches(event)).isTrue()
    }

    @Test
    fun testSystemEvent_matches_differentType_returnsFalse() {
        val strategy = TriggerStrategy.SystemEvent("OVERVIEW_SHOWN")
        val event = TriggerEvent(TriggerEventType.HOTKEY, "OVERVIEW_SHOWN")

        assertThat(strategy.matches(event)).isFalse()
    }

    @Test
    fun testSystemEvent_matches_differentId_returnsFalse() {
        val strategy = TriggerStrategy.SystemEvent("OVERVIEW_SHOWN")
        val event = TriggerEvent(TriggerEventType.SYSTEM, "OVERVIEW_HIDDEN")

        assertThat(strategy.matches(event)).isFalse()
    }

    @Test
    fun testSystemEvent_matches_missingFilter_returnsFalse() {
        val strategy = TriggerStrategy.SystemEvent("OVERVIEW_SHOWN", mapOf("displayId" to 0))
        val event = TriggerEvent(TriggerEventType.SYSTEM, "OVERVIEW_SHOWN", emptyMap())

        assertThat(strategy.matches(event)).isFalse()
    }

    @Test
    fun testSystemEvent_matches_differentFilterValue_returnsFalse() {
        val strategy = TriggerStrategy.SystemEvent("OVERVIEW_SHOWN", mapOf("displayId" to 0))
        val event = TriggerEvent(TriggerEventType.SYSTEM, "OVERVIEW_SHOWN", mapOf("displayId" to 1))

        assertThat(strategy.matches(event)).isFalse()
    }

    @Test
    fun testSystemEvent_matches_extraPayload_returnsTrue() {
        val strategy = TriggerStrategy.SystemEvent("OVERVIEW_SHOWN", mapOf("displayId" to 0))
        val event =
            TriggerEvent(
                TriggerEventType.SYSTEM,
                "OVERVIEW_SHOWN",
                mapOf("displayId" to 0, "extra" to "data"),
            )

        assertThat(strategy.matches(event)).isTrue()
    }
}
