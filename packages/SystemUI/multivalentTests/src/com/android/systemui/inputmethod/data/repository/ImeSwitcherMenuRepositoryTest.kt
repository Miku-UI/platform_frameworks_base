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

package com.android.systemui.inputmethod.data.repository

import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.testDispatcher
import com.android.systemui.kosmos.testScope
import com.android.systemui.testKosmos
import com.android.systemui.util.settings.fakeGlobalSettings
import com.android.systemui.util.settings.fakeSettings
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock

/** Tests for [ImeSwitcherMenuRepository]. */
@SmallTest
@RunWith(AndroidJUnit4::class)
class ImeSwitcherMenuRepositoryTest : SysuiTestCase() {

    private val kosmos = testKosmos()
    private val testScope = kosmos.testScope
    private val fakeSettings = kosmos.fakeSettings
    private val fakeGlobalSettings = kosmos.fakeGlobalSettings

    private val inputMethodManager = mock<InputMethodManager>()
    private val underTest =
        ImeSwitcherMenuRepositoryImpl(
            secureSettings = fakeSettings,
            globalSettings = fakeGlobalSettings,
            inputMethodManager = inputMethodManager,
            mainExecutor = kosmos.testDispatcher.asExecutor(),
        )

    /**
     * Verifies that shouldShowSettingsButton returns true when device is provisioned and user is
     * set up.
     */
    @Test
    fun shouldShowSettingsButton_deviceProvisionedAndUserSetup_returnsTrue() =
        testScope.runTest {
            fakeGlobalSettings.putInt(Settings.Global.DEVICE_PROVISIONED, 1)
            fakeSettings.putIntForUser(Settings.Secure.USER_SETUP_COMPLETE, 1, USER_ID)

            assertThat(underTest.shouldShowSettingsButton(USER_ID)).isTrue()
        }

    /** Verifies that shouldShowSettingsButton returns false when the device is not provisioned. */
    @Test
    fun shouldShowSettingsButton_deviceNotProvisioned_returnsFalse() =
        testScope.runTest {
            fakeGlobalSettings.putInt(Settings.Global.DEVICE_PROVISIONED, 0)
            fakeSettings.putIntForUser(Settings.Secure.USER_SETUP_COMPLETE, 1, USER_ID)

            assertThat(underTest.shouldShowSettingsButton(USER_ID)).isFalse()
        }

    /** Verifies that shouldShowSettingsButton returns false when the user is not set up. */
    @Test
    fun shouldShowSettingsButton_userNotSetup_returnsFalse() =
        testScope.runTest {
            fakeGlobalSettings.putInt(Settings.Global.DEVICE_PROVISIONED, 1)
            fakeSettings.putIntForUser(Settings.Secure.USER_SETUP_COMPLETE, 0, USER_ID)

            assertThat(underTest.shouldShowSettingsButton(USER_ID)).isFalse()
        }

    companion object {
        private const val USER_ID = 100
    }
}
