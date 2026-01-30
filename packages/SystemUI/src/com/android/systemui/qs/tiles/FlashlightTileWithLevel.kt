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

package com.android.systemui.qs.tiles

import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.service.quicksettings.Tile
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.coroutineScope
import androidx.lifecycle.repeatOnLifecycle
import com.android.app.tracing.coroutines.runBlockingTraced as runBlocking
import com.android.internal.logging.MetricsLogger
import com.android.systemui.animation.Expandable
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.flashlight.flags.FlashlightStrength
import com.android.systemui.flashlight.shared.model.FlashlightModel
import com.android.systemui.log.DebugLogger.debugLog
import com.android.systemui.plugins.ActivityStarter
import com.android.systemui.plugins.FalsingManager
import com.android.systemui.plugins.qs.QSTile.BooleanState
import com.android.systemui.plugins.statusbar.StatusBarStateController
import com.android.systemui.qs.QSHost
import com.android.systemui.qs.QsEventLogger
import com.android.systemui.qs.asQSTileIcon
import com.android.systemui.qs.logging.QSLogger
import com.android.systemui.qs.tileimpl.QSTileImpl
import com.android.systemui.qs.tiles.base.domain.model.QSTileInput
import com.android.systemui.qs.tiles.base.shared.model.QSTileConfigProvider
import com.android.systemui.qs.tiles.base.shared.model.QSTileState
import com.android.systemui.qs.tiles.base.shared.model.QSTileUserAction
import com.android.systemui.qs.tiles.impl.flashlight.domain.interactor.FlashlightTileDataInteractor
import com.android.systemui.qs.tiles.impl.flashlight.domain.interactor.FlashlightTileUserActionInteractor
import com.android.systemui.qs.tiles.impl.flashlight.ui.mapper.FlashlightTileMapper
import com.android.systemui.res.R
import javax.inject.Inject
import kotlinx.coroutines.launch

/** Quick settings tile: Control flashlight */
class FlashlightTileWithLevel
@Inject
constructor(
    host: QSHost,
    uiEventLogger: QsEventLogger,
    @Background backgroundLooper: Looper,
    @Main mainHandler: Handler,
    falsingManager: FalsingManager,
    metricsLogger: MetricsLogger,
    statusBarStateController: StatusBarStateController,
    activityStarter: ActivityStarter,
    qsLogger: QSLogger,
    qsTileConfigProvider: QSTileConfigProvider,
    private val dataInteractor: FlashlightTileDataInteractor,
    private val userActionInteractor: FlashlightTileUserActionInteractor,
    private val tileMapper: FlashlightTileMapper,
) :
    QSTileImpl<BooleanState>(
        host,
        uiEventLogger,
        backgroundLooper,
        mainHandler,
        falsingManager,
        metricsLogger,
        statusBarStateController,
        activityStarter,
        qsLogger,
    ) {
    private val config = qsTileConfigProvider.getConfig(TILE_SPEC)

    init {
        if (FlashlightStrength.isUnexpectedlyInLegacyMode()) {
            qsLogger.debugLog { "Instantiating FlashlightTileWithLevel when flag is off" }
        } else {
            lifecycle.coroutineScope.launch {
                lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                    dataInteractor.tileData().collect { refreshState(it) }
                }
            }
        }
    }

    override fun newTileState(): BooleanState {
        val state = BooleanState()
        state.handlesLongClick = false
        return state
    }

    override fun handleUserSwitch(newUserId: Int) {}

    override fun isAvailable(): Boolean = dataInteractor.isAvailable()

    override fun getTileLabel() = mContext.getString(config.uiConfig.labelRes)

    override fun handleClick(expandable: Expandable?) {
        handleAction(QSTileUserAction.Click(expandable))
    }

    override fun handleSecondaryClick(expandable: Expandable?) {
        handleAction(QSTileUserAction.ToggleClick(expandable))
    }

    override fun handleLongClick(expandable: Expandable?) {
        handleAction(QSTileUserAction.LongClick(expandable))
    }

    override fun getLongClickIntent() = null

    private fun handleAction(action: QSTileUserAction) = runBlocking {
        val data = dataInteractor.getCurrentTileModel()
        val user = UserHandle.of(currentTileUser)
        val qsTileInput = QSTileInput(user, action, data)
        userActionInteractor.handleInput(qsTileInput)
    }

    @VisibleForTesting
    public override fun handleUpdateState(state: BooleanState?, arg: Any?) {
        val model = if (arg is FlashlightModel) arg else dataInteractor.getCurrentTileModel()

        val tileState = tileMapper.map(config, model)
        state?.apply {
            this.state = tileState.activationState.legacyState
            value = this.state == Tile.STATE_ACTIVE
            icon =
                tileState.icon?.asQSTileIcon()
                    ?: maybeLoadResourceIcon(
                        if (value) R.drawable.qs_flashlight_icon_on
                        else R.drawable.qs_flashlight_icon_off
                    )
            label = tileLabel
            secondaryLabel = tileState.secondaryLabel
            contentDescription = tileState.contentDescription
            expandedAccessibilityClassName = tileState.expandedAccessibilityClassName
            handlesSecondaryClick =
                tileState.supportedActions.contains(QSTileState.UserAction.TOGGLE_CLICK)
        }
    }

    private companion object {
        const val TILE_SPEC: String = "flashlight"
    }
}
