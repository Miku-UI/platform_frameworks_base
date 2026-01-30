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

package com.android.systemui.statusbar.pipeline.shared.ui.viewmodel

import android.annotation.ColorInt
import android.graphics.Rect
import android.graphics.RectF
import android.view.Display
import android.view.View
import androidx.compose.runtime.getValue
import com.android.app.tracing.FlowTracing.traceEach
import com.android.app.tracing.TrackGroupUtils.trackGroup
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.desktop.domain.interactor.DesktopInteractor
import com.android.systemui.display.dagger.SystemUIDisplaySubcomponent.DisplayAware
import com.android.systemui.display.dagger.SystemUIDisplaySubcomponent.DisplayId
import com.android.systemui.keyguard.domain.interactor.KeyguardInteractor
import com.android.systemui.keyguard.domain.interactor.KeyguardOcclusionInteractor
import com.android.systemui.keyguard.domain.interactor.KeyguardTransitionInteractor
import com.android.systemui.keyguard.shared.model.Edge
import com.android.systemui.keyguard.shared.model.KeyguardState
import com.android.systemui.keyguard.shared.model.KeyguardState.DREAMING
import com.android.systemui.keyguard.shared.model.KeyguardState.GONE
import com.android.systemui.keyguard.shared.model.KeyguardState.LOCKSCREEN
import com.android.systemui.keyguard.shared.model.KeyguardState.OCCLUDED
import com.android.systemui.keyguard.shared.model.TransitionState
import com.android.systemui.lifecycle.Activatable
import com.android.systemui.lifecycle.ExclusiveActivatable
import com.android.systemui.lifecycle.Hydrator
import com.android.systemui.log.table.TableLogBufferFactory
import com.android.systemui.log.table.logDiffsForTable
import com.android.systemui.plugins.DarkIconDispatcher
import com.android.systemui.scene.domain.interactor.SceneInteractor
import com.android.systemui.scene.shared.flag.SceneContainerFlag
import com.android.systemui.scene.shared.model.Overlays
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.shade.domain.interactor.ShadeDisplaysInteractor
import com.android.systemui.shade.domain.interactor.ShadeInteractor
import com.android.systemui.shade.shared.flag.ShadeWindowGoesAround
import com.android.systemui.statusbar.chips.mediaprojection.domain.model.MediaProjectionStopDialogModel
import com.android.systemui.statusbar.chips.sharetoapp.ui.viewmodel.ShareToAppChipViewModel
import com.android.systemui.statusbar.chips.ui.model.MultipleOngoingActivityChipsModel
import com.android.systemui.statusbar.chips.ui.model.MultipleOngoingActivityChipsModelLegacy
import com.android.systemui.statusbar.chips.ui.model.OngoingActivityChipModel
import com.android.systemui.statusbar.chips.ui.viewmodel.OngoingActivityChipsViewModel
import com.android.systemui.statusbar.chips.uievents.StatusBarChipsUiEventLogger
import com.android.systemui.statusbar.events.domain.interactor.SystemStatusEventAnimationInteractor
import com.android.systemui.statusbar.events.shared.model.SystemEventAnimationState.Idle
import com.android.systemui.statusbar.featurepods.popups.StatusBarPopupChips
import com.android.systemui.statusbar.featurepods.popups.ui.model.PopupChipModel
import com.android.systemui.statusbar.featurepods.popups.ui.viewmodel.StatusBarPopupChipsViewModel
import com.android.systemui.statusbar.headsup.shared.StatusBarNoHunBehavior
import com.android.systemui.statusbar.layout.ui.viewmodel.AppHandlesViewModel
import com.android.systemui.statusbar.layout.ui.viewmodel.StatusBarBoundsViewModel
import com.android.systemui.statusbar.layout.ui.viewmodel.StatusBarContentInsetsViewModelStore
import com.android.systemui.statusbar.notification.domain.interactor.ActiveNotificationsInteractor
import com.android.systemui.statusbar.notification.domain.interactor.HeadsUpNotificationInteractor
import com.android.systemui.statusbar.notification.headsup.PinnedStatus
import com.android.systemui.statusbar.notification.promoted.PromotedNotificationUi
import com.android.systemui.statusbar.phone.domain.interactor.DarkIconInteractor
import com.android.systemui.statusbar.phone.domain.interactor.IsAreaDark
import com.android.systemui.statusbar.phone.domain.interactor.LightsOutInteractor
import com.android.systemui.statusbar.phone.ongoingcall.StatusBarChipsModernization
import com.android.systemui.statusbar.pipeline.battery.ui.viewmodel.BatteryNextToPercentViewModel
import com.android.systemui.statusbar.pipeline.battery.ui.viewmodel.BatteryViewModel
import com.android.systemui.statusbar.pipeline.shared.domain.interactor.HomeStatusBarIconBlockListInteractor
import com.android.systemui.statusbar.pipeline.shared.domain.interactor.HomeStatusBarInteractor
import com.android.systemui.statusbar.pipeline.shared.ui.model.ChipsVisibilityModel
import com.android.systemui.statusbar.pipeline.shared.ui.model.SystemInfoCombinedVisibilityModel
import com.android.systemui.statusbar.pipeline.shared.ui.model.VisibilityModel
import com.android.systemui.statusbar.systemstatusicons.ui.viewmodel.SystemStatusIconsViewModel
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import javax.inject.Provider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * A view model that manages the visibility of the [CollapsedStatusBarFragment] based on the device
 * state.
 *
 * Right now, most of the status bar visibility management is actually in
 * [CollapsedStatusBarFragment.calculateInternalModel], which uses
 * [CollapsedStatusBarFragment.shouldHideNotificationIcons] and
 * [StatusBarHideIconsForBouncerManager]. We should move those pieces of logic to this class instead
 * so that it's all in one place and easily testable outside of the fragment.
 */
