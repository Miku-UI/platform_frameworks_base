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

package com.android.systemui.qs.tiles.impl.cell.domain.interactor

import android.annotation.StringRes
import android.content.Context
import android.os.UserHandle
import android.text.Html
import com.android.systemui.common.shared.model.ContentDescription.Companion.loadContentDescription
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.qs.flags.QsSplitInternetTile
import com.android.systemui.qs.tiles.base.domain.interactor.QSTileDataInteractor
import com.android.systemui.qs.tiles.base.domain.model.DataUpdateTrigger
import com.android.systemui.qs.tiles.impl.cell.domain.model.MobileDataTileModel
import com.android.systemui.res.R
import com.android.systemui.statusbar.connectivity.ui.MobileContextProvider
import com.android.systemui.statusbar.pipeline.airplane.domain.interactor.AirplaneModeInteractor
import com.android.systemui.statusbar.pipeline.mobile.NewSatelliteIcon
import com.android.systemui.statusbar.pipeline.mobile.domain.interactor.MobileIconsInteractor
import com.android.systemui.statusbar.pipeline.mobile.domain.model.SignalIconModel
import com.android.systemui.statusbar.pipeline.shared.ConnectivityConstants
import com.android.systemui.user.data.repository.UserRepository
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

@OptIn(ExperimentalCoroutinesApi::class)
class MobileDataTileDataInteractor
@Inject
constructor(
    @Application private val context: Context,
    private val userRepository: UserRepository,
    private val mobileIconsInteractor: MobileIconsInteractor,
    private val airplaneModeInteractor: AirplaneModeInteractor,
    private val mobileContextProvider: MobileContextProvider,
    private val connectivityConstants: ConnectivityConstants,
) : QSTileDataInteractor<MobileDataTileModel> {

    override fun tileData(
        user: UserHandle,
        triggers: Flow<DataUpdateTrigger>,
    ): Flow<MobileDataTileModel> = tileData()

    private val mobileDataContentName: Flow<CharSequence?> =
        mobileIconsInteractor.activeDataIconInteractor.flatMapLatest {
            if (it == null) {
                flowOf(null)
            } else {
                if (NewSatelliteIcon.isEnabled) {
                    combine(it.isRoaming, it.networkTypeIconGroup, it.isNonTerrestrial) {
                        isRoaming,
                        networkTypeIconGroup,
                        isNonTerrestrial ->
                        val mobileContext =
                            mobileContextProvider.getMobileContextForSub(it.subscriptionId, context)
                        val cd = loadString(networkTypeIconGroup.contentDescription, mobileContext)
                        if (isNonTerrestrial) {
                            mobileContext.getString(
                                com.android.internal.R.string.satellite_indicator
                            )
                        } else if (isRoaming) {
                            val roaming = mobileContext.getString(R.string.data_connection_roaming)
                            if (cd != null) {
                                mobileContext.getString(
                                    R.string.mobile_data_text_format,
                                    roaming,
                                    cd,
                                )
                            } else {
                                roaming
                            }
                        } else {
                            cd
                        }
                    }
                } else {
                    combine(it.isRoaming, it.networkTypeIconGroup) { isRoaming, networkTypeIconGroup
                        ->
                        val mobileContext =
                            mobileContextProvider.getMobileContextForSub(it.subscriptionId, context)
                        val cd = loadString(networkTypeIconGroup.contentDescription, mobileContext)
                        if (isRoaming) {
                            val roaming = mobileContext.getString(R.string.data_connection_roaming)
                            if (cd != null) {
                                mobileContext.getString(
                                    R.string.mobile_data_text_format,
                                    roaming,
                                    cd,
                                )
                            } else {
                                roaming
                            }
                        } else {
                            cd
                        }
                    }
                }
            }
        }

    private val mobileDescriptionFlow: Flow<CharSequence?> =
        mobileIconsInteractor.activeDataIconInteractor.flatMapLatest { it ->
            if (it == null) {
                flowOf(null)
            } else {
                it.isDataConnected.flatMapLatest { isConnected ->
                    if (!isConnected) {
                        flowOf(null)
                    } else {
                        combine(it.networkName, it.signalLevelIcon, mobileDataContentName) {
                            networkNameModel,
                            signalIcon,
                            dataContentDescription ->
                            when (signalIcon) {
                                is SignalIconModel.CellularTypeIconModel -> {
                                    mobileDataContentConcat(
                                        networkNameModel.name,
                                        dataContentDescription,
                                    )
                                }
                                is SignalIconModel.Satellite -> {
                                    signalIcon.icon.contentDescription?.loadContentDescription(
                                        context
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

    fun tileData(): Flow<MobileDataTileModel> =
        combine(
            mobileIconsInteractor.defaultDataIconInteractor,
            airplaneModeInteractor.isAirplaneMode,
        ) { default, isAirplaneMode ->
            if (default == null) {
                flowOf(
                    MobileDataTileModel(
                        isSimActive = false,
                        isEnabled = false,
                        isAirplaneModeEnabled = isAirplaneMode,
                    )
                )
            } else {
                combine(default.isDataEnabled, mobileDescriptionFlow, default.networkName) {
                    isDataEnabled,
                    description,
                    defaultName ->
                    MobileDataTileModel(
                        isSimActive = true,
                        isEnabled = isDataEnabled,
                        isAirplaneModeEnabled = isAirplaneMode,
                        secondaryLabel = description ?: defaultName.name,
                    )
                }
            }
        }.flatMapLatest { it }

    private fun mobileDataContentConcat(
        networkName: String?,
        dataContentDescription: CharSequence?,
    ): CharSequence {
        if (dataContentDescription == null) {
            return networkName ?: ""
        }
        if (networkName == null) {
            return Html.fromHtml(dataContentDescription.toString(), 0)
        }

        return Html.fromHtml(
            context.getString(
                R.string.mobile_carrier_text_format,
                networkName,
                dataContentDescription,
            ),
            0,
        )
    }

    private fun loadString(@StringRes resId: Int, context: Context): CharSequence? =
        if (resId != 0) {
            context.getString(resId)
        } else {
            null
        }

    override fun availability(user: UserHandle): Flow<Boolean> {
        return flowOf(isTileSupported() && user.identifier == userRepository.mainUserId)
    }

    fun isTileSupported(): Boolean {
        return QsSplitInternetTile.isEnabled && connectivityConstants.hasDataCapabilities
    }
}
