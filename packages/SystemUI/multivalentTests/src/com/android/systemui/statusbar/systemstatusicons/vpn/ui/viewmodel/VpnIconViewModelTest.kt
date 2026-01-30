/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may not use this file except in compliance with the License.
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

package com.android.systemui.statusbar.systemstatusicons.vpn.ui.viewmodel

import android.content.testableContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.common.shared.model.ContentDescription
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.lifecycle.activateIn
import com.android.systemui.res.R
import com.android.systemui.statusbar.policy.vpn.data.repository.vpnRepository
import com.android.systemui.statusbar.policy.vpn.shared.model.VpnState
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class VpnIconViewModelTest : SysuiTestCase() {

    private val kosmos = testKosmos().useUnconfinedTestDispatcher()
    private val underTest =
        kosmos.vpnIconViewModelFactory.create(kosmos.testableContext).apply {
            activateIn(kosmos.testScope)
        }

    @Test
    fun visible_isFalse_byDefault() = kosmos.runTest { assertThat(underTest.visible).isFalse() }

    @Test
    fun visible_vpnIsEnabled_isTrue() =
        kosmos.runTest {
            vpnRepository.vpnState.value = VpnState(isEnabled = true)

            assertThat(underTest.visible).isTrue()
        }

    @Test
    fun visible_vpnStateChanges_flips() =
        kosmos.runTest {
            assertThat(underTest.visible).isFalse()

            vpnRepository.vpnState.value = VpnState(isEnabled = true)
            assertThat(underTest.visible).isTrue()

            vpnRepository.vpnState.value = VpnState(isEnabled = false)
            assertThat(underTest.visible).isFalse()
        }

    @Test
    fun icon_notVisible_isNull() =
        kosmos.runTest {
            vpnRepository.vpnState.value = VpnState(isEnabled = false)
            assertThat(underTest.icon).isNull()
        }

    @Test
    fun notBranded_validated_hasExpectedIcon() =
        kosmos.runTest {
            vpnRepository.vpnState.value =
                VpnState(isEnabled = true, isBranded = false, isValidated = true)
            assertThat(underTest.icon).isEqualTo(EXPECTED_ICON_STANDARD_VALIDATED)
        }

    @Test
    fun notBranded_notValidated_hasExpectedIcon() =
        kosmos.runTest {
            vpnRepository.vpnState.value =
                VpnState(isEnabled = true, isBranded = false, isValidated = false)
            assertThat(underTest.icon).isEqualTo(EXPECTED_ICON_STANDARD_NOT_VALIDATED)
        }

    @Test
    fun branded_validated_hasExpectedIcon() =
        kosmos.runTest {
            vpnRepository.vpnState.value =
                VpnState(isEnabled = true, isBranded = true, isValidated = true)
            assertThat(underTest.icon).isEqualTo(EXPECTED_ICON_BRANDED_VALIDATED)
        }

    @Test
    fun branded_notValidated_hasExpectedIcon() =
        kosmos.runTest {
            vpnRepository.vpnState.value =
                VpnState(isEnabled = true, isBranded = true, isValidated = false)
            assertThat(underTest.icon).isEqualTo(EXPECTED_ICON_BRANDED_NOT_VALIDATED)
        }

    companion object {
        private val BASE_CONTENT_DESCRIPTION =
            ContentDescription.Resource(R.string.accessibility_vpn_on)

        private val EXPECTED_ICON_STANDARD_VALIDATED =
            Icon.Resource(
                resId = R.drawable.stat_sys_vpn_ic,
                contentDescription = BASE_CONTENT_DESCRIPTION,
            )

        private val EXPECTED_ICON_STANDARD_NOT_VALIDATED =
            Icon.Resource(
                resId = R.drawable.stat_sys_no_internet_vpn_ic,
                contentDescription = BASE_CONTENT_DESCRIPTION,
            )

        private val EXPECTED_ICON_BRANDED_VALIDATED =
            Icon.Resource(
                resId = R.drawable.stat_sys_branded_vpn,
                contentDescription = BASE_CONTENT_DESCRIPTION,
            )

        private val EXPECTED_ICON_BRANDED_NOT_VALIDATED =
            Icon.Resource(
                resId = R.drawable.stat_sys_no_internet_branded_vpn,
                contentDescription = BASE_CONTENT_DESCRIPTION,
            )
    }
}