interface HomeStatusBarViewModel : Activatable {
    /** Factory to create the view model for the battery icon with the percentage alongside */
    val batteryNextToPercentViewModel: BatteryNextToPercentViewModel.Factory
    /** Factory for the unified (percent embedded) battery view model */
    val unifiedBatteryViewModel: BatteryViewModel.BasedOnUserSetting.Factory

    /** Factory to create the view model for system status icons */
    val systemStatusIconsViewModelFactory: SystemStatusIconsViewModel.Factory

    /**
     * Factory to create the view model for storing bounds of child views in/around the status bar.
     */
    val statusBarBoundsViewModelFactory: StatusBarBoundsViewModel.Factory

    /**
     * Factory to create the view model for storing bounds of app handles overlapping with the
     * status bar.
     */
    val appHandlesViewModelFactory: AppHandlesViewModel.Factory

    /**
     * True if the device is currently transitioning from lockscreen to occluded and false
     * otherwise.
     */
    val isTransitioningFromLockscreenToOccluded: StateFlow<Boolean>

    /** Emits whenever a transition from lockscreen to dream has started. */
    val transitionFromLockscreenToDreamStartedEvent: Flow<Unit>

    /**
     * The current media projection stop dialog to be shown, or
     * `MediaProjectionStopDialogModel.Hidden` if no dialog is visible.
     */
    val mediaProjectionStopDialogDueToCallEndedState: StateFlow<MediaProjectionStopDialogModel>

    /**
     * The ongoing activity chip that should be primarily shown on the left-hand side of the status
     * bar. If there are multiple ongoing activity chips, this one should take priority.
     */
    val primaryOngoingActivityChip: StateFlow<OngoingActivityChipModel>

    /** All supported activity chips, whether they are currently active or not. */
    val ongoingActivityChips: ChipsVisibilityModel

    /** Invoked each time a chip's on-screen bounds have changed. */
    fun onChipBoundsChanged(key: String, bounds: RectF)

    /** Notifies that the system icons container was clicked. */
    fun onQuickSettingsChipClicked()

    /** Notifies that the notification icons container was clicked. */
    fun onNotificationIconChipClicked()

    /** Whether the QS Chip should be highlighted. */
    val isQuickSettingsChipHighlighted: Boolean

    /** Whether the Notifications chip should be highlighted. */
    val isNotificationsChipHighlighted: Boolean

