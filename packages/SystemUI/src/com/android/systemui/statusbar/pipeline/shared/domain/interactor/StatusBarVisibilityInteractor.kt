/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.systemui.statusbar.pipeline.shared.domain.interactor

import com.android.app.tracing.FlowTracing.traceEach
import com.android.app.tracing.TrackGroupUtils
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.desktop.domain.interactor.DesktopInteractor
import com.android.systemui.display.dagger.SystemUIDisplaySubcomponent.DisplayAware
import com.android.systemui.display.dagger.SystemUIDisplaySubcomponent.DisplayId
import com.android.systemui.display.dagger.SystemUIDisplaySubcomponent.PerDisplaySingleton
import com.android.systemui.keyguard.domain.interactor.KeyguardInteractor
import com.android.systemui.keyguard.domain.interactor.KeyguardOcclusionInteractor
import com.android.systemui.keyguard.domain.interactor.KeyguardTransitionInteractor
import com.android.systemui.keyguard.shared.model.Edge
import com.android.systemui.keyguard.shared.model.KeyguardState
import com.android.systemui.log.table.TableLogBufferFactory
import com.android.systemui.log.table.logDiffsForTable
import com.android.systemui.scene.domain.interactor.SceneInteractor
import com.android.systemui.scene.shared.flag.SceneContainerFlag
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.shade.domain.interactor.ShadeDisplaysInteractor
import com.android.systemui.shade.domain.interactor.ShadeInteractor
import com.android.systemui.statusbar.pipeline.shared.StatusBarShowIconsInSecureCamera
import javax.inject.Inject
import javax.inject.Provider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn

@PerDisplaySingleton
class StatusBarVisibilityInteractor
@Inject
constructor(
    @field:DisplayId @DisplayAware private val thisDisplayId: Int,
    @DisplayAware homeStatusBarInteractor: HomeStatusBarInteractor,
    @DisplayAware bgDisplayScope: CoroutineScope,
    keyguardInteractor: KeyguardInteractor,
    keyguardTransitionInteractor: KeyguardTransitionInteractor,
    shadeInteractor: ShadeInteractor,
    shadeDisplaysInteractor: Provider<ShadeDisplaysInteractor>,
    sceneInteractor: SceneInteractor,
    occlusionInteractor: KeyguardOcclusionInteractor,
    desktopInteractor: DesktopInteractor,
    @Background bgDispatcher: CoroutineDispatcher,
    tableLoggerFactory: TableLogBufferFactory,
) {
    private val tableLogger = tableLoggerFactory.getOrCreate(tableLogBufferName(thisDisplayId), 200)

    private val currentKeyguardState: StateFlow<KeyguardState> =
        keyguardTransitionInteractor.currentKeyguardState
            .onEach {
                tableLogger.logChange(
                    columnName = COL_KEYGUARD_STATE,
                    value = it.name,
                    isInitial = false,
                )
            }
            .stateIn(
                bgDisplayScope,
                SharingStarted.WhileSubscribed(),
                initialValue = keyguardTransitionInteractor.currentKeyguardState.value,
            )

    /**
     * Whether the display of this statusbar has the shade window (that is hosting shade container
     * and lockscreen, among other things).
     */
    val isShadeWindowOnThisDisplay: StateFlow<Boolean> =
        shadeDisplaysInteractor
            .get()
            .pendingDisplayId
            .map { shadeDisplayId -> thisDisplayId == shadeDisplayId }
            .stateIn(bgDisplayScope, SharingStarted.WhileSubscribed(), initialValue = false)

    private val isShadeExpandedEnough: StateFlow<Boolean> =
        // Keep the status bar visible while the shade is just starting to open or while a HUN is
        // being dragged on (b/412820391), but otherwise hide it so that the status bar doesn't draw
        // while it can't be seen. See b/394257529#comment24.
        shadeInteractor.anyExpansion
            .map { it >= 0.4 }
            .distinctUntilChanged()
            .logDiffsForTable(
                tableLogBuffer = tableLogger,
                columnName = COL_SHADE_EXPANDED_ENOUGH,
                initialValue = false,
            )
            .stateIn(bgDisplayScope, SharingStarted.WhileSubscribed(), initialValue = false)

    private val isShadeVisibleOnAnyDisplay: StateFlow<Boolean> =
        if (SceneContainerFlag.isEnabled) {
            shadeInteractor.isAnyExpanded
        } else {
            isShadeExpandedEnough
        }

    private val isShadeVisibleOnThisDisplay: Flow<Boolean> =
        combine(isShadeWindowOnThisDisplay, isShadeVisibleOnAnyDisplay) {
            hasShade,
            isShadeVisibleOnAnyDisplay ->
            hasShade && isShadeVisibleOnAnyDisplay
        }

    /**
     * True if the current SysUI state can show the home status bar (aka this status bar), and false
     * if we shouldn't be showing any part of the home status bar.
     */
    private val isHomeScreenStatusBarAllowedLegacy: Flow<Boolean> =
        combine(currentKeyguardState, isShadeVisibleOnThisDisplay) {
                currentKeyguardState,
                isShadeVisibleOnThisDisplay ->
                (currentKeyguardState == KeyguardState.GONE ||
                    currentKeyguardState == KeyguardState.OCCLUDED) && !isShadeVisibleOnThisDisplay
            }
            .distinctUntilChanged()
            .logDiffsForTable(
                tableLogBuffer = tableLogger,
                columnName = COL_ALLOWED_LEGACY,
                initialValue = false,
            )
            .stateIn(bgDisplayScope, SharingStarted.WhileSubscribed(), initialValue = false)

    private val isSceneGone: Flow<Boolean> =
        sceneInteractor.currentScene.map { it == Scenes.Gone }.distinctUntilChanged()

    private val isHomeStatusBarAllowedByScene: Flow<Boolean> =
        combine(
                isSceneGone,
                isShadeVisibleOnAnyDisplay,
                occlusionInteractor.isKeyguardOccluded,
                isShadeWindowOnThisDisplay,
            ) { isSceneGone, isShadeVisibleOnAnyDisplay, isOccluded, isShadeWindowOnThisDisplay ->
                if (isOccluded) {
                    true
                } else if (isShadeWindowOnThisDisplay) {
                    isSceneGone && !isShadeVisibleOnAnyDisplay
                } else {
                    // When the shade is visible on another display,
                    // allow the home status bar on the current display.
                    isSceneGone || isShadeVisibleOnAnyDisplay
                }
            }
            .distinctUntilChanged()
            .logDiffsForTable(
                tableLogBuffer = tableLogger,
                columnName = COL_ALLOWED_BY_SCENE,
                initialValue = false,
            )

    // "Compat" to cover both legacy and Scene container case in one flow.
    private val isHomeStatusBarAllowedCompat: Flow<Boolean> =
        if (SceneContainerFlag.isEnabled) {
            isHomeStatusBarAllowedByScene
        } else {
            isHomeScreenStatusBarAllowedLegacy
        }

    /** Whether keyguard is transitioning from Gone to Dreaming. */
    private val isTransitioningFromGoneToDream: Flow<Boolean> =
        keyguardTransitionInteractor
            .isInTransition(
                Edge.create(from = Scenes.Gone, to = KeyguardState.DREAMING),
                edgeWithoutSceneContainer =
                    Edge.create(from = KeyguardState.GONE, to = KeyguardState.DREAMING),
            )
            .distinctUntilChanged()
            .logDiffsForTable(
                tableLogBuffer = tableLogger,
                columnName = COL_GONE_TO_DREAM,
                initialValue = false,
            )
            .flowOn(bgDispatcher)

    /**
     * True if the status bar should be visible.
     *
     * TODO(b/364360986): Once the is<SomeChildView>Visible flows are fully enabled, we shouldn't
     *   need this flow anymore.
     */
    val isHomeStatusBarAllowed: StateFlow<Boolean> =
        isHomeStatusBarAllowedCompat
            .traceEach(
                TrackGroupUtils.trackGroup(TRACK_GROUP, "isHomeStatusBarAllowed"),
                logcat = true,
            )
            .stateIn(bgDisplayScope, SharingStarted.WhileSubscribed(), initialValue = false)

    val shouldHideStatusBarForSecureCamera: Flow<Boolean> =
        if (StatusBarShowIconsInSecureCamera.isEnabled) {
            homeStatusBarInteractor.shouldHideStatusBarForSecureCamera
        } else {
            keyguardInteractor.isSecureCameraActive
        }

    /** True if the home status bar is showing, and there is no HUN happening */
    val canShowOngoingActivityChips: StateFlow<Boolean> =
        combine(
                isHomeStatusBarAllowed,
                shouldHideStatusBarForSecureCamera,
                desktopInteractor.useDesktopStatusBar,
            ) { isHomeStatusBarAllowed, shouldHideStatusBarForSecureCamera, useDesktopStatusBar ->
                (isHomeStatusBarAllowed && !shouldHideStatusBarForSecureCamera) ||
                    useDesktopStatusBar
            }
            .distinctUntilChanged()
            .traceEach(
                TrackGroupUtils.trackGroup(TRACK_GROUP, "canShowOngoingActivityChips"),
                logcat = true,
            )
            .stateIn(bgDisplayScope, SharingStarted.WhileSubscribed(), initialValue = false)

    val shouldHomeStatusBarBeVisible: StateFlow<Boolean> =
        combine(
                isHomeStatusBarAllowed,
                shouldHideStatusBarForSecureCamera,
                isTransitioningFromGoneToDream,
                keyguardInteractor.isKeyguardVisible,
            ) {
                isHomeStatusBarAllowed,
                shouldHideStatusBarForSecureCamera,
                isGoneToDream,
                isKeyguardVisible ->
                // When launching the camera over the lockscreen, the status icons would typically
                // become visible momentarily before animating out, since we're not yet aware that
                // the launching camera activity is fullscreen. Even once the activity finishes
                // launching, it takes a short time before WM decides that the top app wants to hide
                // the icons and tells us to hide them. To ensure that this high-visibility
                // animation is smooth, keep the icons hidden during a camera launch. See
                // b/257292822.
                // Similar to launching the camera: when dream is launched, the icons are
                // momentarily visible because the dream animation has finished, but SysUI has not
                // been informed that the dream is full-screen. See b/273314977.
                isHomeStatusBarAllowed &&
                    !shouldHideStatusBarForSecureCamera &&
                    !isGoneToDream &&
                    // In legacy code, check if keyguard is visible to cover canceled
                    // transitions. In Flexi, the scene state is enough to cover this case.
                    if (!SceneContainerFlag.isEnabled) !isKeyguardVisible else true
            }
            .distinctUntilChanged()
            .logDiffsForTable(
                tableLogBuffer = tableLogger,
                columnName = COL_VISIBLE,
                initialValue = false,
            )
            .stateIn(
                bgDisplayScope,
                SharingStarted.Companion.WhileSubscribed(),
                initialValue = false,
            )

    private companion object {
        private const val COL_ALLOWED_BY_SCENE = "allowedByScene"
        const val COL_ALLOWED_LEGACY = "allowedLegacy"
        const val COL_GONE_TO_DREAM = "Gone->Dreaming"
        const val COL_KEYGUARD_STATE = "keyguardState"
        const val COL_SHADE_EXPANDED_ENOUGH = "shadeExpandedEnough"
        private const val COL_VISIBLE = "visible"
        const val TRACK_GROUP = "StatusBar"

        fun tableLogBufferName(displayId: Int) = "StatusBarVisibilityViewModelTableLog[$displayId]"
    }
}
