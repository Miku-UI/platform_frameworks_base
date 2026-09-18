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
import com.android.wm.shell.desktopai.api.TriggerEvent
import com.android.wm.shell.desktopai.api.TriggerEventType
import com.android.wm.shell.sysui.OverviewVisibilityChangeListener
import com.android.wm.shell.sysui.ShellController
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

/**
 * Tests for [OverviewTriggerSource].
 *
 * Build/Install/Run: atest WMShellUnitTests:OverviewTriggerSourceTest
 */
@SmallTest
@RunWith(AndroidTestingRunner::class)
class OverviewTriggerSourceTest : ShellTestCase() {

    private val shellController = mock<ShellController>()
    private lateinit var overviewTriggerSource: OverviewTriggerSource

    @Before
    fun setUp() {
        overviewTriggerSource = OverviewTriggerSource(shellController)
    }

    @Test
    fun start_registersListener() {
        overviewTriggerSource.start {}
        verify(shellController).addOverviewVisibilityChangeListener(any())
    }

    @Test
    fun onOverviewShown_triggersEvent() {
        var receivedEvent: TriggerEvent? = null
        val listenerCaptor = argumentCaptor<OverviewVisibilityChangeListener>()

        overviewTriggerSource.start { event -> receivedEvent = event }
        verify(shellController).addOverviewVisibilityChangeListener(listenerCaptor.capture())

        val displayId = 1
        listenerCaptor.firstValue.onOverviewShown(displayId)

        assertThat(receivedEvent).isNotNull()
        assertThat(receivedEvent?.type).isEqualTo(TriggerEventType.SYSTEM)
        assertThat(receivedEvent?.id).isEqualTo("OVERVIEW_SHOWN")
        assertThat(receivedEvent?.payload).containsEntry("displayId", displayId)
    }

    @Test
    fun onOverviewHidden_triggersEvent() {
        var receivedEvent: TriggerEvent? = null
        val listenerCaptor = argumentCaptor<OverviewVisibilityChangeListener>()

        overviewTriggerSource.start { event -> receivedEvent = event }
        verify(shellController).addOverviewVisibilityChangeListener(listenerCaptor.capture())

        val displayId = 2
        listenerCaptor.firstValue.onOverviewHidden(displayId)

        assertThat(receivedEvent).isNotNull()
        assertThat(receivedEvent?.type).isEqualTo(TriggerEventType.SYSTEM)
        assertThat(receivedEvent?.id).isEqualTo("OVERVIEW_HIDDEN")
        assertThat(receivedEvent?.payload).containsEntry("displayId", displayId)
    }
}