    /**
     * The multiple ongoing activity chips that should be shown on the left-hand side of the status
     * bar.
     */
    @Deprecated("Since StatusBarChipsModernization, use the new ongoingActivityChips")
    val ongoingActivityChipsLegacy: StateFlow<MultipleOngoingActivityChipsModelLegacy>

    /** View model for the carrier name that may show in the status bar based on carrier config */
    val operatorNameViewModel: StatusBarOperatorNameViewModel

    /** The popup chips that should be shown on the right-hand side of the status bar. */
    val popupChips: List<PopupChipModel.Shown>

    /**
     * True if the status bar should be visible.
     *
     * TODO(b/364360986): Once the is<SomeChildView>Visible flows are fully enabled, we shouldn't
     *   need this flow anymore.
     */
    val isHomeStatusBarAllowed: StateFlow<Boolean>

    /** True if the home status bar is showing, and there is no HUN happening */
    val canShowOngoingActivityChips: Flow<Boolean>

    /** True if the operator name view is not hidden due to HUN or other visibility state */
    val shouldShowOperatorNameView: Flow<Boolean>
    val isClockVisible: Flow<VisibilityModel>
    val isNotificationIconContainerVisible: Flow<VisibilityModel>

    /**
     * Pair of (system info visibility, event animation state). The animation state can be used to
     * respond to the system event chip animations. In all cases, system info visibility correctly
     * models the View.visibility for the system info area
     */
    val systemInfoCombinedVis: StateFlow<SystemInfoCombinedVisibilityModel>

    /** Which icons to block from the home status bar */
    val iconBlockList: Flow<List<String>>

    /** This status bar's current content area for the given rotation in absolute bounds. */
    val contentArea: Flow<Rect>

    /**
     * Apps can request a low profile mode [android.view.View.SYSTEM_UI_FLAG_LOW_PROFILE] where
     * status bar and navigation icons dim. In this mode, a notification dot appears where the
     * notification icons would appear if they would be shown outside of this mode.
     *
     * This flow tells when to show or hide the notification dot in the status bar to indicate
     * whether there are notifications when the device is in
     * [android.view.View.SYSTEM_UI_FLAG_LOW_PROFILE].
     */
    val areNotificationsLightsOut: Flow<Boolean>

    /**
     * A flow of [StatusBarTintColor], a functional interface that will allow a view to calculate
     * its correct tint depending on location
     */
    val areaTint: Flow<StatusBarTintColor>

    /** [IsAreaDark] applicable for this status bar's display and content area */
    val areaDark: IsAreaDark

    /** True if the desktop status bar is enabled. */
    val useDesktopStatusBar: Boolean

    /** Interface for the assisted factory, to allow for providing a fake in tests */
    interface HomeStatusBarViewModelFactory {
        fun create(): HomeStatusBarViewModel
    }
}

