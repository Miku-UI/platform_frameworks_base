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

package com.android.systemui.media.remedia.domain.interactor

import android.app.ActivityOptions
import android.app.BroadcastOptions
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.session.MediaSession
import android.os.UserHandle
import android.provider.Settings
import android.util.Log
import com.android.internal.jank.Cuj
import com.android.internal.logging.InstanceId
import com.android.media.flags.Flags.fixOutputSwitcherMultiuserSupport
import com.android.settingslib.media.LocalMediaManager.MediaDeviceState
import com.android.systemui.ActivityIntentHelper
import com.android.systemui.Flags
import com.android.systemui.animation.DialogCuj
import com.android.systemui.animation.DialogTransitionAnimator
import com.android.systemui.animation.Expandable
import com.android.systemui.common.shared.model.ContentDescription
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.common.shared.model.asIcon
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.deviceentry.domain.interactor.DeviceEntryInteractor
import com.android.systemui.keyguard.domain.interactor.KeyguardTransitionInteractor
import com.android.systemui.keyguard.shared.model.KeyguardState
import com.android.systemui.media.controls.domain.pipeline.MediaDataProcessor
import com.android.systemui.media.controls.domain.pipeline.getNotificationActions
import com.android.systemui.media.controls.shared.model.MediaAction
import com.android.systemui.media.controls.shared.model.SuggestionData
import com.android.systemui.media.dialog.MediaOutputDialogManager
import com.android.systemui.media.remedia.data.model.MediaDataModel
import com.android.systemui.media.remedia.data.repository.MediaRepository
import com.android.systemui.media.remedia.domain.model.MediaActionModel
import com.android.systemui.media.remedia.domain.model.MediaOutputDeviceModel
import com.android.systemui.media.remedia.domain.model.MediaSessionModel
import com.android.systemui.media.remedia.shared.model.MediaCardActionButtonLayout
import com.android.systemui.media.remedia.shared.model.MediaColorScheme
import com.android.systemui.media.remedia.shared.model.MediaSessionState
import com.android.systemui.plugins.ActivityStarter
import com.android.systemui.res.R
import com.android.systemui.statusbar.NotificationLockscreenUserManager
import com.android.systemui.statusbar.policy.KeyguardStateController
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Defines interface for classes that can provide business logic in the domain of the media controls
 * element.
 */
interface MediaInteractor {

    /** The list of sessions. Needs to be backed by a compose snapshot state. */
    val sessions: List<MediaSessionModel>

    /** Index of the current visible media session */
    val currentCarouselIndex: Int

    /** Whether media carousel should show first media session. */
    val shouldScrollToFirst: Boolean

    /** Whether guts state should show on carousel. */
    val isGutsVisible: Boolean

    /** Are there any media notifications active? */
    val hasActiveMedia: Boolean

    /** Are there any media entries, including inactive ones? */
    val hasAnyMedia: Boolean

    val allowMediaOnLockscreen: Boolean

    /** Is the device on lockscreen? */
    val isOnLockscreen: Flow<Boolean>

    /** Seek to [to], in milliseconds on the media session with the given [sessionKey]. */
    fun seek(sessionKey: InstanceId, to: Long)

    /** Hide the representation of the media session with the given [sessionKey]. */
    fun hide(sessionKey: InstanceId, delayMs: Long, userInitiated: Boolean)

    /** Open media settings. */
    fun openMediaSettings()

    fun reorderMedia()

    fun storeCurrentCarouselIndex(index: Int)

    fun resetScrollToFirst()

    fun setIsGutsVisible(isGutsVisible: Boolean)
}

