/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.systemui.qs.tiles.impl.modes.domain.interactor

import android.app.Flags
import android.content.Context
import android.os.UserHandle
import com.android.app.tracing.coroutines.flow.flowName
import com.android.settingslib.notification.modes.ZenMode
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.keyguard.data.repository.KeyguardRepository
import com.android.systemui.keyguard.shared.model.StatusBarState
import com.android.systemui.qs.tiles.ModesTile
import com.android.systemui.qs.tiles.base.domain.interactor.QSTileDataInteractor
import com.android.systemui.qs.tiles.base.domain.model.DataUpdateTrigger
import com.android.systemui.qs.tiles.impl.modes.domain.model.ModesTileModel
import com.android.systemui.shade.ShadeDisplayAware
import com.android.systemui.shade.domain.interactor.ShadeInteractor
import com.android.systemui.statusbar.policy.domain.interactor.ZenModeInteractor
import com.android.systemui.statusbar.policy.domain.model.ActiveZenModes
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@SysUISingleton
class ModesTileDataInteractor
@Inject
constructor(
    @ShadeDisplayAware val context: Context,
    val zenModeInteractor: ZenModeInteractor,
    private val shadeInteractor: ShadeInteractor,
    private val keyguardRepository: KeyguardRepository,
    @Background val bgDispatcher: CoroutineDispatcher,
    @Background val bgScope: CoroutineScope,
) : QSTileDataInteractor<ModesTileModel> {

    override fun tileData(
        user: UserHandle,
        triggers: Flow<DataUpdateTrigger>,
    ): Flow<ModesTileModel> = tileData()

    private val recentlyDeactivatedModeIds: MutableStateFlow<List<String>> =
        MutableStateFlow(listOf())
    private var waitingToRemoveQuickModeOverride: Job? = null

    /**
     * An adapted version of the base class' [tileData] method for use in an old-style tile.
     *
     * TODO(b/299909989): Remove after the transition.
     */
    fun tileData(): Flow<ModesTileModel> =
        if (Flags.modesUiTileReactivatesLast()) {
            combine(zenModeInteractor.modes, recentlyDeactivatedModeIds) {
                    modes,
                    recentlyDeactivatedModeIds ->
                    buildTileData(modes, recentlyDeactivatedModeIds)
                }
                .flowName("tileData")
                .flowOn(bgDispatcher)
                .distinctUntilChanged()
        } else {
            zenModeInteractor.activeModes
                .map { activeModes -> buildTileDataLegacy(activeModes) }
                .flowName("tileData")
                .flowOn(bgDispatcher)
                .distinctUntilChanged()
        }

    suspend fun getCurrentTileModel(): ModesTileModel =
        if (Flags.modesUiTileReactivatesLast()) {
            buildTileData(zenModeInteractor.modes.value, recentlyDeactivatedModeIds.value)
        } else {
            buildTileDataLegacy(zenModeInteractor.getActiveModes())
        }

    private suspend fun buildTileData(
        modes: List<ZenMode>,
        quickModeOverrides: List<String>,
    ): ModesTileModel {
        val activeModesList =
            modes.filter { mode -> mode.isActive }.sortedWith(ZenMode.PRIORITIZING_COMPARATOR)
        val mainActiveMode = activeModesList.firstOrNull()
        val quickMode = getQuickMode(modes, quickModeOverrides)

        val icon =
            if (mainActiveMode != null) {
                zenModeInteractor.getModeIcon(mainActiveMode)
            } else {
                if (quickMode != null) {
                    zenModeInteractor.getModeIcon(quickMode)
                } else {
                    getDefaultTileIcon()
                }
            }

        return ModesTileModel(
            isActivated = activeModesList.isNotEmpty(),
            activeModes = activeModesList.map { ModesTileModel.ActiveMode(it.id, it.name) },
            icon = icon,
            quickMode = quickMode ?: modes.single { it.isManualDnd },
        )
    }

    /**
     * Calculate the "quick mode" (toggle in the two-target tile), which can be:
     * 1. temporarily, the mode that was last deactivated via this tile (expires),
     * 2. otherwise, the last activated manual mode,
     * 3. otherwise, returns `null`, which means the DND mode will be used.
     */
    private fun getQuickMode(modes: List<ZenMode>, quickModeOverrides: List<String>): ZenMode? {
        val manualModes =
            modes
                .filter { mode -> mode.isManualInvocationAllowed }
                .sortedWith(ZenMode.PRIORITIZING_COMPARATOR)
        val recentlyDeactivatedManualMode =
            manualModes
                .filter { mode -> quickModeOverrides.contains(mode.id) }
                .minByOrNull { mode -> quickModeOverrides.indexOf(mode.id) }
        val lastActivatedManualMode =
            manualModes
                .filter { mode -> mode.lastManualActivation != null }
                .maxByOrNull { it.lastManualActivation!! }

        return recentlyDeactivatedManualMode ?: lastActivatedManualMode
    }

    fun setQuickModeOverride(deactivatedModeIds: List<String>) {
        if (!Flags.modesUiTileReactivatesLast()) {
            return
        }

        waitingToRemoveQuickModeOverride?.cancel()
        waitingToRemoveQuickModeOverride = null

        recentlyDeactivatedModeIds.value = deactivatedModeIds

        // Remember the recently deactivated modes (to use for "quick mode") until shade is closed,
        // then clear it. That way the user can quickly reactivate a mode they just deactivated,
        // but for later usages they will have their most recently activated mode.
        if (deactivatedModeIds.isNotEmpty()) {
            waitingToRemoveQuickModeOverride =
                bgScope.launch {
                    combine(shadeInteractor.isAnyExpanded, keyguardRepository.statusBarState) {
                            isAnyExpanded,
                            statusBarState ->
                            !isAnyExpanded || statusBarState == StatusBarState.KEYGUARD
                        }
                        .distinctUntilChanged()
                        .first { it } // it == shade is closed. stop collecting and continue

                    recentlyDeactivatedModeIds.value = listOf()
                }
        }
    }

    private fun buildTileDataLegacy(activeModes: ActiveZenModes): ModesTileModel {
        return ModesTileModel(
            isActivated = activeModes.isAnyActive(),
            activeModes = activeModes.names.map { ModesTileModel.ActiveMode(null, it) },
            icon = if (activeModes.main != null) activeModes.main.icon else getDefaultTileIcon(),
            quickMode = null,
        )
    }

    private fun getDefaultTileIcon() =
        Icon.Loaded(
            context.getDrawable(ModesTile.ICON_RES_ID)!!,
            contentDescription = null,
            resId = ModesTile.ICON_RES_ID,
        )

    override fun availability(user: UserHandle): Flow<Boolean> = flowOf(true)
}
