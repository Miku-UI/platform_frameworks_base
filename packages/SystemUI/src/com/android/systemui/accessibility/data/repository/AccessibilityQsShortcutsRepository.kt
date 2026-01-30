/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.systemui.accessibility.data.repository

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.text.TextUtils
import android.util.SparseArray
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.IUserInitializationCompleteCallback
import androidx.annotation.GuardedBy
import com.android.app.tracing.coroutines.asyncTraced as async
import com.android.internal.accessibility.AccessibilityShortcutController
import com.android.server.accessibility.Flags
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.kairos.awaitClose
import com.android.systemui.log.LogBuffer
import com.android.systemui.log.core.LogLevel
import com.android.systemui.log.dagger.QSLog
import com.android.systemui.qs.pipeline.shared.TileSpec
import com.android.systemui.qs.tiles.ColorCorrectionTile
import com.android.systemui.qs.tiles.ColorInversionTile
import com.android.systemui.qs.tiles.FontScalingTile
import com.android.systemui.qs.tiles.HearingDevicesTile
import com.android.systemui.qs.tiles.OneHandedModeTile
import com.android.systemui.qs.tiles.ReduceBrightColorsTile
import com.android.systemui.utils.coroutines.flow.conflatedCallbackFlow
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Provides data related to accessibility quick setting shortcut option. */
interface AccessibilityQsShortcutsRepository {
    /**
     * Observable for the a11y features the user chooses in the Settings app to use the quick
     * setting option.
     */
    fun a11yQsShortcutTargets(userId: Int): SharedFlow<Set<String>>

    /** Notify accessibility manager that there are changes to the accessibility tiles */
    suspend fun notifyAccessibilityManagerTilesChanged(userContext: Context, tiles: List<TileSpec>)
}

