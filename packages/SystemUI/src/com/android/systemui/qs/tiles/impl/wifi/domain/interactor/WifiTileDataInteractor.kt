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

package com.android.systemui.qs.tiles.impl.wifi.domain.interactor

import android.content.Context
import android.os.UserHandle
import com.android.systemui.qs.flags.QsSplitInternetTile
import com.android.systemui.qs.tiles.base.domain.interactor.QSTileDataInteractor
import com.android.systemui.qs.tiles.base.domain.model.DataUpdateTrigger
import com.android.systemui.qs.tiles.impl.wifi.domain.model.WifiTileModel
import com.android.systemui.res.R
import com.android.systemui.shade.ShadeDisplayAware
import com.android.systemui.statusbar.pipeline.airplane.data.repository.AirplaneModeRepository
import com.android.systemui.statusbar.pipeline.shared.ui.model.WifiToggleState
import com.android.systemui.statusbar.pipeline.wifi.domain.interactor.WifiInteractor
import com.android.systemui.statusbar.pipeline.wifi.shared.model.WifiNetworkModel
import com.android.systemui.statusbar.pipeline.wifi.ui.model.WifiIcon
import com.android.systemui.statusbar.pipeline.wifi.ui.model.WifiTileIconModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf

/** Observes wifi tile state changes providing the [WifiTileModel]. */
class WifiTileDataInteractor
@Inject
constructor(
    @ShadeDisplayAware private val context: Context,
    airplaneModeRepository: AirplaneModeRepository,
    val wifiInteractor: WifiInteractor,
) : QSTileDataInteractor<WifiTileModel> {

    private val notConnectedDescriptionFlow: Flow<CharSequence> =
        combine(wifiInteractor.areNetworksAvailable, airplaneModeRepository.isAirplaneMode) {
            networksAvailable,
            isAirplaneMode ->
            when {
                isAirplaneMode -> {
                    context.getString(R.string.status_bar_airplane)
                }
                networksAvailable -> {
                    context.getString(R.string.quick_settings_networks_available)
                }
                else -> {
                    context.getString(R.string.quick_settings_networks_unavailable)
                }
            }
        }

    override fun tileData(
        user: UserHandle,
        triggers: Flow<DataUpdateTrigger>,
    ): Flow<WifiTileModel> = tileData()

    fun tileData(): Flow<WifiTileModel> =
        combine(
            wifiInteractor.wifiToggleState,
            wifiInteractor.isEnabled,
            wifiInteractor.wifiNetwork,
            notConnectedDescriptionFlow,
        ) { toggleState, isEnabled, wifiNetwork, notConnectedDescription ->
            if (toggleState == WifiToggleState.Pausing) {
                return@combine WifiTileModel.Inactive(
                    icon = WifiTileIconModel(R.drawable.vd_wifi),
                    secondaryLabel = notConnectedDescription,
                )
            } else if (toggleState == WifiToggleState.Scanning) {
                return@combine WifiTileModel.Active(
                    icon = WifiTileIconModel(R.drawable.ic_wifi_connecting),
                    secondaryLabel = context.getString(R.string.quick_settings_scanning_for_wifi),
                )
            }

            if (!isEnabled) {
                return@combine WifiTileModel.Inactive(
                    icon = WifiTileIconModel(R.drawable.ic_wifi_off),
                    secondaryLabel = null,
                )
            }

            val wifiIcon = WifiIcon.fromModel(wifiNetwork, context, showHotspotInfo = true)
            if (wifiNetwork is WifiNetworkModel.Active && wifiIcon is WifiIcon.Visible) {
                val secondary = removeDoubleQuotes(wifiNetwork.ssid)
                WifiTileModel.Active(
                    icon = WifiTileIconModel(wifiIcon.icon.resId),
                    secondaryLabel = secondary,
                )
            } else {
                WifiTileModel.Inactive(
                    icon = WifiTileIconModel(R.drawable.vd_wifi),
                    secondaryLabel = notConnectedDescription,
                )
            }
        }

    override fun availability(user: UserHandle): Flow<Boolean> = flowOf(isAvailable())

    fun isAvailable(): Boolean = QsSplitInternetTile.isEnabled

    private companion object {
        fun removeDoubleQuotes(string: String?): String? {
            if (string == null) return null
            return if (string.firstOrNull() == '"' && string.lastOrNull() == '"') {
                string.substring(1, string.length - 1)
            } else string
        }
    }
}
