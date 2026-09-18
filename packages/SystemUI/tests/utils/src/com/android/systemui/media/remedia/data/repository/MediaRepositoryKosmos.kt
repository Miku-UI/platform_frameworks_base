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

package com.android.systemui.media.remedia.data.repository

import android.content.applicationContext
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.runtime.snapshots.Snapshot
import com.android.internal.logging.InstanceId
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.applicationCoroutineScope
import com.android.systemui.kosmos.runCurrent
import com.android.systemui.kosmos.testDispatcher
import com.android.systemui.media.controls.shared.model.MediaAction
import com.android.systemui.media.controls.shared.model.MediaButton
import com.android.systemui.media.controls.shared.model.MediaData
import com.android.systemui.media.controls.util.fakeMediaControllerFactory
import com.android.systemui.media.remedia.data.model.MediaDataModel
import com.android.systemui.media.remedia.domain.interactor.mediaInteractor
import com.android.systemui.media.remedia.shared.model.MediaSessionState
import com.android.systemui.res.R
import com.android.systemui.statusbar.notification.collection.provider.visualStabilityProvider
import com.android.systemui.util.settings.fakeSettings
import com.android.systemui.util.time.systemClock

val Kosmos.mediaRepository by
    Kosmos.Fixture {
        MediaRepositoryImpl(
            applicationContext = applicationContext,
            applicationScope = applicationCoroutineScope,
            backgroundDispatcher = testDispatcher,
            visualStabilityProvider = visualStabilityProvider,
            systemClock = systemClock,
            secureSettings = fakeSettings,
            mediaControllerFactory = fakeMediaControllerFactory,
        )
    }

val Kosmos.fakeMediaRepository by
    Kosmos.Fixture {
        FakeMediaRepository(
            applicationContext = applicationContext,
            applicationScope = applicationCoroutineScope,
            backgroundDispatcher = testDispatcher,
            fakeUserSettings = fakeSettings,
        )
    }

val Kosmos.fakeActiveMedia by
    Kosmos.Fixture {
        MediaDataModel(
            instanceId = InstanceId.fakeInstanceId(1),
            appUid = 1,
            packageName = "com.fake.music.app",
            appName = "Fake_Music_Player",
            appIcon = Icon.Resource(resId = R.drawable.ic_cake, contentDescription = null),
            background = null,
            title = "Fake title",
            subtitle = "Fake subtitle",
            colorScheme = null,
            notificationActions = emptyList(),
            notificationActionsCompressed = listOf(0),
            playbackStateActions =
                MediaButton(
                    playOrPause = mediaPauseActionButton,
                    nextOrCustom = mediaNextActionButton,
                    prevOrCustom = mediaPrevActionButton,
                ),
            outputDevice = null,
            clickIntent = null,
            state = MediaSessionState.Playing,
            durationMs = 60_000,
            positionMs = 20_000,
            canShowSeekbar = true,
            canBeScrubbed = true,
            canBeDismissed = false,
            isActive = true,
            isResume = false,
            resumeAction = null,
            isExplicit = true,
            suggestionData = null,
            token = null,
            needsImmediateRemoval = false,
        )
    }

val Kosmos.fakeActiveMediaData by
    Kosmos.Fixture {
        MediaData(
            userId = 0,
            app = "Fake_Music_Player",
            artist = "Fake artist",
            song = "Fake song",
            artwork = null,
            semanticActions =
                MediaButton(
                    playOrPause = mediaPauseActionButton,
                    nextOrCustom = mediaNextActionButton,
                    prevOrCustom = mediaPrevActionButton,
                ),
            packageName = applicationContext.packageName,
            clickIntent = null,
            device = null,
            active = true,
            resumeAction = null,
            playbackLocation = 0,
            resumption = false,
            notificationKey = "fake_notification_key_1",
            hasCheckedForResume = false,
            isPlaying = true,
            isClearable = true,
            instanceId = InstanceId.fakeInstanceId(1),
            appUid = 1,
        )
    }