@SysUISingleton
class MediaInteractorImpl
@Inject
constructor(
    @Application val applicationContext: Context,
    @Application val applicationScope: CoroutineScope,
    val repository: MediaRepository,
    val mediaDataProcessor: MediaDataProcessor,
    private val keyguardStateController: KeyguardStateController,
    private val activityStarter: ActivityStarter,
    private val activityIntentHelper: ActivityIntentHelper,
    private val lockscreenUserManager: NotificationLockscreenUserManager,
    private val mediaOutputDialogManager: MediaOutputDialogManager,
    private val deviceEntryInteractor: DeviceEntryInteractor,
    private val keyguardTransitionInteractor: KeyguardTransitionInteractor,
) : MediaInteractor {

    override val sessions: List<MediaSessionModel>
        get() =
            repository.currentMedia.mapNotNull {
                if (it.needsImmediateRemoval) {
                    hide(it.instanceId, 0, repository.isSwipedAway)
                    null
                } else {
                    toMediaSessionModel(it)
                }
            }

    override val currentCarouselIndex: Int
        get() = repository.currentCarouselIndex

    override val shouldScrollToFirst: Boolean
        get() = repository.shouldScrollToFirst

    override val isGutsVisible: Boolean
        get() = repository.isGutsVisible

    /** Are there any media notifications active? */
    override val hasActiveMedia: Boolean
        get() = repository.currentMedia.any { dataModel -> dataModel.isActive }

    /** Are there any media entries, including inactive ones? */
    override val hasAnyMedia: Boolean
        get() = repository.currentMedia.isNotEmpty()

    override val allowMediaOnLockscreen: Boolean
        get() = repository.allowMediaOnLockscreen

    override val isOnLockscreen: Flow<Boolean> = deviceEntryInteractor.isDeviceEntered.map { !it }

    init {
        if (Flags.mediaControlsInCompose() && !Flags.sceneContainer()) {
            applicationScope.launch {
                keyguardTransitionInteractor.isFinishedIn(KeyguardState.LOCKSCREEN).collect {
                    currentlyInLockscreen ->
                    if (!currentlyInLockscreen && repository.isReorderingAllowed) {
                        resetState()
                    }
                }
            }
        }
        repository.visualStabilityListenerFlow.onEach { resetState() }.launchIn(applicationScope)
    }

    /**
     * Clear players that are due for removal and reset the carousel state. This method is intended
     * to happen off screen.
     */
    private fun resetState() {
        repository.keysNeedRemoval.forEach { key ->
            hide(key, delayMs = 0, repository.isUserInitiatedRemovalQueued)
        }
        repository.cleanKeysNeedRemoval()
        reorderMedia()
    }

    override fun seek(sessionKey: InstanceId, to: Long) {
        repository.seek(sessionKey, to)
    }

    override fun hide(sessionKey: InstanceId, delayMs: Long, userInitiated: Boolean) {
        mediaDataProcessor.dismissMediaData(sessionKey, delayMs, userInitiated)
    }

    override fun openMediaSettings() {
        activityStarter.startActivity(settingsIntent, true)
    }

    override fun reorderMedia() {
        repository.reorderMedia()
    }

    override fun storeCurrentCarouselIndex(index: Int) {
        repository.storeCarouselIndex(index)
    }

    override fun resetScrollToFirst() {
        repository.resetScrollToFirst()
    }

    override fun setIsGutsVisible(isGutsVisible: Boolean) {
        repository.storeIsGutsVisible(isGutsVisible)
    }

    private fun toMediaSessionModel(dataModel: MediaDataModel): MediaSessionModel {
        return object : MediaSessionModel {
            override val key
                get() = dataModel.instanceId

            override val uid: Int
                get() = dataModel.appUid

            override val packageName: String
                get() = dataModel.packageName

            override val appName
                get() = dataModel.appName

            override val appIcon: Icon
                get() = dataModel.appIcon

            override val background: Icon?
                get() = dataModel.background

            override val colorScheme: MediaColorScheme?
                get() = dataModel.colorScheme

            override val title: String
                get() = dataModel.title

            override val subtitle: String
                get() = dataModel.subtitle

            override val isExplicit: Boolean
                get() = dataModel.isExplicit

            override val onClick: (Expandable) -> Unit
                get() = { expandable ->
                    dataModel.clickIntent?.let { startClickIntent(expandable, it) }
                }

            override val isActive: Boolean
                get() = dataModel.isActive

            override val canBeHidden: Boolean
                get() = dataModel.canBeDismissed

            override val canShowSeekbar: Boolean
                get() = dataModel.canShowSeekbar

            override val canBeScrubbed: Boolean
                get() = dataModel.canBeScrubbed

            override val state: MediaSessionState
                get() = dataModel.state

            override val positionMs: Long
                get() = dataModel.positionMs

            override val durationMs: Long
                get() = dataModel.durationMs

            override val outputDevice: MediaOutputDeviceModel
                get() =
                    with(dataModel.outputDevice) {
                        MediaOutputDeviceModel(
                            name = this?.name,
                            // Set home devices icon as default.
                            icon =
                                this?.icon?.let { Icon.Loaded(it, contentDescription = null) }
                                    ?: Icon.Resource(
                                        R.drawable.ic_media_home_devices,
                                        contentDescription = null,
                                    ),
                            isInProgress = false,
                            onClick = { expandable ->
                                startOutputSwitcherClick(dataModel, expandable)
                            },
                        )
                    }

            override val suggestedOutputDevice: MediaOutputDeviceModel?
                get() = dataModel.suggestionData?.toDeviceModel()

            override val actionButtonLayout: MediaCardActionButtonLayout
                get() =
                    dataModel.playbackStateActions?.let {
                        MediaCardActionButtonLayout.WithPlayPause
                    }
                        ?: MediaCardActionButtonLayout.SecondaryActionsOnly(
                            dataModel.notificationActionsCompressed
                        )

            override val playPauseAction: MediaActionModel
                get() =
                    dataModel.playbackStateActions
                        ?.playOrPause
                        ?.getMediaActionModel(R.id.actionPlayPause) ?: MediaActionModel.None

            override val leftAction: MediaActionModel
                get() =
                    dataModel.playbackStateActions?.let {
                        it.prevOrCustom?.getMediaActionModel(R.id.actionPrev)
                            ?: if (it.reservePrev) {
                                MediaActionModel.ReserveSpace
                            } else {
                                MediaActionModel.None
                            }
                    } ?: MediaActionModel.None

            override val rightAction: MediaActionModel
                get() =
                    dataModel.playbackStateActions?.let {
                        it.nextOrCustom?.getMediaActionModel(R.id.actionNext)
                            ?: if (it.reserveNext) {
                                MediaActionModel.ReserveSpace
                            } else {
                                MediaActionModel.None
                            }
                    } ?: MediaActionModel.None

            override val additionalActions: List<MediaActionModel.Action>
                get() =
                    dataModel.playbackStateActions?.let { playbackActions ->
                        listOfNotNull(
                            playbackActions.custom0?.getMediaActionModel(R.id.action0)
                                as? MediaActionModel.Action,
                            playbackActions.custom1?.getMediaActionModel(R.id.action1)
                                as? MediaActionModel.Action,
                        )
                    }
                        ?: getNotificationActions(dataModel.notificationActions, activityStarter)
                            .mapNotNull { it.getMediaActionModel() as? MediaActionModel.Action }

            override val onSessionVisible: (areDeviceChipsVisible: Boolean) -> Unit
                get() = { areDeviceChipsVisible ->
                    // The device suggestion chip is not show in the resumption player or when the
                    // presentation mode is compact or thumbnail.
                    if (!dataModel.isResume && areDeviceChipsVisible) {
                        dataModel.suggestionData?.onSuggestionSpaceVisible?.run()
                    }
                }
        }
    }

    private fun MediaAction.getMediaActionModel(id: Int? = null): MediaActionModel {
        return icon?.let { drawable ->
            MediaActionModel.Action(
                id = id ?: -1,
                icon =
                    Icon.Loaded(
                        drawable = drawable,
                        contentDescription =
                            contentDescription?.let { ContentDescription.Loaded(it.toString()) },
                    ),
                onClick = { action?.run() },
            )
        } ?: MediaActionModel.None
    }

    private fun SuggestionData.toDeviceModel(): MediaOutputDeviceModel? {
        if (suggestedMediaDeviceData == null) {
            return null
        }
        return MediaOutputDeviceModel(
            suggestedMediaDeviceData.name,
            suggestedMediaDeviceData.icon.asIcon(),
            suggestedMediaDeviceData.connectionState == MediaDeviceState.STATE_CONNECTING,
            onClick = { suggestedMediaDeviceData.connect() },
        )
    }

    fun startClickIntent(expandable: Expandable, clickIntent: PendingIntent) {
        if (!launchOverLockscreen(expandable, clickIntent)) {
            activityStarter.postStartActivityDismissingKeyguard(
                clickIntent,
                expandable.activityTransitionController(Cuj.CUJ_SHADE_APP_LAUNCH_FROM_MEDIA_PLAYER),
            )
        }
    }

    private fun launchOverLockscreen(
        expandable: Expandable?,
        pendingIntent: PendingIntent,
    ): Boolean {
        val showOverLockscreen =
            keyguardStateController.isShowing &&
                activityIntentHelper.wouldPendingShowOverLockscreen(
                    pendingIntent,
                    lockscreenUserManager.currentUserId,
                )
        if (showOverLockscreen) {
            try {
                if (expandable != null) {
                    activityStarter.startPendingIntentMaybeDismissingKeyguard(
                        pendingIntent,
                        /* intentSentUiThreadCallback = */ null,
                        expandable.activityTransitionController(
                            Cuj.CUJ_SHADE_APP_LAUNCH_FROM_MEDIA_PLAYER
                        ),
                    )
                } else {
                    val options = BroadcastOptions.makeBasic()
                    options.isInteractive = true
                    options.pendingIntentBackgroundActivityStartMode =
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS
                    pendingIntent.send(options.toBundle())
                }
            } catch (e: PendingIntent.CanceledException) {
                Log.e(TAG, "pending intent was canceled")
            }
            return true
        }
        return false
    }

    private fun startOutputSwitcherClick(dataModel: MediaDataModel, expandable: Expandable?) {
        dataModel.outputDevice?.intent?.let { startDeviceIntent(dataModel.instanceId, it) }
            ?: startMediaOutputDialog(
                expandable,
                dataModel.packageName,
                dataModel.appUid,
                dataModel.token,
            )
    }

    private fun startMediaOutputDialog(
        expandable: Expandable?,
        packageName: String,
        appUid: Int,
        token: MediaSession.Token? = null,
    ) {
        if (fixOutputSwitcherMultiuserSupport()) {
            mediaOutputDialogManager.createAndShowWithController(
                packageName,
                aboveStatusBar = true,
                expandable?.dialogController(),
                token = token,
                userHandle = UserHandle.getUserHandleForUid(appUid),
            )
        } else {
            mediaOutputDialogManager.createAndShowWithController(
                packageName,
                aboveStatusBar = true,
                expandable?.dialogController(),
                token = token,
            )
        }
    }

    private fun Expandable.dialogController(): DialogTransitionAnimator.Controller? {
        return dialogTransitionController(
            cuj =
                DialogCuj(Cuj.CUJ_SHADE_DIALOG_OPEN, MediaOutputDialogManager.INTERACTION_JANK_TAG)
        )
    }

    private fun startDeviceIntent(instanceId: InstanceId, deviceIntent: PendingIntent) {
        if (deviceIntent.isActivity) {
            if (!launchOverLockscreen(expandable = null, deviceIntent)) {
                activityStarter.postStartActivityDismissingKeyguard(deviceIntent)
            }
        } else {
            Log.w(TAG, "Device pending intent of instanceId=$instanceId is not an activity.")
        }
    }

    companion object {
        private const val TAG = "MediaInteractor"
        private val settingsIntent: Intent = Intent(Settings.ACTION_MEDIA_CONTROLS_SETTINGS)
    }
}
