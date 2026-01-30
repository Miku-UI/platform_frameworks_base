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

package com.android.systemui.qs.tiles.base.shared.model

import com.android.systemui.kosmos.Kosmos
import com.android.systemui.qs.tiles.impl.airplane.qsAirplaneModeTileConfig
import com.android.systemui.qs.tiles.impl.alarm.qsAlarmTileConfig
import com.android.systemui.qs.tiles.impl.battery.qsBatterySaverTileConfig
import com.android.systemui.qs.tiles.impl.bluetooth.qsBluetoothTileConfig
import com.android.systemui.qs.tiles.impl.cast.qsCastTileConfig
import com.android.systemui.qs.tiles.impl.colorcorrection.qsColorCorrectionTileConfig
import com.android.systemui.qs.tiles.impl.flashlight.qsFlashlightTileConfig
import com.android.systemui.qs.tiles.impl.internet.qsInternetTileConfig
import com.android.systemui.qs.tiles.impl.inversion.qsColorInversionTileConfig
import com.android.systemui.qs.tiles.impl.uimodenight.qsUiModeNightTileConfig

val Kosmos.fakeQSTileConfigProvider by Kosmos.Fixture { FakeQSTileConfigProvider() }
var Kosmos.qSTileConfigProvider: QSTileConfigProvider by Kosmos.Fixture { fakeQSTileConfigProvider }

fun Kosmos.populateQsTileConfigProvider() {
    with(fakeQSTileConfigProvider) {
        putConfig(qsAirplaneModeTileConfig.tileSpec, qsAirplaneModeTileConfig)
        putConfig(qsAlarmTileConfig.tileSpec, qsAlarmTileConfig)
        putConfig(qsBatterySaverTileConfig.tileSpec, qsBatterySaverTileConfig)
        putConfig(qsColorCorrectionTileConfig.tileSpec, qsColorCorrectionTileConfig)
        putConfig(qsColorInversionTileConfig.tileSpec, qsColorInversionTileConfig)
        putConfig(qsFlashlightTileConfig.tileSpec, qsFlashlightTileConfig)
        putConfig(qsInternetTileConfig.tileSpec, qsInternetTileConfig)
        putConfig(qsUiModeNightTileConfig.tileSpec, qsUiModeNightTileConfig)
        putConfig(qsBluetoothTileConfig.tileSpec, qsBluetoothTileConfig)
        putConfig(qsCastTileConfig.tileSpec, qsCastTileConfig)
    }
}
