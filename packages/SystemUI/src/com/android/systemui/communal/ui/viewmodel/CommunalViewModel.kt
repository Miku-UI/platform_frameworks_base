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

package com.android.systemui.communal.ui.viewmodel

import android.content.ComponentName
import com.android.app.tracing.coroutines.launchTraced as launch
import com.android.compose.animation.scene.SceneKey
import com.android.systemui.Flags
import com.android.systemui.Flags.hubEditModeTransition
import com.android.systemui.classifier.Classifier
import com.android.systemui.classifier.domain.interactor.FalsingInteractor
import com.android.systemui.communal.dagger.CommunalModule.Companion.SWIPE_TO_HUB
import com.android.systemui.communal.domain.interactor.CommunalInteractor
import com.android.systemui.communal.domain.interactor.CommunalSceneInteractor
import com.android.systemui.communal.domain.interactor.CommunalSettingsInteractor
import com.android.systemui.communal.domain.interactor.CommunalTutorialInteractor
import com.android.systemui.communal.domain.model.CommunalContentModel
import com.android.systemui.communal.shared.log.CommunalMetricsLogger
import com.android.systemui.communal.shared.log.CommunalSceneLogger
import com.android.systemui.communal.shared.model.CommunalBackgroundType
import com.android.systemui.communal.shared.model.CommunalScenes
import com.android.systemui.communal.shared.model.EditModeState
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.keyguard.domain.interactor.KeyguardInteractor
import com.android.systemui.keyguard.domain.interactor.KeyguardTransitionInteractor
import com.android.systemui.keyguard.shared.model.KeyguardState
import com.android.systemui.keyguard.ui.transitions.BlurConfig
import com.android.systemui.log.LogBuffer
import com.android.systemui.log.core.Logger
import com.android.systemui.log.dagger.CommunalLog
import com.android.systemui.media.controls.domain.pipeline.interactor.MediaCarouselInteractor
import com.android.systemui.media.controls.ui.controller.MediaCarouselController
import com.android.systemui.media.controls.ui.view.MediaHost
import com.android.systemui.media.dagger.MediaModule
import com.android.systemui.media.remedia.shared.flag.MediaControlsInComposeFlag
import com.android.systemui.media.remedia.ui.viewmodel.MediaViewModel
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.shade.domain.interactor.ShadeInteractor
import com.android.systemui.statusbar.KeyguardIndicationController
import com.android.systemui.util.kotlin.BooleanFlowOperators.allOf
import com.android.systemui.util.kotlin.BooleanFlowOperators.not
import com.android.systemui.util.kotlin.getValue
import com.android.systemui.utils.coroutines.flow.conflatedCallbackFlow
import com.android.systemui.utils.coroutines.flow.flatMapLatestConflated
import dagger.Lazy
import javax.inject.Inject
import javax.inject.Named
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

