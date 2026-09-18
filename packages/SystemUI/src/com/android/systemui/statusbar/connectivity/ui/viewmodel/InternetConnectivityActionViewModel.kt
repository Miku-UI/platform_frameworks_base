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

package com.android.systemui.statusbar.connectivity.ui.viewmodel

import android.os.Handler
import com.android.systemui.CoreStartable
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.plugins.qs.TileDetailsViewModel
import com.android.systemui.qs.flags.QsDetailedView
import com.android.systemui.qs.panels.ui.viewmodel.DetailsViewModel
import com.android.systemui.qs.pipeline.shared.TileSpec
import com.android.systemui.qs.tiles.dialog.InternetDetailsViewModel
import com.android.systemui.qs.tiles.dialog.InternetDialogManager
import com.android.systemui.retail.domain.interactor.RetailModeInteractor
import com.android.systemui.scene.shared.model.TransitionKeys
import com.android.systemui.shade.domain.interactor.ShadeInteractor
import com.android.systemui.shade.domain.interactor.ShadeModeInteractor
import com.android.systemui.statusbar.connectivity.AccessPointController
import com.android.systemui.statusbar.connectivity.ConnectivityModule
import com.android.systemui.statusbar.connectivity.domain.interactor.InternetConnectivityActionInteractor
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * ViewModel for the internet connectivity action. This is a CoreStartable that listens for internet
 * connectivity actions and performs the appropriate UI side effects.
 */
@SysUISingleton
class InternetConnectivityActionViewModel
@Inject
constructor(
    @param:Application private val scope: CoroutineScope,
    @param:Main private val mainHandler: Handler,
    private val interactor: InternetConnectivityActionInteractor,
    private val internetDialogManager: InternetDialogManager,
    private val accessPointController: AccessPointController,
    private val shadeInteractor: ShadeInteractor,
    private val detailsViewModel: DetailsViewModel,
    private val shadeModeInteractor: ShadeModeInteractor,
    private val retailModeInteractor: RetailModeInteractor,
) : CoreStartable {

    override fun start() {
        scope.launch {
            interactor.internetConnectivityActionEvent.collect {
                mainHandler.post { handleInternetConnectivityAction() }
            }
        }
    }

    private fun handleInternetConnectivityAction() {
        if (
            QsDetailedView.isEnabled &&
                shadeModeInteractor.isDualShade &&
                !retailModeInteractor.isInRetailMode
        ) {
            val activeTileDetails: TileDetailsViewModel? = detailsViewModel.activeTileDetails
            if (activeTileDetails is InternetDetailsViewModel) {
                return
            }

            if (activeTileDetails == null) {
                shadeInteractor.expandQuickSettingsShade(
                    ACTION_INTERNET_CONNECTIVITY_LOGGING,
                    TransitionKeys.Instant,
                )
            }
            detailsViewModel.onTileClicked(TileSpec.create(ConnectivityModule.INTERNET_TILE_SPEC))
            return
        }

        internetDialogManager.create(
            true,
            accessPointController.canConfigMobileData(),
            accessPointController.canConfigWifi(),
            null, /* view */
        )
    }

    private companion object {
        const val ACTION_INTERNET_CONNECTIVITY_LOGGING = "ACTION_INTERNET_CONNECTIVITY"
    }
}
