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

package com.android.systemui.statusbar.phone.ongoingcall.domain.interactor

import androidx.annotation.VisibleForTesting
import com.android.systemui.ambient.statusbar.shared.flag.OngoingActivityChipsOnDream
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.display.dagger.SystemUIDisplaySubcomponent
import com.android.systemui.display.dagger.SystemUIDisplaySubcomponent.DisplayAware
import com.android.systemui.display.dagger.SystemUIDisplaySubcomponent.PerDisplaySingleton
import com.android.systemui.keyguard.domain.interactor.KeyguardInteractor
import com.android.systemui.log.LogBuffer
import com.android.systemui.log.core.Logger
import com.android.systemui.statusbar.data.repository.StatusBarModePerDisplayRepository
import com.android.systemui.statusbar.gesture.SwipeStatusBarAwayGestureHandler
import com.android.systemui.statusbar.phone.ongoingcall.OngoingCallLog
import com.android.systemui.statusbar.phone.ongoingcall.shared.model.OngoingCallModel
import com.android.systemui.statusbar.window.StatusBarWindowControllerStore
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch

/**
 * A per-display interactor for the ongoing call chip in the status bar.
 *
 * This class is responsible for the UI logic of the ongoing call chip for a single display. It
 * consumes the global call state from [OngoingCallInteractor] and, for its display, decides when
 * the status bar needs to be force-shown for the chip and handles the swipe-away gesture.
 *
 * There is one instance of this class for each active display.
 *
 * @see OngoingCallInteractor
 */
@PerDisplaySingleton
class OngoingCallStatusBarInteractor
@Inject
constructor(
    @DisplayAware private val displayId: Int,
    @DisplayAware private val scope: CoroutineScope,
    @DisplayAware private val statusBarModeRepository: StatusBarModePerDisplayRepository,
    private val statusBarWindowControllerStore: StatusBarWindowControllerStore,
    @DisplayAware private val swipeStatusBarAwayGestureHandler: SwipeStatusBarAwayGestureHandler,
    private val ongoingCallInteractor: OngoingCallInteractor,
    keyguardInteractor: KeyguardInteractor,
    @OngoingCallLog private val logBuffer: LogBuffer,
    @Main private val mainCoroutineContext: CoroutineContext,
) : SystemUIDisplaySubcomponent.LifecycleListener {

    private val logger = Logger(logBuffer, TAG)

    /** Tracks whether the call chip has been swiped away. */
    private val _isChipSwipedAway = MutableStateFlow(false)
    val isChipSwipedAway: StateFlow<Boolean> = _isChipSwipedAway.asStateFlow()

    // TODO(b/400720280): maybe put this inside [OngoingCallModel].
    @VisibleForTesting
    val isStatusBarRequiredForOngoingCall =
        combine(
            ongoingCallInteractor.ongoingCallState,
            isChipSwipedAway,
            keyguardInteractor.isDreamingWithOverlay,
        ) { callState, chipSwipedAway, isDreamingWithOverlay ->
            callState.willCallChipBeVisible() &&
                // Don't force-show the status bar if the user has already swiped it away.
                !chipSwipedAway &&
                // Don't force-show the status bar if currently dreaming with overlay, as the
                // overlay render its own status bar
                !(OngoingActivityChipsOnDream.isEnabled && isDreamingWithOverlay)
        }

    // TODO(b/400720280): maybe put this inside [OngoingCallModel].
    @VisibleForTesting
    val isGestureListeningEnabled =
        combine(
            ongoingCallInteractor.ongoingCallState,
            statusBarModeRepository.isInFullscreenMode,
            isChipSwipedAway,
        ) { callState, isFullscreen, chipSwipedAway ->
            callState.willCallChipBeVisible() && !chipSwipedAway && isFullscreen
        }

    override fun start() {
        scope.launch(mainCoroutineContext) {
            ongoingCallInteractor.ongoingCallState
                .filterIsInstance<OngoingCallModel.NoCall>()
                .collect { _isChipSwipedAway.value = false }
        }

        scope.launch(mainCoroutineContext) {
            isStatusBarRequiredForOngoingCall.collect { statusBarRequired ->
                setStatusBarRequiredForOngoingCall(statusBarRequired)
            }
        }

        scope.launch(mainCoroutineContext) {
            isGestureListeningEnabled.collect { isEnabled -> updateGestureListening(isEnabled) }
        }
    }

    /** Callback that must run when the status bar is swiped while gesture listening is active. */
    @VisibleForTesting
    fun onStatusBarSwiped() {
        logger.d("Status bar chip swiped away")
        _isChipSwipedAway.value = true
    }

    private fun setStatusBarRequiredForOngoingCall(statusBarRequired: Boolean) {
        // TODO(b/382808183): Create a single repository that can be utilized in
        //  `statusBarModeRepositoryStore` and `statusBarWindowControllerStore` so we do not need
        //  two separate calls to force the status bar to stay visible.
        statusBarModeRepository.setOngoingProcessRequiresStatusBarVisible(statusBarRequired)

        statusBarWindowControllerStore
            .forDisplay(displayId)
            ?.setOngoingProcessRequiresStatusBarVisible(
                statusBarRequired,
                source = "OngoingCallStatusBarInteractor[displayId=$displayId]",
            )
    }

    private fun updateGestureListening(isEnabled: Boolean) {
        if (isEnabled) {
            swipeStatusBarAwayGestureHandler.addOnGestureDetectedCallback(TAG) { _ ->
                onStatusBarSwiped()
            }
        } else {
            swipeStatusBarAwayGestureHandler.removeOnGestureDetectedCallback(TAG)
        }
    }

    private fun OngoingCallModel.willCallChipBeVisible() =
        this is OngoingCallModel.InCall && !isAppVisible

    companion object {
        private val TAG = "OngoingCall"
    }
}
