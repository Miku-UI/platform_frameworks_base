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
import com.android.systemui.SysuiTestCase
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.globalactions.shared.model.GlobalActionType
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.lifecycle.activateIn
import com.android.systemui.testKosmosNew
import com.android.systemui.user.data.repository.fakeUserRepository
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class LogoutGlobalActionViewModelTest : SysuiTestCase() {

    private val kosmos = testKosmosNew()
    private val Kosmos.underTest by
        Kosmos.Fixture {
            logoutGlobalActionViewModelFactory.create().apply { activateIn(testScope) }
        }

    @Before
    fun setup() {
        // Default to disabled for clear test baselines
        kosmos.fakeUserRepository.setPolicyManagerLogoutEnabled(false)
        kosmos.fakeUserRepository.setUserManagerLogoutEnabled(false)
    }

    @Test
    fun state_whenEnabledByPolicy_isVisible() =
        kosmos.runTest {
            // GIVEN logout is enabled via device policy
            fakeUserRepository.setPolicyManagerLogoutEnabled(true)

            val state = underTest.state
            assertThat(state).isInstanceOf(GlobalActionUiState.Visible::class.java)
            val visibleState = state as GlobalActionUiState.Visible
            assertThat(visibleState.key).isEqualTo(GlobalActionType.LOGOUT)
            assertThat(visibleState.textResId).isEqualTo(R.string.global_action_logout)
            assertThat((visibleState.icon as Icon.Resource).resId)
                .isEqualTo(com.android.systemui.res.R.drawable.ic_global_actions_logout)

            // VERIFY it clicks through to the proper repository method
            visibleState.onClick()

            assertThat(fakeUserRepository.logOutWithPolicyManagerCallCount).isEqualTo(1)
        }

    @Test
    fun state_whenEnabledByUserManager_isVisible() =
        kosmos.runTest {
            // GIVEN logout is enabled via user manager
            fakeUserRepository.setUserManagerLogoutEnabled(true)

            val state = underTest.state
            assertThat(state).isInstanceOf(GlobalActionUiState.Visible::class.java)

            // VERIFY it clicks through to the proper repository method
            (state as GlobalActionUiState.Visible).onClick()

            assertThat(fakeUserRepository.logOutWithUserManagerCallCount).isEqualTo(1)
        }

    @Test
    fun state_whenDisabled_isHidden() =
        kosmos.runTest {
            // GIVEN neither policy nor user manager enable logout (set in setup)
            val state = underTest.state
            assertThat(state).isInstanceOf(GlobalActionUiState.Hidden::class.java)
            assertThat(state.key).isEqualTo(GlobalActionType.LOGOUT)
        }
}