val Kosmos.fakePausedMediaDataWithCustomActions by
    Kosmos.Fixture {
        MediaData(
            userId = 0,
            app = "Fake_Audio_Player",
            artist = "Fake artist",
            song = "Fake song",
            artwork = null,
            packageName = applicationContext.packageName,
            semanticActions =
                MediaButton(
                    playOrPause = mediaPlayActionButton,
                    nextOrCustom = mediaNextActionButton,
                    prevOrCustom = mediaPrevActionButton,
                    custom0 =
                        MediaAction(
                            icon =
                                AppCompatResources.getDrawable(
                                    this.applicationContext,
                                    R.drawable.cloud,
                                ),
                            action = null,
                            contentDescription = "Custom0",
                            background = null,
                            rebindId = 4,
                        ),
                    custom1 =
                        MediaAction(
                            icon =
                                AppCompatResources.getDrawable(
                                    this.applicationContext,
                                    R.drawable.hearing,
                                ),
                            action = null,
                            contentDescription = "Custom1",
                            background = null,
                            rebindId = 5,
                        ),
                ),
            notificationKey = "fake_notification_key_2",
            isPlaying = false,
            instanceId = InstanceId.fakeInstanceId(2),
            appUid = 2,
        )
    }

val Kosmos.fakeResumableMediaData by
    Kosmos.Fixture {
        MediaData(
            userId = 0,
            app = "Fake_Podcast_Player",
            artist = "Fake artist",
            song = "Fake song",
            packageName = applicationContext.packageName,
            semanticActions = MediaButton(playOrPause = mediaPlayActionButton),
            notificationKey = "fake_notification_key_3",
            resumeAction = {},
            resumption = true,
            hasCheckedForResume = true,
            isPlaying = false,
            isClearable = true,
            instanceId = InstanceId.fakeInstanceId(3),
            active = false,
            appUid = 3,
        )
    }

val Kosmos.fakeDismissibleMedia by
    Kosmos.Fixture {
        fakeActiveMedia.copy(
            instanceId = InstanceId.fakeInstanceId(3),
            appName = "Fake_Dismissible_Music_Player",
            canBeDismissed = true,
            state = MediaSessionState.Paused,
        )
    }

val Kosmos.fakeDismissibleMediaData by
    Kosmos.Fixture {
        fakeActiveMediaData.copy(
            instanceId = InstanceId.fakeInstanceId(3),
            app = "Fake_Dismissible_Music_Player",
            isClearable = true,
            isPlaying = false,
        )
    }

val Kosmos.mediaPlayActionButton: MediaAction
    get() =
        MediaAction(
            icon =
                AppCompatResources.getDrawable(
                    this.applicationContext,
                    R.drawable.ic_media_play_button,
                ),
            action = null,
            contentDescription = "Play",
            background =
                AppCompatResources.getDrawable(
                    this.applicationContext,
                    R.drawable.ic_media_play_button_container,
                ),
            rebindId = 1,
        )

val Kosmos.mediaPauseActionButton: MediaAction
    get() =
        MediaAction(
            icon =
                AppCompatResources.getDrawable(
                    this.applicationContext,
                    R.drawable.ic_media_pause_button,
                ),
            action = null,
            contentDescription = "Pause",
            background =
                AppCompatResources.getDrawable(
                    this.applicationContext,
                    R.drawable.ic_media_pause_button_container,
                ),
            rebindId = 1,
        )
val Kosmos.mediaNextActionButton: MediaAction
    get() =
        MediaAction(
            icon =
                AppCompatResources.getDrawable(this.applicationContext, R.drawable.ic_media_next),
            action = null,
            contentDescription = "Next",
            background = null,
            rebindId = 2,
        )
val Kosmos.mediaPrevActionButton: MediaAction
    get() =
        MediaAction(
            icon =
                AppCompatResources.getDrawable(this.applicationContext, R.drawable.ic_media_prev),
            action = null,
            contentDescription = "Prev",
            background = null,
            rebindId = 3,
        )

fun Kosmos.setFakeCurrentMedia(mediaList: List<MediaDataModel>) {
    fakeMediaRepository.setFakeCurrentMedia(mediaList)
}
fun Kosmos.setFakeCurrentMediaData(mediaDataList: List<MediaData>) {
    Snapshot.withMutableSnapshot {
        mediaDataList.forEach { data -> mediaRepository.addCurrentUserMediaEntry(data) }
        mediaInteractor.reorderMedia()
        runCurrent()
    }
}