@SysUISingleton
class AccessibilityQsShortcutsRepositoryImpl
@Inject
constructor(
    private val manager: AccessibilityManager,
    private val userA11yQsShortcutsRepositoryFactory: UserA11yQsShortcutsRepository.Factory,
    @Background private val backgroundDispatcher: CoroutineDispatcher,
    @Background private val applicationScope: CoroutineScope,
    @QSLog private val logBuffer: LogBuffer,
) : AccessibilityQsShortcutsRepository {
    companion object {
        val TILE_SPEC_TO_COMPONENT_MAPPING =
            mapOf(
                ColorCorrectionTile.TILE_SPEC to
                    AccessibilityShortcutController.DALTONIZER_TILE_COMPONENT_NAME,
                ColorInversionTile.TILE_SPEC to
                    AccessibilityShortcutController.COLOR_INVERSION_TILE_COMPONENT_NAME,
                OneHandedModeTile.TILE_SPEC to
                    AccessibilityShortcutController.ONE_HANDED_TILE_COMPONENT_NAME,
                ReduceBrightColorsTile.TILE_SPEC to
                    AccessibilityShortcutController
                        .REDUCE_BRIGHT_COLORS_TILE_SERVICE_COMPONENT_NAME,
                FontScalingTile.TILE_SPEC to
                    AccessibilityShortcutController.FONT_SIZE_TILE_COMPONENT_NAME,
                HearingDevicesTile.TILE_SPEC to
                    AccessibilityShortcutController.ACCESSIBILITY_HEARING_AIDS_TILE_COMPONENT_NAME,
            )

        const val LOG_TAG = "AccessibilityQsShortcutsRepository"
    }

    private val a11yUserInitializationCompleteState: StateFlow<Int?> =
        conflatedCallbackFlow {
                val callback =
                    object : IUserInitializationCompleteCallback.Stub() {
                        override fun onUserInitializationComplete(userId: Int) {
                            logBuffer.log(
                                LOG_TAG,
                                LogLevel.DEBUG,
                                { int1 = userId },
                                { "onUserInitializationComplete userId = $int1" },
                            )
                            trySend(userId)
                        }
                    }
                manager.registerUserInitializationCompleteCallback(callback)

                awaitClose { manager.unregisterUserInitializationCompleteCallback(callback) }
            }
            .flowOn(backgroundDispatcher)
            .stateIn(
                applicationScope,
                SharingStarted.WhileSubscribed(replayExpirationMillis = 0L),
                null,
            )

    private val pendingExecution: MutableStateFlow<Pair<Context, List<TileSpec>>?> =
        MutableStateFlow(null)

    init {
        if (Flags.notifyQsTileChangedAfterUserInitialization()) {
            applicationScope.launch(context = backgroundDispatcher) {
                a11yUserInitializationCompleteState.collectLatest { a11yUserId ->
                    val (userContext, tiles) = pendingExecution.value ?: return@collectLatest
                    logBuffer.log(
                        LOG_TAG,
                        LogLevel.DEBUG,
                        {
                            int1 = userContext.userId
                            str1 = "$a11yUserId"
                        },
                        {
                            "observedUserInitializationComplete: " +
                                "pendingUserContext:userId $int1, a11yUserId: $str1"
                        },
                    )
                    if (userContext.userId == a11yUserId) {
                        notifyAccessibilityManagerTilesChanged(userContext, tiles)
                    }
                }
            }
        }
    }

    @GuardedBy("userA11yQsShortcutsRepositories")
    private val userA11yQsShortcutsRepositories = SparseArray<UserA11yQsShortcutsRepository>()

    override fun a11yQsShortcutTargets(userId: Int): SharedFlow<Set<String>> {
        return synchronized(userA11yQsShortcutsRepositories) {
            if (userId !in userA11yQsShortcutsRepositories) {
                val userA11yQsShortcutsRepository =
                    userA11yQsShortcutsRepositoryFactory.create(userId)
                userA11yQsShortcutsRepositories.put(userId, userA11yQsShortcutsRepository)
            }
            userA11yQsShortcutsRepositories.get(userId).targets
        }
    }

    @SuppressLint("MissingPermission") // android.permission.STATUS_BAR_SERVICE
    override suspend fun notifyAccessibilityManagerTilesChanged(
        userContext: Context,
        tiles: List<TileSpec>,
    ) {

        logBuffer.log(
            LOG_TAG,
            LogLevel.DEBUG,
            {
                int1 = userContext.userId
                str1 = "$tiles"
            },
            { "notifyAccessibilityManagerTilesChanged(userId= $int1, tiles= $str1" },
        )

        if (Flags.notifyQsTileChangedAfterUserInitialization()) {
            if (tiles.isEmpty()) {
                // There is always at least one tile in the QS Panel.
                return
            }
            if (a11yUserInitializationCompleteState.value != userContext.userId) {
                logBuffer.log(
                    LOG_TAG,
                    LogLevel.DEBUG,
                    {
                        int1 = userContext.userId
                        str1 = "${a11yUserInitializationCompleteState.value}"
                    },
                    {
                        "userNotInitializedYet: pending process. " +
                            "sysUiUserId= $int1, a11yUserId= $str1"
                    },
                )
                pendingExecution.emit(Pair(userContext, tiles))
                return
            }

            pendingExecution.emit(null)
        }
        val newTiles = mutableListOf<ComponentName>()
        val accessibilityTileServices = getAccessibilityTileServices(userContext)
        tiles.forEach { tileSpec ->
            when (tileSpec) {
                is TileSpec.CustomTileSpec -> {
                    if (accessibilityTileServices.contains(tileSpec.componentName)) {
                        newTiles.add(tileSpec.componentName)
                    }
                }

                is TileSpec.PlatformTileSpec -> {
                    if (TILE_SPEC_TO_COMPONENT_MAPPING.containsKey(tileSpec.spec)) {
                        newTiles.add(TILE_SPEC_TO_COMPONENT_MAPPING[tileSpec.spec]!!)
                    }
                }

                TileSpec.Invalid -> {
                    // do nothing
                }
            }
        }

        withContext(backgroundDispatcher) {
            manager.notifyQuickSettingsTilesChanged(userContext.userId, newTiles)
        }
    }

    private suspend fun getAccessibilityTileServices(context: Context): Set<ComponentName> =
        coroutineScope {
            val a11yServiceTileServices: Deferred<Set<ComponentName>> =
                async(context = backgroundDispatcher) {
                    manager.installedAccessibilityServiceList
                        .mapNotNull {
                            val packageName = it.resolveInfo.serviceInfo.packageName
                            val tileServiceClass = it.tileServiceName

                            if (!TextUtils.isEmpty(tileServiceClass)) {
                                ComponentName(packageName, tileServiceClass!!)
                            } else {
                                null
                            }
                        }
                        .toSet()
                }

            val a11yShortcutInfoTileServices: Deferred<Set<ComponentName>> =
                async(context = backgroundDispatcher) {
                    manager
                        .getInstalledAccessibilityShortcutListAsUser(context, context.userId)
                        .mapNotNull {
                            val packageName = it.componentName.packageName
                            val tileServiceClass = it.tileServiceName

                            if (!TextUtils.isEmpty(tileServiceClass)) {
                                ComponentName(packageName, tileServiceClass!!)
                            } else {
                                null
                            }
                        }
                        .toSet()
                }

            a11yServiceTileServices.await() + a11yShortcutInfoTileServices.await()
        }
}