class HomeStatusBarViewModelImpl
@AssistedInject
constructor(
    @DisplayId thisDisplayId: Int,
    override val batteryNextToPercentViewModel: BatteryNextToPercentViewModel.Factory,
    override val unifiedBatteryViewModel: BatteryViewModel.BasedOnUserSetting.Factory,
    override val systemStatusIconsViewModelFactory: SystemStatusIconsViewModel.Factory,
    override val statusBarBoundsViewModelFactory: StatusBarBoundsViewModel.Factory,
    override val appHandlesViewModelFactory: AppHandlesViewModel.Factory,
    tableLoggerFactory: TableLogBufferFactory,
    @DisplayAware homeStatusBarInteractor: HomeStatusBarInteractor,
    homeStatusBarIconBlockListInteractor: HomeStatusBarIconBlockListInteractor,
    lightsOutInteractor: LightsOutInteractor,
    notificationsInteractor: ActiveNotificationsInteractor,
    desktopInteractor: DesktopInteractor,
    darkIconInteractor: DarkIconInteractor,
    headsUpNotificationInteractor: HeadsUpNotificationInteractor,
    keyguardTransitionInteractor: KeyguardTransitionInteractor,
    keyguardInteractor: KeyguardInteractor,
    override val operatorNameViewModel: StatusBarOperatorNameViewModel,
    private val sceneInteractor: SceneInteractor,
    occlusionInteractor: KeyguardOcclusionInteractor,
    private val shadeInteractor: ShadeInteractor,
    shareToAppChipViewModel: ShareToAppChipViewModel,
    @DisplayAware private val ongoingActivityChipsViewModel: OngoingActivityChipsViewModel,
    statusBarPopupChipsViewModelFactory: StatusBarPopupChipsViewModel.Factory,
    animations: SystemStatusEventAnimationInteractor,
    statusBarContentInsetsViewModelStore: StatusBarContentInsetsViewModelStore,
    @DisplayAware bgDisplayScope: CoroutineScope,
    @Background bgDispatcher: CoroutineDispatcher,
    shadeDisplaysInteractor: Provider<ShadeDisplaysInteractor>,
    private val uiEventLogger: StatusBarChipsUiEventLogger,
) : HomeStatusBarViewModel, ExclusiveActivatable() {

    private val hydrator = Hydrator(traceName = "HomeStatusBarViewModel.hydrator")

    val tableLogger = tableLoggerFactory.getOrCreate(tableLogBufferName(thisDisplayId), 200)

    private val statusBarPopupChips by lazy { statusBarPopupChipsViewModelFactory.create() }

    override val isTransitioningFromLockscreenToOccluded: StateFlow<Boolean> =
        if (SceneContainerFlag.isEnabled) {
                flowOf(false)
            } else {
                keyguardTransitionInteractor
                    .isInTransition(Edge.create(from = LOCKSCREEN, to = OCCLUDED))
                    .distinctUntilChanged()
                    .logDiffsForTable(
                        tableLogBuffer = tableLogger,
                        columnName = COL_LOCK_TO_OCCLUDED,
                        initialValue = false,
                    )
            }
            .stateIn(bgDisplayScope, SharingStarted.WhileSubscribed(), initialValue = false)

    override val transitionFromLockscreenToDreamStartedEvent: Flow<Unit> =
        keyguardTransitionInteractor
            .transition(Edge.create(from = LOCKSCREEN, to = DREAMING))
            .filter { it.transitionState == TransitionState.STARTED }
            .map {}
            .flowOn(bgDispatcher)

    override val mediaProjectionStopDialogDueToCallEndedState =
        shareToAppChipViewModel.stopDialogToShow

    override val primaryOngoingActivityChip = ongoingActivityChipsViewModel.primaryChip

    override val ongoingActivityChipsLegacy = ongoingActivityChipsViewModel.chipsLegacy

    override val popupChips
        get() = statusBarPopupChips.shownPopupChips

    private val isShadeExpandedEnough =
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

    /**
     * Whether the display of this statusbar has the shade window (that is hosting shade container
     * and lockscreen, among other things).
     */
    private val isShadeWindowOnThisDisplay =
        if (ShadeWindowGoesAround.isEnabled) {
            shadeDisplaysInteractor.get().displayId.map { shadeDisplayId ->
                thisDisplayId == shadeDisplayId
            }
        } else {
            // Shade doesn't move anywhere, it is always on the default display.
            flowOf(thisDisplayId == Display.DEFAULT_DISPLAY)
        }

    private val isShadeVisibleOnAnyDisplay =
        if (SceneContainerFlag.isEnabled) {
            sceneInteractor.currentOverlays.map { currentOverlays ->
                (Overlays.NotificationsShade in currentOverlays ||
                    Overlays.QuickSettingsShade in currentOverlays)
            }
        } else {
            isShadeExpandedEnough
        }

    private val isShadeVisibleOnThisDisplay: Flow<Boolean> =
        combine(isShadeWindowOnThisDisplay, isShadeVisibleOnAnyDisplay) {
            hasShade,
            isShadeVisibleOnAnyDisplay ->
            hasShade && isShadeVisibleOnAnyDisplay
        }

    /** Whether keyguard is transitioning from Gone to Dreaming. */
    private val isTransitioningFromGoneToDream: Flow<Boolean> =
        keyguardTransitionInteractor
            .isInTransition(
                Edge.create(from = Scenes.Gone, to = DREAMING),
                edgeWithoutSceneContainer = Edge.create(from = GONE, to = DREAMING),
            )
            .distinctUntilChanged()
            .logDiffsForTable(
                tableLogBuffer = tableLogger,
                columnName = COL_GONE_TO_DREAM,
                initialValue = false,
            )
            .flowOn(bgDispatcher)

    private val isHomeStatusBarAllowedByScene: Flow<Boolean> =
        combine(
                sceneInteractor.currentScene,
                isShadeVisibleOnAnyDisplay,
                occlusionInteractor.isKeyguardOccluded,
                isShadeWindowOnThisDisplay,
            ) { currentScene, isShadeVisibleOnAnyDisplay, isOccluded, isShadeWindowOnThisDisplay ->
                if (isOccluded) {
                    true
                } else if (isShadeWindowOnThisDisplay) {
                    currentScene == Scenes.Gone && !isShadeVisibleOnAnyDisplay
                } else {
                    // When the shade is visible on another display,
                    // allow the home status bar on the current display.
                    currentScene == Scenes.Gone || isShadeVisibleOnAnyDisplay
                }
            }
            .distinctUntilChanged()
            .logDiffsForTable(
                tableLogBuffer = tableLogger,
                columnName = COL_ALLOWED_BY_SCENE,
                initialValue = false,
            )

    override val areNotificationsLightsOut: Flow<Boolean> =
        combine(
                notificationsInteractor.areAnyNotificationsPresent,
                lightsOutInteractor.isLowProfile(thisDisplayId) ?: flowOf(false),
            ) { hasNotifications, isLowProfile ->
                hasNotifications && isLowProfile
            }
            .distinctUntilChanged()
            .logDiffsForTable(
                tableLogBuffer = tableLogger,
                columnName = COL_NOTIF_LIGHTS_OUT,
                initialValue = false,
            )
            .flowOn(bgDispatcher)

    override val areaTint: Flow<StatusBarTintColor> =
        darkIconInteractor
            .darkState(thisDisplayId)
            .map { (areas: Collection<Rect>, tint: Int) ->
                StatusBarTintColor { viewBounds: Rect ->
                    if (DarkIconDispatcher.isInAreas(areas, viewBounds)) {
                        tint
                    } else {
                        DarkIconDispatcher.DEFAULT_ICON_TINT
                    }
                }
            }
            .conflate()
            .distinctUntilChanged()
            .flowOn(bgDispatcher)

    override val areaDark: IsAreaDark by
        hydrator.hydratedStateOf(
            traceName = "areaDark",
            initialValue = IsAreaDark { true },
            source = darkIconInteractor.isAreaDark(thisDisplayId),
        )

    private val currentKeyguardState: Flow<KeyguardState> =
        keyguardTransitionInteractor.currentKeyguardState.onEach {
            tableLogger.logChange(
                columnName = COL_KEYGUARD_STATE,
                value = it.name,
                isInitial = false,
            )
        }

    override val useDesktopStatusBar: Boolean by
        hydrator.hydratedStateOf(
            traceName = "useDesktopStatusBar",
            initialValue = false,
            source = desktopInteractor.useDesktopStatusBar,
        )

    override val isQuickSettingsChipHighlighted: Boolean by
        hydrator.hydratedStateOf(
            traceName = "isQsChipHighlighted",
            initialValue = false,
            source = shadeInteractor.isQsExpanded,
        )

    override val isNotificationsChipHighlighted: Boolean by
        hydrator.hydratedStateOf(
            traceName = "isNotificationsChipHighlighted",
            initialValue = false,
            source = shadeInteractor.isNotificationsExpanded,
        )

    /**
     * True if the current SysUI state can show the home status bar (aka this status bar), and false
     * if we shouldn't be showing any part of the home status bar.
     */
    private val isHomeScreenStatusBarAllowedLegacy: Flow<Boolean> =
        combine(currentKeyguardState, isShadeVisibleOnThisDisplay) {
                currentKeyguardState,
                isShadeVisibleOnThisDisplay ->
                (currentKeyguardState == GONE || currentKeyguardState == OCCLUDED) &&
                    !isShadeVisibleOnThisDisplay
            }
            .distinctUntilChanged()
            .logDiffsForTable(
                tableLogBuffer = tableLogger,
                columnName = COL_ALLOWED_LEGACY,
                initialValue = false,
            )
            .stateIn(bgDisplayScope, SharingStarted.WhileSubscribed(), initialValue = false)

    // "Compat" to cover both legacy and Scene container case in one flow.
    private val isHomeStatusBarAllowedCompat =
        if (SceneContainerFlag.isEnabled) {
            isHomeStatusBarAllowedByScene
        } else {
            isHomeScreenStatusBarAllowedLegacy
        }

    override val isHomeStatusBarAllowed =
        isHomeStatusBarAllowedCompat
            .traceEach(trackGroup(TRACK_GROUP, "isHomeStatusBarAllowed"), logcat = true)
            .stateIn(bgDisplayScope, SharingStarted.WhileSubscribed(), initialValue = false)

    private val shouldHomeStatusBarBeVisible =
        combine(
                isHomeStatusBarAllowed,
                keyguardInteractor.isSecureCameraActive,
                headsUpNotificationInteractor.statusBarHeadsUpStatus,
                isTransitioningFromGoneToDream,
                keyguardInteractor.isKeyguardVisible,
            ) {
                isHomeStatusBarAllowed,
                isSecureCameraActive,
                headsUpState,
                isGoneToDream,
                isKeyguardVisible ->
                val showForHeadsUp =
                    if (StatusBarNoHunBehavior.isEnabled) {
                        false
                    } else {
                        // HUNs can appear on lockscreen if face auth with bypass is enabled, in
                        // which case we need to show the HUN app name in the status bar
                        headsUpState.isPinned
                    }
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
                showForHeadsUp ||
                    (isHomeStatusBarAllowed &&
                        !isSecureCameraActive &&
                        !isGoneToDream &&
                        // In legacy code, check if keyguard is visible to cover canceled
                        // transitions. In Flexi, the scene state is enough to cover this case.
                        if (!SceneContainerFlag.isEnabled) !isKeyguardVisible else true)
            }
            .distinctUntilChanged()
            .logDiffsForTable(
                tableLogBuffer = tableLogger,
                columnName = COL_VISIBLE,
                initialValue = false,
            )
            .stateIn(bgDisplayScope, SharingStarted.WhileSubscribed(), initialValue = false)

    /**
     * True if we need to hide the usual start side content in order to show the heads up
     * notification info.
     */
    private val hideStartSideContentForHeadsUp: Flow<Boolean> =
        if (StatusBarNoHunBehavior.isEnabled) {
            flowOf(false)
        } else {
            headsUpNotificationInteractor.statusBarHeadsUpStatus.map {
                it == PinnedStatus.PinnedBySystem
            }
        }

    override val shouldShowOperatorNameView: Flow<Boolean> =
        combine(
                shouldHomeStatusBarBeVisible,
                hideStartSideContentForHeadsUp,
                homeStatusBarInteractor.visibilityViaDisableFlags,
                homeStatusBarInteractor.shouldShowOperatorName,
            ) {
                shouldStatusBarBeVisible,
                hideStartSideContentForHeadsUp,
                visibilityViaDisableFlags,
                shouldShowOperator ->
                shouldStatusBarBeVisible &&
                    !hideStartSideContentForHeadsUp &&
                    visibilityViaDisableFlags.isSystemInfoAllowed &&
                    shouldShowOperator
            }
            .distinctUntilChanged()
            .logDiffsForTable(
                tableLogBuffer = tableLogger,
                columnName = COL_SHOW_OPERATOR_NAME,
                initialValue = false,
            )
            .flowOn(bgDispatcher)

    override val canShowOngoingActivityChips: Flow<Boolean> =
        combine(
            isHomeStatusBarAllowed,
            keyguardInteractor.isSecureCameraActive,
            hideStartSideContentForHeadsUp,
        ) { isHomeStatusBarAllowed, isSecureCameraActive, hideStartSideContentForHeadsUp ->
            isHomeStatusBarAllowed && !isSecureCameraActive && !hideStartSideContentForHeadsUp
        }

    private val chipsVisibilityModel: StateFlow<ChipsVisibilityModel> =
        combine(ongoingActivityChipsViewModel.chips, canShowOngoingActivityChips) { chips, canShow
                ->
                ChipsVisibilityModel(chips, areChipsAllowed = canShow)
            }
            .traceEach(trackGroup(TRACK_GROUP, "chips"), logcat = true) {
                "Chips[allowed=${it.areChipsAllowed} numChips=${it.chips.active.size}]"
            }
            .stateIn(
                bgDisplayScope,
                SharingStarted.WhileSubscribed(),
                ChipsVisibilityModel(
                    chips = MultipleOngoingActivityChipsModel(),
                    areChipsAllowed = false,
                ),
            )

    override val ongoingActivityChips: ChipsVisibilityModel by
        hydrator.hydratedStateOf(
            traceName = "ongoingActivityChips",
            initialValue =
                ChipsVisibilityModel(
                    chips = MultipleOngoingActivityChipsModel(),
                    areChipsAllowed = false,
                ),
            source = chipsVisibilityModel,
        )

    override fun onChipBoundsChanged(key: String, bounds: RectF) {
        ongoingActivityChipsViewModel.onChipBoundsChanged(key, bounds)
    }

    override fun onQuickSettingsChipClicked() {
        if (SceneContainerFlag.isUnexpectedlyInLegacyMode()) {
            return
        }
        shadeInteractor.toggleQuickSettingsShade(
            loggingReason = "HomeStatusBarViewModel.onQuickSettingsChipClicked"
        )
    }

    override fun onNotificationIconChipClicked() {
        if (SceneContainerFlag.isUnexpectedlyInLegacyMode()) {
            return
        }
        shadeInteractor.toggleNotificationsShade(
            loggingReason = "HomeStatusBarViewModel.onNotificationIconChipClicked"
        )
    }

    private val hasOngoingActivityChips =
        if (StatusBarChipsModernization.isEnabled) {
            chipsVisibilityModel.map { it.chips.active.any { chip -> !chip.isHidden } }
        } else if (PromotedNotificationUi.isEnabled) {
            ongoingActivityChipsLegacy.map { it.primary is OngoingActivityChipModel.Active }
        } else {
            primaryOngoingActivityChip.map { it is OngoingActivityChipModel.Active }
        }

    private val isAnyChipVisible =
        combine(hasOngoingActivityChips, canShowOngoingActivityChips) { hasChips, canShowChips ->
            hasChips && canShowChips
        }

    override val isClockVisible: Flow<VisibilityModel> =
        combine(
                shouldHomeStatusBarBeVisible,
                hideStartSideContentForHeadsUp,
                homeStatusBarInteractor.visibilityViaDisableFlags,
            ) { shouldStatusBarBeVisible, hideStartSideContentForHeadsUp, visibilityViaDisableFlags
                ->
                val showClock =
                    shouldStatusBarBeVisible &&
                        visibilityViaDisableFlags.isClockAllowed &&
                        !hideStartSideContentForHeadsUp
                // Always use View.INVISIBLE here, so that animations work
                VisibilityModel(showClock.toVisibleOrInvisible(), visibilityViaDisableFlags.animate)
            }
            .distinctUntilChanged()
            .logDiffsForTable(
                tableLogBuffer = tableLogger,
                columnPrefix = COL_PREFIX_CLOCK,
                initialValue = VisibilityModel(false.toVisibleOrInvisible(), false),
            )
            .flowOn(bgDispatcher)

    override val isNotificationIconContainerVisible: Flow<VisibilityModel> =
        combine(
                shouldHomeStatusBarBeVisible,
                isAnyChipVisible,
                homeStatusBarInteractor.visibilityViaDisableFlags,
            ) { shouldStatusBarBeVisible, anyChipVisible, visibilityViaDisableFlags ->
                val showNotificationIconContainer =
                    if (anyChipVisible) {
                        false
                    } else {
                        shouldStatusBarBeVisible &&
                            visibilityViaDisableFlags.areNotificationIconsAllowed
                    }
                VisibilityModel(
                    showNotificationIconContainer.toVisibleOrGone(),
                    visibilityViaDisableFlags.animate,
                )
            }
            .distinctUntilChanged()
            .logDiffsForTable(
                tableLogBuffer = tableLogger,
                columnPrefix = COL_PREFIX_NOTIF_CONTAINER,
                initialValue = VisibilityModel(false.toVisibleOrInvisible(), false),
            )
            .flowOn(bgDispatcher)

    private val isSystemInfoVisible =
        combine(shouldHomeStatusBarBeVisible, homeStatusBarInteractor.visibilityViaDisableFlags) {
            shouldStatusBarBeVisible,
            visibilityViaDisableFlags ->
            val showSystemInfo =
                shouldStatusBarBeVisible && visibilityViaDisableFlags.isSystemInfoAllowed
            VisibilityModel(showSystemInfo.toVisibleOrGone(), visibilityViaDisableFlags.animate)
        }

    override val systemInfoCombinedVis =
        combine(isSystemInfoVisible, animations.animationState) { sysInfoVisible, animationState ->
                SystemInfoCombinedVisibilityModel(sysInfoVisible, animationState)
            }
            .distinctUntilChanged()
            .logDiffsForTable(
                tableLogBuffer = tableLogger,
                columnPrefix = COL_PREFIX_SYSTEM_INFO,
                initialValue =
                    SystemInfoCombinedVisibilityModel(VisibilityModel(View.VISIBLE, false), Idle),
            )
            .stateIn(
                bgDisplayScope,
                SharingStarted.WhileSubscribed(),
                SystemInfoCombinedVisibilityModel(VisibilityModel(View.VISIBLE, false), Idle),
            )

    override val iconBlockList: Flow<List<String>> =
        homeStatusBarIconBlockListInteractor.iconBlockList.flowOn(bgDispatcher)

    override val contentArea: Flow<Rect> =
        statusBarContentInsetsViewModelStore.forDisplay(thisDisplayId)?.contentArea
            ?: flowOf(Rect(0, 0, 0, 0)).flowOn(bgDispatcher)

    @View.Visibility
    private fun Boolean.toVisibleOrGone(): Int {
        return if (this) View.VISIBLE else View.GONE
    }

    // Similar to the above, but uses INVISIBLE in place of GONE
    @View.Visibility
    private fun Boolean.toVisibleOrInvisible(): Int = if (this) View.VISIBLE else View.INVISIBLE

    override suspend fun onActivated(): Nothing {
        coroutineScope {
            launch { hydrator.activate() }
            if (StatusBarPopupChips.isEnabled) {
                launch { statusBarPopupChips.activate() }
            }
            launch { uiEventLogger.hydrateUiEventLogging(chipsFlow = chipsVisibilityModel) }
            awaitCancellation()
        }
    }

    /** Inject this to create the display-dependent view model */
    @AssistedFactory
    interface HomeStatusBarViewModelFactoryImpl :
        HomeStatusBarViewModel.HomeStatusBarViewModelFactory {
        override fun create(): HomeStatusBarViewModelImpl
    }

    companion object {
        private const val COL_LOCK_TO_OCCLUDED = "Lock->Occluded"
        private const val COL_GONE_TO_DREAM = "Gone->Dreaming"
        private const val COL_ALLOWED_LEGACY = "allowedLegacy"
        private const val COL_ALLOWED_BY_SCENE = "allowedByScene"
        private const val COL_SHADE_EXPANDED_ENOUGH = "shadeExpandedEnough"
        private const val COL_KEYGUARD_STATE = "keyguardState"
        private const val COL_NOTIF_LIGHTS_OUT = "notifLightsOut"
        private const val COL_SHOW_OPERATOR_NAME = "showOperatorName"
        private const val COL_VISIBLE = "visible"
        private const val COL_PREFIX_CLOCK = "clock"
        private const val COL_PREFIX_NOTIF_CONTAINER = "notifContainer"
        private const val COL_PREFIX_SYSTEM_INFO = "systemInfo"

        private const val TRACK_GROUP = "StatusBar"

        fun tableLogBufferName(displayId: Int) = "HomeStatusBarViewModel[$displayId]"
    }
}

/** Lookup the color for a given view in the status bar */
fun interface StatusBarTintColor {
    @ColorInt fun tint(viewBounds: Rect): Int
}
