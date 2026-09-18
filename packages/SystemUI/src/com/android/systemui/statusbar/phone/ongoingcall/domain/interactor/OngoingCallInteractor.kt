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

package com.android.systemui.statusbar.phone.ongoingcall.domain.interactor

import com.android.systemui.activity.data.repository.ActivityManagerRepository
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.log.LogBuffer
import com.android.systemui.log.core.Logger
import com.android.systemui.statusbar.notification.domain.interactor.ActiveNotificationsInteractor
import com.android.systemui.statusbar.notification.shared.ActiveNotificationModel
import com.android.systemui.statusbar.phone.ongoingcall.OngoingCallLog
import com.android.systemui.statusbar.phone.ongoingcall.shared.model.OngoingCallModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

/**
 * The central interactor for ongoing call state.
 *
 * This class is a global singleton that monitors call notifications and the visibility of call apps
 * to determine the state of an ongoing call. It emits:
 * - [OngoingCallModel.NoCall] when there is no call notification.
 * - [OngoingCallModel.InCall] when there is a call notification.
 *
 * The UI logic for showing, hiding, and interacting with the ongoing call chip in the status bar is
 * handled on a per-display basis by [OngoingCallStatusBarInteractor].
 *
 * @see OngoingCallStatusBarInteractor
 */
@SysUISingleton
class OngoingCallInteractor
@Inject
constructor(
    @Application private val scope: CoroutineScope,
    private val activityManagerRepository: ActivityManagerRepository,
    activeNotificationsInteractor: ActiveNotificationsInteractor,
    @OngoingCallLog private val logBuffer: LogBuffer,
) {
    private val logger = Logger(logBuffer, TAG)

    /** Tracks whether the call chip has been swiped away. */
    private val _isChipSwipedAway = MutableStateFlow(false)
    val isChipSwipedAway: StateFlow<Boolean> = _isChipSwipedAway.asStateFlow()

    /** The current state of ongoing calls. */
    val ongoingCallState: StateFlow<OngoingCallModel> =
        activeNotificationsInteractor.ongoingCallNotification
            .flatMapLatest { notification ->
                createOngoingCallStateFlow(notification = notification)
            }
            .stateIn(
                scope = scope,
                started = SharingStarted.WhileSubscribed(),
                initialValue = OngoingCallModel.NoCall,
            )

    private fun OngoingCallModel.willCallChipBeVisible() =
        this is OngoingCallModel.InCall && !isAppVisible

    private fun createOngoingCallStateFlow(
        notification: ActiveNotificationModel?
    ): Flow<OngoingCallModel> {
        if (notification == null) {
            logger.d("No active call notification - hiding chip")
            return flowOf(OngoingCallModel.NoCall)
        }

        return combine(
            flowOf(notification),
            activityManagerRepository.createIsAppVisibleFlow(
                creationUid = notification.uid,
                logger = logger,
                identifyingLogTag = TAG,
            ),
        ) { model, isVisible ->
            deriveOngoingCallState(model, isVisible)
        }
    }

    private fun deriveOngoingCallState(
        model: ActiveNotificationModel,
        isVisible: Boolean,
    ): OngoingCallModel {
        logger.d({
            "Active call detected: uid=$int1 startTime=$long1 hasIcon=$bool1 isAppVisible=$bool2"
        }) {
            int1 = model.uid
            long1 = model.whenTime
            bool1 = model.statusBarChipIconView != null
            bool2 = isVisible
        }
        return OngoingCallModel.InCall(
            startTimeMs = model.whenTime,
            notificationIconView = model.statusBarChipIconView,
            intent = model.contentIntent,
            notificationKey = model.key,
            appName = model.appName,
            promotedContent = model.promotedContent,
            requestedPromotion = model.requestedPromotion,
            isAppVisible = isVisible,
            notificationInstanceId = model.instanceId,
            packageName = model.packageName,
        )
    }

    companion object {
        private val TAG = "OngoingCall"
    }
}
