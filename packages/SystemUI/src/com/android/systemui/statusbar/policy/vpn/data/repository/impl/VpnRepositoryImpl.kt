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

package com.android.systemui.statusbar.policy.vpn.data.repository.impl

import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnManager
import android.os.UserManager
import android.util.SparseArray
import com.android.internal.net.VpnConfig
import com.android.systemui.common.coroutine.ChannelExt.trySendWithFailureLogging
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.kairos.awaitClose
import com.android.systemui.settings.UserTracker
import com.android.systemui.statusbar.policy.vpn.data.repository.VpnRepository
import com.android.systemui.statusbar.policy.vpn.shared.model.VpnState
import com.android.systemui.utils.coroutines.flow.conflatedCallbackFlow
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn

@SuppressLint("MissingPermission")
@SysUISingleton
class VpnRepositoryImpl
@Inject
constructor(
    @Background private val scope: CoroutineScope,
    @Background private val bgDispatcher: CoroutineDispatcher,
    private val connectivityManager: ConnectivityManager,
    private val userManager: UserManager,
    private val userTracker: UserTracker,
    private val vpnManager: VpnManager,
    private val packageManager: PackageManager,
) : VpnRepository {

    /**
     * A single flow that manages all state evolution. Callbacks produce a transform function that
     * is applied by `scan` to atomically update the [VpnInternalState].
     */
    private val internalState: StateFlow<VpnInternalState> =
        conflatedCallbackFlow<(VpnInternalState) -> VpnInternalState> {
                val vpnNetworkRequest =
                    NetworkRequest.Builder()
                        .clearCapabilities()
                        .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
                        .build()

                val callback =
                    object : ConnectivityManager.NetworkCallback() {
                        override fun onAvailable(network: Network) {
                            trySendWithFailureLogging(
                                { state ->
                                    state.copy(vpnState = computeVpnState(state.networkProperties))
                                },
                                TAG,
                                "onAvailable",
                            )
                        }

                        override fun onLost(network: Network) {
                            trySendWithFailureLogging(
                                { state ->
                                    val newProps = state.networkProperties - network
                                    state.copy(
                                        networkProperties = newProps,
                                        vpnState = computeVpnState(newProps),
                                    )
                                },
                                TAG,
                                "onLost",
                            )
                        }

                        override fun onCapabilitiesChanged(
                            network: Network,
                            networkCapabilities: NetworkCapabilities,
                        ) {
                            val validated =
                                networkCapabilities.hasCapability(
                                    NetworkCapabilities.NET_CAPABILITY_VALIDATED
                                )
                            trySendWithFailureLogging(
                                { state ->
                                    val networkProperties = state.networkProperties[network]
                                    val updatedNetwork =
                                        networkProperties?.copy(isValidated = validated)
                                            ?: NetworkProperties(isValidated = validated)
                                    val updatedNetworkProperties =
                                        state.networkProperties + (network to updatedNetwork)
                                    state.copy(
                                        networkProperties = updatedNetworkProperties,
                                        vpnState = computeVpnState(updatedNetworkProperties),
                                    )
                                },
                                TAG,
                                "onCapabilitiesChanged",
                            )
                        }

                        override fun onLinkPropertiesChanged(
                            network: Network,
                            linkProperties: LinkProperties,
                        ) {
                            val interfaceName = linkProperties.interfaceName
                            trySendWithFailureLogging(
                                { state ->
                                    val networkProperties = state.networkProperties[network]
                                    val updatedNetworkProperties =
                                        networkProperties?.copy(interfaceName = interfaceName)
                                            ?: NetworkProperties(interfaceName = interfaceName)

                                    val updatedNetworkPropertiesMap =
                                        state.networkProperties +
                                            (network to updatedNetworkProperties)

                                    state.copy(
                                        networkProperties = updatedNetworkPropertiesMap,
                                        vpnState = computeVpnState(updatedNetworkPropertiesMap),
                                    )
                                },
                                TAG,
                                "onLinkPropertiesChanged",
                            )
                        }
                    }
                connectivityManager.registerNetworkCallback(vpnNetworkRequest, callback)
                awaitClose { connectivityManager.unregisterNetworkCallback(callback) }
            }
            .scan(VpnInternalState()) { state, transform -> transform(state) }
            .flowOn(bgDispatcher)
            .stateIn(scope, SharingStarted.WhileSubscribed(), VpnInternalState())

    override val vpnState: StateFlow<VpnState> =
        internalState
            .map { it.vpnState }
            .distinctUntilChanged()
            .stateIn(scope, SharingStarted.WhileSubscribed(), VpnState())

    private fun computeVpnState(networkProperties: Map<Network, NetworkProperties>): VpnState {
        val vpns = getCurrentVpns()
        val vpnUserId = getVpnUserId(userTracker.userId)
        val profileIds = userManager.getProfileIdsWithDisabled(vpnUserId)

        val isEnabled = profileIds.any { profileId -> vpns.get(profileId) != null }

        val primaryVpnConfig = vpns[vpnUserId]
        val isBranded = primaryVpnConfig.isBranded()
        val isValidated = isVpnValidated(primaryVpnConfig, vpns, networkProperties)

        return VpnState(isEnabled = isEnabled, isBranded = isBranded, isValidated = isValidated)
    }

    /** Internal state holder that contains both the property cache and the final VPN state. */
    private data class VpnInternalState(
        val networkProperties: Map<Network, NetworkProperties> = emptyMap(),
        val vpnState: VpnState = VpnState(),
    )

    /** Data class to cache relevant properties of a VPN network. */
    private data class NetworkProperties(
        val interfaceName: String? = null,
        val isValidated: Boolean = false,
    )

    private fun getVpnUserId(currentUserId: Int): Int {
        val userInfo = userManager.getUserInfo(currentUserId)
        return if (userInfo?.isRestricted == true) {
            userInfo.restrictedProfileParentId
        } else {
            currentUserId
        }
    }

    private fun getCurrentVpns(): SparseArray<VpnConfig> {
        val vpns = SparseArray<VpnConfig>()
        for (user in userManager.users) {
            val vpnConfig = vpnManager.getVpnConfig(user.id)
            if (vpnConfig != null) {
                vpns.put(user.id, vpnConfig)
            }
        }
        return vpns
    }

    private fun isVpnValidated(
        primaryVpnConfig: VpnConfig?,
        currentVpns: SparseArray<VpnConfig>,
        networkProperties: Map<Network, NetworkProperties>,
    ): Boolean {
        if (primaryVpnConfig != null) {
            return primaryVpnConfig.getValidationStatus(networkProperties)
        }

        val vpnUserId = getVpnUserId(userTracker.userId)

        return userManager
            .getEnabledProfileIds(vpnUserId)
            .asSequence()
            .mapNotNull { profileId -> currentVpns.get(profileId) }
            .all { it.getValidationStatus(networkProperties) }
    }

    private fun VpnConfig?.isBranded(): Boolean {
        if (this == null || this.legacy) {
            return false
        }
        try {
            val appInfo = packageManager.getApplicationInfo(this.user, PackageManager.GET_META_DATA)
            if (appInfo.metaData == null || !appInfo.isSystemApp) {
                return false
            }
            return appInfo.metaData.getBoolean("com.android.systemui.IS_BRANDED", false)
        } catch (e: PackageManager.NameNotFoundException) {
            return false
        }
    }

    private fun VpnConfig.getValidationStatus(
        networkProperties: Map<Network, NetworkProperties>
    ): Boolean {
        val properties = networkProperties.values.find { it.interfaceName == this.interfaze }
        // If no matching network is found, consider it validated
        return properties?.isValidated ?: true
    }

    companion object {
        private const val TAG = "VpnRepository"
    }
}
