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

package com.android.systemui.globalactions.ui.viewmodel

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.internal.R
import com.android.internal.logging.uiEventLoggerFake
import com.android.systemui.SysuiTestCase
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.globalactions.globalActionsManager
import com.android.systemui.globalactions.shared.model.GlobalActionType
import com.android.systemui.globalactions.shared.model.GlobalActionsEvent
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.lifecycle.activateIn
import com.android.systemui.testKosmosNew
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.verify

@SmallTest
@RunWith(AndroidJUnit4::class)
class RestartGlobalActionViewModelTest : SysuiTestCase() {

    private val kosmos = testKosmosNew()
    private val Kosmos.underTest by
        Kosmos.Fixture {
            restartGlobalActionViewModelFactory.create().apply { activateIn(testScope) }
        }

    @Test
    fun state_isVisible() =
        kosmos.runTest {
            assertThat(underTest.state).isInstanceOf(GlobalActionUiState.Visible::class.java)
            assertThat(underTest.state.key).isEqualTo(GlobalActionType.RESTART)
            assertThat(underTest.state.textResId).isEqualTo(R.string.global_action_restart)
            assertThat(underTest.state.icon).isInstanceOf(Icon.Resource::class.java)
            assertThat(underTest.state.icon.resId)
                .isEqualTo(com.android.systemui.res.R.drawable.ic_global_actions_restart)
        }

    @Test
    fun onClick_triggersReboot() =
        kosmos.runTest {
            underTest.state.onClick()

            verify(kosmos.globalActionsManager).reboot(false)
        }

    @Test
    fun onClick_logsUiEvent() =
        kosmos.runTest {
            underTest.state.onClick()

            assertThat(uiEventLoggerFake.numLogs()).isEqualTo(1)
            assertThat(uiEventLoggerFake.eventId(0))
                .isEqualTo(GlobalActionsEvent.GA_REBOOT_PRESS.id)
        }

    @Test
    fun onLongClick_triggersSafeModeReboot() =
        kosmos.runTest {
            underTest.state.onLongClick?.invoke()

            verify(kosmos.globalActionsManager).reboot(true)
        }

    @Test
    fun onLongClick_logsUiEvent() =
        kosmos.runTest {
            underTest.state.onLongClick?.invoke()

            assertThat(uiEventLoggerFake.numLogs()).isEqualTo(1)
            assertThat(uiEventLoggerFake.eventId(0))
                .isEqualTo(GlobalActionsEvent.GA_REBOOT_LONG_PRESS.id)
        }
}
