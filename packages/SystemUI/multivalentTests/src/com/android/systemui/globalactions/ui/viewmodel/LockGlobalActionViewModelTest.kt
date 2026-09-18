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
import com.android.systemui.authentication.data.repository.fakeAuthenticationRepository
import com.android.systemui.authentication.shared.model.AuthenticationMethodModel
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.deviceentry.domain.interactor.deviceEntryInteractor
import com.android.systemui.globalactions.shared.model.GlobalActionType
import com.android.systemui.globalactions.shared.model.GlobalActionsEvent
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.lifecycle.activateIn
import com.android.systemui.testKosmosNew
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class LockGlobalActionViewModelTest : SysuiTestCase() {

    private val kosmos = testKosmosNew()
    private val Kosmos.underTest by
        Kosmos.Fixture { lockGlobalActionViewModelFactory.create().apply { activateIn(testScope) } }

    @Test
    fun state_whenSecure_isVisible() =
        kosmos.runTest {
            // GIVEN a secure authentication method
            fakeAuthenticationRepository.setAuthenticationMethod(AuthenticationMethodModel.Pin)

            // THEN the action is visible and has correct details
            val state = underTest.state
            assertThat(state).isInstanceOf(GlobalActionUiState.Visible::class.java)

            val visibleState = state as GlobalActionUiState.Visible
            assertThat(visibleState.key).isEqualTo(GlobalActionType.LOCK)
            assertThat(visibleState.textResId).isEqualTo(R.string.global_action_unrestricted_lock)
            assertThat((visibleState.icon as Icon.Resource).resId)
                .isEqualTo(com.android.systemui.res.R.drawable.ic_global_actions_lockdown)
        }

    @Test
    fun state_whenInsecure_isHidden() =
        kosmos.runTest {
            // GIVEN an insecure authentication method
            fakeAuthenticationRepository.setAuthenticationMethod(AuthenticationMethodModel.None)

            // THEN the action is hidden
            val state = underTest.state
            assertThat(state).isInstanceOf(GlobalActionUiState.Hidden::class.java)
            assertThat(state.key).isEqualTo(GlobalActionType.LOCK)
        }

    @Test
    fun onClick_locksDevice() =
        kosmos.runTest {
            fakeAuthenticationRepository.setAuthenticationMethod(AuthenticationMethodModel.Pin)
            val state = underTest.state as GlobalActionUiState.Visible
            val isUnlocked by collectLastValue(deviceEntryInteractor.isUnlocked)

            state.onClick()

            assertThat(isUnlocked).isFalse()
        }

    @Test
    fun onClick_logsUiEvent() =
        kosmos.runTest {
            fakeAuthenticationRepository.setAuthenticationMethod(AuthenticationMethodModel.Pin)
            val state = underTest.state as GlobalActionUiState.Visible

            state.onClick()

            assertThat(uiEventLoggerFake.numLogs()).isEqualTo(1)
            assertThat(uiEventLoggerFake.eventId(0)).isEqualTo(GlobalActionsEvent.GA_LOCK_PRESS.id)
        }
}
