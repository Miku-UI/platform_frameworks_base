/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.android.systemui.statusbar.policy.vpn.data.repository.impl

import android.content.packageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.connectivityManager
import android.net.vpnManager
import android.os.userManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.internal.net.VpnConfig
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.statusbar.policy.vpn.data.repository.realVpnRepository
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.anyString
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
class VpnRepositoryImplTest : SysuiTestCase() {

    private val kosmos = testKosmos().useUnconfinedTestDispatcher()
    private val underTest by lazy { kosmos.realVpnRepository }

    private val mockNetwork: Network = mock()
    private val mockValidatedCapabilities: NetworkCapabilities =
        mock<NetworkCapabilities>().apply {
            whenever(hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)).thenReturn(true)
        }
    private val mockNonValidatedCapabilities: NetworkCapabilities =
        mock<NetworkCapabilities>().apply {
            whenever(hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)).thenReturn(false)
        }
    private val mockLinkProperties: android.net.LinkProperties =
        mock<android.net.LinkProperties>().apply {
            whenever(interfaceName).thenReturn("test_interface")
        }

    @Before
    fun setUp() {
        whenever(kosmos.userManager.users).thenReturn(listOf(primaryUser))
        whenever(kosmos.userManager.getProfileIdsWithDisabled(primaryUser.id))
            .thenReturn(intArrayOf(primaryUser.id))
        whenever(kosmos.userManager.getEnabledProfileIds(primaryUser.id))
            .thenReturn(intArrayOf(primaryUser.id))

        whenever(kosmos.connectivityManager.allNetworks).thenReturn(arrayOf(mockNetwork))
        whenever(kosmos.connectivityManager.getLinkProperties(mockNetwork))
            .thenReturn(mockLinkProperties)
        whenever(kosmos.connectivityManager.getNetworkCapabilities(mockNetwork))
            .thenReturn(mockNonValidatedCapabilities)

        whenever(kosmos.packageManager.getApplicationInfo(anyString(), anyInt()))
            .thenReturn(nonBrandedAppInfo)
    }

    @Test
    fun isVpnEnabled_whenVpnConnected_isTrue() =
        kosmos.runTest {
            val vpnState by collectLastValue(underTest.vpnState)

            setupVpnConfig(primaryUser.id, testVpnConfig)

            assertThat(vpnState?.isEnabled).isFalse()

            getVpnChangeCallback().onAvailable(mockNetwork)

            assertThat(vpnState?.isEnabled).isTrue()
        }

    @Test
    fun isVpnEnabled_whenVpnDisconnected_isFalse() =
        kosmos.runTest {
            val vpnState by collectLastValue(underTest.vpnState)

            setupVpnConfig(primaryUser.id, testVpnConfig)
            getVpnChangeCallback().onCapabilitiesChanged(mockNetwork, mockValidatedCapabilities)

            assertThat(vpnState?.isEnabled).isTrue()

            whenever(kosmos.vpnManager.getVpnConfig(anyInt())).thenReturn(null)
            getVpnChangeCallback().onCapabilitiesChanged(mockNetwork, mockValidatedCapabilities)

            assertThat(vpnState?.isEnabled).isFalse()
        }

    @Test
    fun isVpnBranded_whenVpnIsBrandedSystemApp_isTrue() =
        kosmos.runTest {
            val vpnState by collectLastValue(underTest.vpnState)

            setupVpnConfig(primaryUser.id, testVpnConfig)
            setupAppInfo(brandedSystemAppInfo)

            getVpnChangeCallback().onCapabilitiesChanged(mockNetwork, mockValidatedCapabilities)

            assertThat(vpnState?.isBranded).isTrue()
        }

    @Test
    fun isVpnBranded_whenVpnIsBrandedNonSystemApp_isFalse() =
        kosmos.runTest {
            val vpnState by collectLastValue(underTest.vpnState)

            setupVpnConfig(primaryUser.id, testVpnConfig)
            setupAppInfo(brandedNonSystemAppInfo) // Use non-system app

            getVpnChangeCallback().onCapabilitiesChanged(mockNetwork, mockValidatedCapabilities)

            assertThat(vpnState?.isBranded).isFalse()
        }

    @Test
    fun isVpnBranded_whenVpnIsNotBranded_isFalse() =
        kosmos.runTest {
            val vpnState by collectLastValue(underTest.vpnState)

            setupVpnConfig(primaryUser.id, testVpnConfig)
            setupAppInfo(nonBrandedAppInfo)

            getVpnChangeCallback().onCapabilitiesChanged(mockNetwork, mockValidatedCapabilities)

            assertThat(vpnState?.isBranded).isFalse()
        }

    @Test
    fun isVpnValidated_whenVpnIsValidated_isTrue() =
        kosmos.runTest {
            val vpnState by collectLastValue(underTest.vpnState)

            setupVpnConfig(primaryUser.id, testVpnConfig)

            val callback = getVpnChangeCallback()
            callback.onLinkPropertiesChanged(mockNetwork, mockLinkProperties)
            callback.onCapabilitiesChanged(mockNetwork, mockValidatedCapabilities)

            assertThat(vpnState?.isValidated).isTrue()
        }

    @Test
    fun isVpnValidated_whenVpnIsNotValidated_isFalse() =
        kosmos.runTest {
            val vpnState by collectLastValue(underTest.vpnState)

            setupVpnConfig(primaryUser.id, testVpnConfig)

            val callback = getVpnChangeCallback()
            callback.onLinkPropertiesChanged(mockNetwork, mockLinkProperties)
            callback.onCapabilitiesChanged(mockNetwork, mockNonValidatedCapabilities)

            assertThat(vpnState?.isValidated).isFalse()
        }

    @Test
    fun isVpnEnabled_whenVpnEnabledForOtherUser_isFalse() =
        kosmos.runTest {
            val vpnState by collectLastValue(underTest.vpnState)
            assertThat(vpnState?.isEnabled).isFalse()

            setupMultipleUsers()
            setupVpnConfig(primaryUser.id, null)
            setupVpnConfig(secondaryUser.id, testVpnConfig)

            getVpnChangeCallback().onAvailable(mockNetwork)

            assertThat(vpnState?.isEnabled).isFalse()
        }

    @Test
    fun isVpnBranded_whenBrandedVpnOnOtherUser_isFalse() =
        kosmos.runTest {
            val vpnState by collectLastValue(underTest.vpnState)

            setupMultipleUsers()
            setupVpnConfig(primaryUser.id, null)
            setupVpnConfig(secondaryUser.id, testVpnConfig.apply { user = secondaryUser.name })
            setupAppInfo(brandedAppInfo)

            getVpnChangeCallback().onCapabilitiesChanged(mockNetwork, mockValidatedCapabilities)

            assertThat(vpnState?.isBranded).isFalse()
        }

    @Test
    fun isVpnValidated_whenVpnOnOtherUserIsValidated_isTrue() =
        kosmos.runTest {
            val vpnState by collectLastValue(underTest.vpnState)

            setupMultipleUsers()
            whenever(kosmos.userManager.getEnabledProfileIds(primaryUser.id))
                .thenReturn(intArrayOf(primaryUser.id, secondaryUser.id))

            setupVpnConfig(primaryUser.id, null)
            setupVpnConfig(secondaryUser.id, testVpnConfig.apply { user = secondaryUser.name })

            val callback = getVpnChangeCallback()
            callback.onLinkPropertiesChanged(mockNetwork, mockLinkProperties)
            callback.onCapabilitiesChanged(mockNetwork, mockValidatedCapabilities)

            assertThat(vpnState?.isValidated).isTrue()
        }

    @Test
    fun isVpnValidated_whenVpnOnOtherUserIsNotValidated_isFalse() =
        kosmos.runTest {
            val vpnState by collectLastValue(underTest.vpnState)

            setupMultipleUsers()
            whenever(kosmos.userManager.getEnabledProfileIds(primaryUser.id))
                .thenReturn(intArrayOf(primaryUser.id, secondaryUser.id))

            setupVpnConfig(primaryUser.id, null)
            setupVpnConfig(secondaryUser.id, testVpnConfig.apply { user = secondaryUser.name })

            val callback = getVpnChangeCallback()
            callback.onLinkPropertiesChanged(mockNetwork, mockLinkProperties)
            callback.onCapabilitiesChanged(mockNetwork, mockNonValidatedCapabilities)

            assertThat(vpnState?.isValidated).isFalse()
        }

    @Test
    fun isVpnValidated_whenNoEnabledProfiles_isTrue() =
        kosmos.runTest {
            val vpnState by collectLastValue(underTest.vpnState)

            setupMultipleUsers()
            whenever(kosmos.userManager.getEnabledProfileIds(primaryUser.id))
                .thenReturn(intArrayOf())

            setupVpnConfig(primaryUser.id, null)
            setupVpnConfig(secondaryUser.id, testVpnConfig.apply { user = secondaryUser.name })

            val callback = getVpnChangeCallback()
            callback.onLinkPropertiesChanged(mockNetwork, mockLinkProperties)
            callback.onCapabilitiesChanged(mockNetwork, mockNonValidatedCapabilities)

            assertThat(vpnState?.isValidated).isTrue()
        }

    @Test
    fun vpnState_combinesAllStates() =
        kosmos.runTest {
            val vpnState by collectLastValue(underTest.vpnState)

            assertThat(vpnState?.isEnabled).isFalse()
            assertThat(vpnState?.isBranded).isFalse()
            assertThat(vpnState?.isValidated).isFalse()

            setupVpnConfig(primaryUser.id, testVpnConfig)
            setupAppInfo(brandedSystemAppInfo)

            val callback = getVpnChangeCallback()
            callback.onAvailable(mockNetwork)
            callback.onLinkPropertiesChanged(mockNetwork, mockLinkProperties)
            callback.onCapabilitiesChanged(mockNetwork, mockValidatedCapabilities)

            assertThat(vpnState?.isEnabled).isTrue()
            assertThat(vpnState?.isBranded).isTrue()
            assertThat(vpnState?.isValidated).isTrue()
        }

    private fun setupVpnConfig(userId: Int, config: VpnConfig?) {
        whenever(kosmos.vpnManager.getVpnConfig(userId)).thenReturn(config)
    }

    private fun setupAppInfo(appInfo: android.content.pm.ApplicationInfo) {
        whenever(kosmos.packageManager.getApplicationInfo(anyString(), anyInt()))
            .thenReturn(appInfo)
    }

    private fun setupMultipleUsers() {
        whenever(kosmos.userManager.users).thenReturn(listOf(primaryUser, secondaryUser))
        whenever(kosmos.userManager.getProfileIdsWithDisabled(primaryUser.id))
            .thenReturn(intArrayOf(primaryUser.id))
        whenever(kosmos.userManager.getEnabledProfileIds(primaryUser.id))
            .thenReturn(intArrayOf(primaryUser.id))
    }

    private fun getVpnChangeCallback(): ConnectivityManager.NetworkCallback {
        val callbackCaptor = argumentCaptor<ConnectivityManager.NetworkCallback>()
        val requestCaptor = argumentCaptor<NetworkRequest>()
        verify(kosmos.connectivityManager, atLeastOnce())
            .registerNetworkCallback(requestCaptor.capture(), callbackCaptor.capture())
        return callbackCaptor.lastValue
    }

    companion object {
        private const val IS_BRANDED_KEY = "com.android.systemui.IS_BRANDED"
        private val primaryUser = android.content.pm.UserInfo(0, "primary", 0)
        private val secondaryUser = android.content.pm.UserInfo(10, "secondary", 0)

        private val brandedAppInfo =
            android.content.pm.ApplicationInfo().apply {
                metaData = android.os.Bundle().apply { putBoolean(IS_BRANDED_KEY, true) }
            }

        private val nonBrandedAppInfo =
            android.content.pm.ApplicationInfo().apply {
                metaData = android.os.Bundle().apply { putBoolean(IS_BRANDED_KEY, false) }
            }

        private val brandedSystemAppInfo =
            android.content.pm.ApplicationInfo().apply {
                flags = android.content.pm.ApplicationInfo.FLAG_SYSTEM
                metaData = android.os.Bundle().apply { putBoolean(IS_BRANDED_KEY, true) }
            }

        private val brandedNonSystemAppInfo =
            android.content.pm.ApplicationInfo().apply {
                flags = 0 // Not a system app
                metaData = android.os.Bundle().apply { putBoolean(IS_BRANDED_KEY, true) }
            }

        private val testVpnConfig =
            VpnConfig().apply {
                user = "testuser"
                interfaze = "test_interface"
            }
    }
}
