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

package com.android.systemui.statusbar.chips.call.ui.viewmodel

import android.app.PendingIntent
import android.content.Context
import com.android.internal.jank.Cuj
import com.android.internal.logging.InstanceId
import com.android.systemui.animation.ActivityTransitionAnimator
import com.android.systemui.animation.ComposableControllerFactory
import com.android.systemui.common.shared.model.ContentDescription
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.log.LogBuffer
import com.android.systemui.log.core.Logger
import com.android.systemui.plugins.ActivityStarter
import com.android.systemui.res.R
import com.android.systemui.statusbar.chips.StatusBarChipLogTags.pad
import com.android.systemui.statusbar.chips.StatusBarChipsLog
import com.android.systemui.statusbar.chips.StatusBarChipsReturnAnimations
import com.android.systemui.statusbar.chips.call.domain.interactor.CallChipInteractor
import com.android.systemui.statusbar.chips.notification.domain.interactor.StatusBarNotificationChipsInteractor
import com.android.systemui.statusbar.chips.ui.model.Chronometer
import com.android.systemui.statusbar.chips.ui.model.ColorsModel
import com.android.systemui.statusbar.chips.ui.model.EventTime
import com.android.systemui.statusbar.chips.ui.model.OngoingActivityChipModel
import com.android.systemui.statusbar.chips.ui.viewmodel.ChipTransitionHelper.Companion.TransitionAwareChipModel
import com.android.systemui.statusbar.chips.ui.viewmodel.ChipTransitionHelper.Companion.TransitionState
import com.android.systemui.statusbar.chips.ui.viewmodel.ChipTransitionHelper.Companion.buildTransitionManager
import com.android.systemui.statusbar.chips.ui.viewmodel.ChipTransitionHelper.Companion.shouldChipBeHidden
import com.android.systemui.statusbar.chips.ui.viewmodel.OngoingActivityChipViewModel
import com.android.systemui.statusbar.chips.ui.viewmodel.OngoingActivityChipViewModel.Companion.createNotificationToggleClickBehavior
import com.android.systemui.statusbar.chips.ui.viewmodel.OngoingActivityChipViewModel.Companion.isShowingHeadsUpFromChipTap
import com.android.systemui.statusbar.chips.uievents.StatusBarChipsUiEventLogger
import com.android.systemui.statusbar.notification.domain.interactor.HeadsUpNotificationInteractor
import com.android.systemui.statusbar.notification.domain.model.TopPinnedState
import com.android.systemui.statusbar.phone.ongoingcall.shared.model.OngoingCallModel
import com.android.systemui.util.time.SystemClock
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** View model for the ongoing phone call chip shown in the status bar. */
@SysUISingleton
class CallChipViewModel
@Inject
constructor(
    @Main private val context: Context,
    @Application private val scope: CoroutineScope,
    interactor: CallChipInteractor,
    private val notifChipsInteractor: StatusBarNotificationChipsInteractor,
    headsUpNotificationInteractor: HeadsUpNotificationInteractor,
    systemClock: SystemClock,
    private val activityStarter: ActivityStarter,
    @StatusBarChipsLog private val logBuffer: LogBuffer,
    private val uiEventLogger: StatusBarChipsUiEventLogger,
) : OngoingActivityChipViewModel {
    private val logger = Logger(logBuffer, "CallChipVM".pad())
    /** The transition cookie used to register and unregister launch and return animations. */
    private val cookie =
        ActivityTransitionAnimator.TransitionCookie("${CallChipViewModel::class.java}")

    /**
     * Used internally to determine when a launch or return animation is in progress, as these
     * require special handling.
     */
    private val transitionState: MutableStateFlow<TransitionState> =
        MutableStateFlow(TransitionState.NoTransition)

    // Since we're combining the chip state and the transition state flows, getting the old value by
    // using [pairwise()] would confuse things. This is because if the calculation is triggered by
    // a change in transition state, the chip state will still show the previous and current values,
    // making it difficult to figure out what actually changed. Instead we cache the old value here,
    // so that at each update we can keep track of what actually changed.
    private var latestState: OngoingCallModel = OngoingCallModel.NoCall
    private var latestTransitionState: TransitionState = TransitionState.NoTransition

    private val chipWithReturnAnimation: StateFlow<OngoingActivityChipModel> =
        if (StatusBarChipsReturnAnimations.isEnabled) {
            combine(
                    interactor.ongoingCallState,
                    transitionState,
                    headsUpNotificationInteractor.statusBarHeadsUpState,
                ) { newState, newTransitionState, headsUpState ->
                    val oldState = latestState
                    latestState = newState
                    val oldTransitionState = latestTransitionState
                    latestTransitionState = newTransitionState

                    // Note: This log might be too noisy with HUN transitions.
                    logger.d({
                        "Call chip state updated: $str1" +
                            " oldTransitionState=$str2" +
                            " newTransitionState=$str3"
                    }) {
                        str1 = "oldState=${oldState.logString()} newState=${newState.logString()}"
                        str2 = oldTransitionState::class.simpleName
                        str3 = newTransitionState::class.simpleName
                    }

                    when (newState) {
                        is OngoingCallModel.NoCall -> {
                            val transitionManager =
                                buildTransitionManager(
                                    chip = TransitionAwareChipModel.Inactive(cookie),
                                    transitionState = newTransitionState,
                                    updateTransitionState = { updatedState ->
                                        transitionState.value = updatedState
                                    },
                                    scope = scope,
                                    factory = transitionControllerFactory,
                                    activityStarter = activityStarter,
                                )
                            transitionControllerFactory = transitionManager?.controllerFactory
                            OngoingActivityChipModel.Inactive(transitionManager = transitionManager)
                        }

                        is OngoingCallModel.InCall -> {
                            prepareChip(
                                oldState = oldState,
                                newState = newState,
                                headsUpState = headsUpState,
                                systemClock = systemClock,
                                oldTransitionState = oldTransitionState,
                                newTransitionState = newTransitionState,
                            )
                        }
                    }
                }
                .stateIn(
                    scope,
                    SharingStarted.WhileSubscribed(),
                    OngoingActivityChipModel.Inactive(),
                )
        } else {
            MutableStateFlow(OngoingActivityChipModel.Inactive()).asStateFlow()
        }

    private val chipLegacy: StateFlow<OngoingActivityChipModel> =
        if (!StatusBarChipsReturnAnimations.isEnabled) {
            combine(
                    interactor.ongoingCallState,
                    headsUpNotificationInteractor.statusBarHeadsUpState,
                ) { callState, headsUpState ->
                    when (callState) {
                        is OngoingCallModel.NoCall -> OngoingActivityChipModel.Inactive()
                        is OngoingCallModel.InCall -> {
                            prepareChip(
                                oldState = null,
                                newState = callState,
                                headsUpState = headsUpState,
                                systemClock = systemClock,
                            )
                        }
                    }
                }
                .stateIn(
                    scope,
                    SharingStarted.WhileSubscribed(),
                    OngoingActivityChipModel.Inactive(),
                )
        } else {
            MutableStateFlow(OngoingActivityChipModel.Inactive()).asStateFlow()
        }

    override val chip: StateFlow<OngoingActivityChipModel> =
        if (StatusBarChipsReturnAnimations.isEnabled) {
            chipWithReturnAnimation
        } else {
            chipLegacy
        }

    /**
     * The controller factory that the call chip uses to register and unregister its transition
     * animations.
     */
    private var transitionControllerFactory: ComposableControllerFactory? = null

    /** Builds an [OngoingActivityChipModel] from all the relevant information. */
    private fun prepareChip(
        oldState: OngoingCallModel?,
        newState: OngoingCallModel.InCall,
        headsUpState: TopPinnedState,
        systemClock: SystemClock,
        oldTransitionState: TransitionState = TransitionState.NoTransition,
        newTransitionState: TransitionState = TransitionState.NoTransition,
    ): OngoingActivityChipModel.Active {
        val key = "$KEY_PREFIX${newState.notificationKey}"
        val contentDescription = getContentDescription(newState.appName)
        val icon =
            OngoingActivityChipModel.ChipIcon.StatusBarNotificationIcon(
                newState.notificationKey,
                contentDescription,
            )

        val colors = ColorsModel.AccentThemed
        val intent = newState.intent
        val instanceId = newState.notificationInstanceId

        val content =
            when {
                newState.startTimeMs <= 0L -> {
                    // If the start time is invalid, don't show a timer and show just an icon.
                    // See b/192379214.
                    OngoingActivityChipModel.Content.IconOnly
                }
                headsUpState.isShowingHeadsUpFromChipTap(
                    notificationKey = newState.notificationKey
                ) -> {
                    // If the user tapped this chip to show the HUN, we want to just show the icon
                    // because the HUN will show the rest of the information.
                    // Similar behavior to [NotifChipsViewModel].
                    OngoingActivityChipModel.Content.IconOnly
                }
                else -> {
                    val startTimeInElapsedRealtime =
                        newState.startTimeMs - systemClock.currentTimeMillis() +
                            systemClock.elapsedRealtime()
                    OngoingActivityChipModel.Content.Timer(
                        value =
                            Chronometer.Running(
                                EventTime.ElapsedRealtime(startTimeInElapsedRealtime)
                            ),
                        timeSource = systemClock,
                    )
                }
            }

        val oldChip =
            oldState?.let {
                if (oldState is OngoingCallModel.NoCall) {
                    TransitionAwareChipModel.Inactive(cookie)
                } else {
                    TransitionAwareChipModel.Active(
                        cookie = cookie,
                        component =
                            if (oldState is OngoingCallModel.InCall) {
                                oldState.intent?.intent?.component
                            } else {
                                null
                            },
                        isAppVisible =
                            if (oldState is OngoingCallModel.InCall) {
                                oldState.isAppVisible
                            } else {
                                false
                            },
                    )
                }
            }
        val newChip =
            TransitionAwareChipModel.Active(
                cookie = cookie,
                component = newState.intent?.intent?.component,
                isAppVisible = newState.isAppVisible,
                launchCujType = Cuj.CUJ_STATUS_BAR_APP_LAUNCH_FROM_CALL_CHIP,
                returnCujType = Cuj.CUJ_STATUS_BAR_APP_RETURN_TO_CALL_CHIP,
            )

        val isHidden =
            if (!StatusBarChipsReturnAnimations.isEnabled) {
                newState.isAppVisible
            } else {
                shouldChipBeHidden(oldChip, newChip, oldTransitionState, newTransitionState)
            }

        val transitionManager =
            buildTransitionManager(
                chip = newChip,
                transitionState = newTransitionState,
                updateTransitionState = { updatedState -> transitionState.value = updatedState },
                scope = scope,
                factory = transitionControllerFactory,
                activityStarter = activityStarter,
            )
        transitionControllerFactory = transitionManager?.controllerFactory

        return OngoingActivityChipModel.Active(
            key = key,
            notificationKey = newState.notificationKey,
            icon = icon,
            managingPackageName = newState.packageName,
            content = content,
            colors = colors,
            clickBehavior =
                getClickBehavior(
                    intent = intent,
                    instanceId = instanceId,
                    notificationKey = newState.notificationKey,
                    headsUpState = headsUpState,
                ),
            isHidden = isHidden,
            transitionManager = transitionManager,
            instanceId = instanceId,
        )
    }

    private fun getClickBehavior(
        intent: PendingIntent?,
        instanceId: InstanceId?,
        notificationKey: String,
        headsUpState: TopPinnedState,
    ): OngoingActivityChipModel.ClickBehavior {
        return createNotificationToggleClickBehavior(
            applicationScope = scope,
            notifChipsInteractor = notifChipsInteractor,
            logger = logger,
            notificationKey = notificationKey,
            isShowingHeadsUpFromChipTap = headsUpState.isShowingHeadsUpFromChipTap(notificationKey),
        )
    }

    private fun logChipTapped(key: String, instanceId: InstanceId?) {
        logger.i({ "Chip clicked" }) {}
        uiEventLogger.logChipTapToShow(key = key, instanceId = instanceId)
    }

    private fun getContentDescription(appName: String): ContentDescription {
        val ongoingCallDescription = context.getString(R.string.ongoing_call_content_description)
        return ContentDescription.Loaded(
            context.getString(
                R.string.accessibility_desc_notification_icon,
                appName,
                ongoingCallDescription,
            )
        )
    }

    companion object {
        private val phoneIcon =
            Icon.Resource(
                com.android.internal.R.drawable.ic_phone,
                ContentDescription.Resource(R.string.ongoing_call_content_description),
            )

        const val KEY_PREFIX = "callChip-"
    }
}
