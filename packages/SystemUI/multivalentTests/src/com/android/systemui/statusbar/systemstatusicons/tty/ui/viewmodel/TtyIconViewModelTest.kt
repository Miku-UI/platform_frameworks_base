/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.statusbar.systemstatusicons.tty.ui.viewmodel

import android.telecom.TelecomManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.common.shared.model.ContentDescription
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.lifecycle.activateIn
import com.android.systemui.res.R
import com.android.systemui.statusbar.policy.domain.interactor.fakeTtyStatusInteractor
import com.android.systemui.statusbar.systemstatusicons.flags.EnableSystemStatusIconsInCompose
import com.android.systemui.testKosmos
import com.android.telecom.mockTelecomManager
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.whenever

@SmallTest
@EnableSystemStatusIconsInCompose
@RunWith(AndroidJUnit4::class)
class TtyIconViewModelTest : SysuiTestCase() {

    private val kosmos = testKosmos().useUnconfinedTestDispatcher()
    private val Kosmos.underTest by
        Kosmos.Fixture {
            kosmos.ttyIconViewModelFactory.create(mContext).apply { activateIn(kosmos.testScope) }
        }

    @Test
    fun initialState_iconIsNull_notVisible() =
        kosmos.runTest {
            whenever(mockTelecomManager.currentTtyMode).thenReturn(TelecomManager.TTY_MODE_OFF)

            assertThat(underTest.icon).isNull()
            assertThat(underTest.visible).isFalse()
        }

    @Test
    fun icon_ttyOn_isExpectedIcon() =
        kosmos.runTest {
            assertThat(underTest.icon).isNull()
            setTtyModeEnabled(true)

            val expectedIcon =
                Icon.Resource(
                    resId = R.drawable.stat_sys_tty_mode,
                    contentDescription =
                        ContentDescription.Resource(R.string.accessibility_tty_enabled),
                )
            assertThat(underTest.icon).isEqualTo(expectedIcon)
        }

    @Test
    fun visible_ttyOn_isTrue() =
        kosmos.runTest {
            assertThat(underTest.visible).isFalse()

            setTtyModeEnabled(true)

            assertThat(underTest.visible).isTrue()
        }

    @Test
    fun visible_ttyOnThenOff_isFalse() =
        kosmos.runTest {
            assertThat(underTest.visible).isFalse()

            setTtyModeEnabled(true)
            assertThat(underTest.visible).isTrue()

            setTtyModeEnabled(false)
            assertThat(underTest.visible).isFalse()
        }

    /** Helper function to send a TTY mode change broadcast. */
    private fun Kosmos.setTtyModeEnabled(isEnabled: Boolean) {
        fakeTtyStatusInteractor.isEnabled.value = isEnabled
    }
}