/** The default view model used for showing the communal hub. */
@SysUISingleton
class CommunalViewModel
@Inject
constructor(
    @Main val mainDispatcher: CoroutineDispatcher,
    @Application private val scope: CoroutineScope,
    @Background private val bgScope: CoroutineScope,
    private val keyguardTransitionInteractor: KeyguardTransitionInteractor,
    keyguardInteractor: KeyguardInteractor,
    private val keyguardIndicationController: KeyguardIndicationController,
    communalSceneInteractor: CommunalSceneInteractor,
    private val communalInteractor: CommunalInteractor,
    private val communalSettingsInteractor: CommunalSettingsInteractor,
    tutorialInteractor: CommunalTutorialInteractor,
    private val shadeInteractor: ShadeInteractor,
    @Named(MediaModule.COMMUNAL_HUB) mediaHost: MediaHost,
    @CommunalLog logBuffer: LogBuffer,
    private val metricsLogger: CommunalMetricsLogger,
    mediaCarouselController: MediaCarouselController,
    blurConfig: BlurConfig,
    @Named(SWIPE_TO_HUB) private val swipeToHub: Boolean,
    private val communalSceneLogger: CommunalSceneLogger,
    private val falsingInteractor: FalsingInteractor,
    mediaViewModelFactory: MediaViewModel.Factory,
    mediaCarouselInteractorLazy: Lazy<MediaCarouselInteractor>,
) :
    BaseCommunalViewModel(
        communalSceneInteractor,
        communalInteractor,
        mediaHost,
        mediaCarouselController,
        mediaViewModelFactory,
        mediaCarouselInteractorLazy,
    ) {

    private val logger = Logger(logBuffer, "CommunalViewModel")

    private val mediaCarouselInteractor by mediaCarouselInteractorLazy

    private val isMediaHostVisible =
        if (MediaControlsInComposeFlag.isEnabled) {
            combine(
                mediaCarouselInteractor.isLockedAndHidden,
                mediaCarouselInteractor.hasActiveMedia,
            ) { isLockedAndHidden, hasActiveMedia ->
                if (isLockedAndHidden) {
                    false
                } else {
                    hasActiveMedia
                }
            }
        } else {
            conflatedCallbackFlow {
                    val callback = { visible: Boolean ->
                        trySend(visible)
                        Unit
                    }
                    mediaHost.addVisibilityChangeListener(callback)
                    awaitClose { mediaHost.removeVisibilityChangeListener(callback) }
                }
                .onStart {
                    // Ensure the visibility state is correct when the hub is opened and this flow
                    // is
                    // started so that the UMO is shown when needed. The visibility state in
                    // MediaHost
                    // is not updated once its view has been detached, aka the hub is closed, which
                    // can
                    // result in this getting stuck as False and never being updated as the UMO is
                    // not
                    // shown.
                    mediaHost.updateViewVisibility()
                    emit(mediaHost.visible)
                }
                .distinctUntilChanged()
                .onEach { logger.d({ "_isMediaHostVisible: $bool1" }) { bool1 = it } }
                .flowOn(mainDispatcher)
        }

    /** Communal content saved from the previous emission when the flow is active (not "frozen"). */
    private var frozenCommunalContent: List<CommunalContentModel>? = null

    private val ongoingContent =
        isMediaHostVisible.flatMapLatest { isMediaHostVisible ->
            communalInteractor.ongoingContent(isMediaHostVisible).onEach {
                if (!MediaControlsInComposeFlag.isEnabled) {
                    mediaHost.updateViewVisibility()
                }
            }
        }

    private val latestCommunalContent: Flow<List<CommunalContentModel>> =
        tutorialInteractor.isTutorialAvailable
            .flatMapLatest { isTutorialMode ->
                if (isTutorialMode) {
                    return@flatMapLatest flowOf(communalInteractor.tutorialContent)
                }
                combine(
                    ongoingContent,
                    communalInteractor.widgetContent,
                    communalInteractor.ctaTileContent,
                ) { ongoing, widgets, ctaTile ->
                    ongoing + widgets + ctaTile
                }
            }
            .onEach { models ->
                frozenCommunalContent = models
                logger.d({ "Content updated: $str1" }) { str1 = models.joinToString { it.key } }
            }

    override val isCommunalContentVisible: Flow<Boolean> = flowOf(true)

    /**
     * Freeze the content flow, when an activity is about to show, like starting a timer via voice:
     * 1) in handheld mode, use the keyguard occluded state;
     * 2) in dreaming mode, where keyguard is already occluded by dream, use the dream wakeup
     *    signal. Since in this case the shell transition info does not include
     *    KEYGUARD_VISIBILITY_TRANSIT_FLAGS, KeyguardTransitionHandler will not run the
     *    occludeAnimation on KeyguardViewMediator.
     */
    override val isCommunalContentFlowFrozen: Flow<Boolean> =
        allOf(
                keyguardTransitionInteractor.isFinishedIn(
                    content = Scenes.Communal,
                    stateWithoutSceneContainer = KeyguardState.GLANCEABLE_HUB,
                ),
                keyguardInteractor.isKeyguardOccluded,
                not(keyguardInteractor.isAbleToDream),
            )
            .distinctUntilChanged()
            .onEach { logger.d("isCommunalContentFlowFrozen: $it") }

    override val communalContent: Flow<List<CommunalContentModel>> =
        isCommunalContentFlowFrozen
            .flatMapLatestConflated { isFrozen ->
                if (isFrozen) {
                    flowOf(frozenCommunalContent ?: emptyList())
                } else {
                    latestCommunalContent
                }
            }
            .onEach { models ->
                logger.d({ "CommunalContent: $str1" }) { str1 = models.joinToString { it.key } }
            }

    override val isEmptyState: Flow<Boolean> =
        communalInteractor.widgetContent
            .map { it.isEmpty() }
            .distinctUntilChanged()
            .onEach { logger.d("isEmptyState: $it") }

    private val _currentPopup: MutableStateFlow<PopupType?> = MutableStateFlow(null)
    override val currentPopup: Flow<PopupType?> = _currentPopup.asStateFlow()

    // The widget is focusable for accessibility when the hub is fully visible and shade is not
    // opened.
    override val isFocusable: Flow<Boolean> =
        combine(
                keyguardTransitionInteractor.isFinishedIn(
                    content = Scenes.Communal,
                    stateWithoutSceneContainer = KeyguardState.GLANCEABLE_HUB,
                ),
                communalInteractor.isIdleOnCommunal,
                shadeInteractor.isAnyFullyExpanded,
            ) { transitionedToGlanceableHub, isIdleOnCommunal, isAnyFullyExpanded ->
                transitionedToGlanceableHub && isIdleOnCommunal && !isAnyFullyExpanded
            }
            .distinctUntilChanged()

    private val _isEnableWidgetDialogShowing: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val isEnableWidgetDialogShowing: Flow<Boolean> = _isEnableWidgetDialogShowing.asStateFlow()

    private val _isEnableWorkProfileDialogShowing: MutableStateFlow<Boolean> =
        MutableStateFlow(false)
    val isEnableWorkProfileDialogShowing: Flow<Boolean> =
        _isEnableWorkProfileDialogShowing.asStateFlow()

    // SystemUI begins animating to the edit mode layout (e.g., pushing down widgets) as soon as the
    // transition to edit mode starts. It then animates back to the original layout before the edit
    // mode activity fully finishes, ensuring a smooth visual transition.
    override val shouldShowEditModeLayout: Flow<Boolean> =
        if (hubEditModeTransition())
            communalSceneInteractor.editModeState.map { it != null && it > EditModeState.STARTING }
        else flowOf(false)

    private val isUiBlurredByBouncer =
        if (Flags.bouncerUiRevamp()) {
            keyguardInteractor.primaryBouncerShowing
        } else {
            flowOf(false)
        }

    private val isUiBlurredByShade =
        if (Flags.notificationShadeBlur()) {
            shadeInteractor.anyExpansion.map { it > 0 }
        } else {
            flowOf(false)
        }

    // Signal for whether the hub should be manually blurred. This turns true when the shade or
    // bouncer is showing.
    val isUiBlurred: StateFlow<Boolean> =
        combine(isUiBlurredByBouncer, isUiBlurredByShade) { values -> values.any { it } }
            .stateIn(scope, SharingStarted.WhileSubscribed(), initialValue = false)

    val blurRadiusPx: Float = blurConfig.maxBlurRadiusPx

    override fun onOpenWidgetEditor(shouldOpenWidgetPickerOnStart: Boolean) {
        // Persist scroll position in glanceable hub so we end up in the same position in edit mode.
        persistScrollPosition("open widget editor")
        communalInteractor.showWidgetEditor(shouldOpenWidgetPickerOnStart)
    }

    override fun onDismissCtaTile() {
        scope.launch {
            communalInteractor.dismissCtaTile()
            setCurrentPopupType(PopupType.CtaTile)
        }
    }

    override fun onShowPreviousMedia() {
        mediaCarouselController.mediaCarouselScrollHandler.scrollByStep(-1)
    }

    override fun onShowNextMedia() {
        mediaCarouselController.mediaCarouselScrollHandler.scrollByStep(1)
    }

    override fun onTapWidget(componentName: ComponentName, rank: Int) {
        metricsLogger.logTapWidget(componentName.flattenToString(), rank)
    }

    fun onClick() {
        keyguardIndicationController.showActionToUnlock()
    }

    override fun onLongClick() {
        if (Flags.glanceableHubDirectEditMode()) {
            onOpenWidgetEditor(false)
            return
        }
        setCurrentPopupType(PopupType.CustomizeWidgetButton)
    }

    override fun onHidePopup() {
        setCurrentPopupType(null)
    }

    override fun onOpenEnableWidgetDialog() {
        setIsEnableWidgetDialogShowing(true)
    }

    fun onEnableWidgetDialogConfirm() {
        communalInteractor.navigateToCommunalWidgetSettings()
        setIsEnableWidgetDialogShowing(false)
    }

    fun onEnableWidgetDialogCancel() {
        setIsEnableWidgetDialogShowing(false)
    }

    override fun onOpenEnableWorkProfileDialog() {
        setIsEnableWorkProfileDialogShowing(true)
    }

    fun onEnableWorkProfileDialogConfirm() {
        communalInteractor.unpauseWorkProfile()
        setIsEnableWorkProfileDialogShowing(false)
    }

    fun onEnableWorkProfileDialogCancel() {
        setIsEnableWorkProfileDialogShowing(false)
    }

    private fun setIsEnableWidgetDialogShowing(isVisible: Boolean) {
        _isEnableWidgetDialogShowing.value = isVisible
    }

    private fun setIsEnableWorkProfileDialogShowing(isVisible: Boolean) {
        _isEnableWorkProfileDialogShowing.value = isVisible
    }

    private fun setCurrentPopupType(popupType: PopupType?) {
        _currentPopup.value = popupType
        delayedHideCurrentPopupJob?.cancel()

        if (popupType != null) {
            delayedHideCurrentPopupJob =
                scope.launch {
                    delay(POPUP_AUTO_HIDE_TIMEOUT_MS)
                    setCurrentPopupType(null)
                }
        } else {
            delayedHideCurrentPopupJob = null
        }
    }

    private var delayedHideCurrentPopupJob: Job? = null

    /** Whether we can transition to a new scene based on a user gesture. */
    fun canChangeScene(toScene: SceneKey): Boolean {
        if (shadeInteractor.isAnyFullyExpanded.value) {
            communalSceneLogger.logSceneChangeRejection(
                from = currentScene.value,
                to = toScene,
                originalChangeReason = "user interaction",
                rejectionReason = "shade is open",
            )
            return false
        }

        return !communalSettingsInteractor.isV2FlagEnabled() ||
            isInteractionAllowedByFalsing(toScene).also { sceneChangeAllowed ->
                if (sceneChangeAllowed) {
                    communalSceneLogger.logSceneChangeRequested(
                        from = currentScene.value,
                        to = toScene,
                        reason = "user interaction",
                        isInstant = false,
                    )
                } else {
                    communalSceneLogger.logSceneChangeRejection(
                        from = currentScene.value,
                        to = toScene,
                        originalChangeReason = null,
                        rejectionReason = "false touch detected",
                    )
                }
            }
    }

    private fun isInteractionAllowedByFalsing(toScene: SceneKey): Boolean {
        // It's important that the falsing system is always queried, even if we aren't going to
        // enforce. This helps build the right signal in the system.
        val isFalseTouch = falsingInteractor.isFalseTouch(Classifier.GLANCEABLE_HUB_SWIPE)
        // Only enforce falsing if moving from the lockscreen to the glanceable hub.
        if (toScene != CommunalScenes.Communal) {
            return true
        }
        return !isFalseTouch
    }

    /**
     * Whether touches should be disabled in communal.
     *
     * This is needed because the notification shade does not block touches in blank areas and these
     * fall through to the glanceable hub, which we don't want.
     *
     * Using a StateFlow as the value does not necessarily change when hub becomes available.
     */
    val touchesAllowed: StateFlow<Boolean> =
        not(shadeInteractor.isAnyFullyExpanded)
            .stateIn(bgScope, SharingStarted.Eagerly, initialValue = false)

    /** The type of background to use for the hub. */
    val communalBackground: Flow<CommunalBackgroundType> =
        communalSettingsInteractor.communalBackground

    /**
     * Whether to show a temporary background for edit mode transition.
     *
     * This is for coordinating the transition to and from edit mode; the background hides the
     * activity entry and exit animations below the SystemUI window.
     */
    val showBackgroundForEditModeTransition: Flow<Boolean> =
        if (Flags.hubEditModeTransition())
            communalSceneInteractor.editModeState.map { it != null && it > EditModeState.STARTING }
        else flowOf(false)

    /** See [CommunalSettingsInteractor.isV2FlagEnabled] */
    fun v2FlagEnabled(): Boolean = communalSettingsInteractor.isV2FlagEnabled()

    val swipeToHubEnabled: Flow<Boolean> by lazy {
        val inAllowedDeviceState =
            if (v2FlagEnabled()) {
                communalSettingsInteractor.manualOpenEnabled
            } else {
                MutableStateFlow(swipeToHub)
            }

        if (v2FlagEnabled()) {
            val inAllowedKeyguardState =
                keyguardTransitionInteractor.startedKeyguardTransitionStep.map {
                    it.to == KeyguardState.LOCKSCREEN || it.to == KeyguardState.GLANCEABLE_HUB
                }
            allOf(
                inAllowedDeviceState,
                inAllowedKeyguardState,
                not(shadeInteractor.isAnyFullyExpanded),
            )
        } else {
            inAllowedDeviceState
        }
    }

    val swipeFromHubInLandscape: Flow<Boolean> = communalSceneInteractor.willRotateToPortrait

    fun onOrientationChange(orientation: Int) =
        communalSceneInteractor.setCommunalContainerOrientation(orientation)

    companion object {
        const val POPUP_AUTO_HIDE_TIMEOUT_MS = 12000L
    }
}

sealed class PopupType {
    data object CtaTile : PopupType()

    data object CustomizeWidgetButton : PopupType()
}
