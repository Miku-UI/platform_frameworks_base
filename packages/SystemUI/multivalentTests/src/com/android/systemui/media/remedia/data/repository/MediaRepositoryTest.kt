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

package com.android.systemui.media.remedia.data.repository

import android.content.packageManager
import android.content.pm.ApplicationInfo
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.UserHandle
import android.provider.Settings
import androidx.test.annotation.UiThreadTest
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.internal.logging.InstanceId
import com.android.systemui.SysuiTestCase
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.coroutines.collectLastValue
import com.android.systemui.kosmos.testScope
import com.android.systemui.media.controls.shared.model.MediaData
import com.android.systemui.media.controls.util.fakeMediaControllerFactory
import com.android.systemui.media.remedia.data.model.MediaDataModel
import com.android.systemui.res.R
import com.android.systemui.statusbar.notification.collection.provider.mockVisualStabilityProvider
import com.android.systemui.statusbar.notification.collection.provider.visualStabilityProvider
import com.android.systemui.testKosmosNew
import com.android.systemui.util.settings.fakeSettings
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
@SmallTest
@RunWith(AndroidJUnit4::class)
@UiThreadTest
class MediaRepositoryTest : SysuiTestCase() {

    private val drawable = context.getDrawable(R.drawable.ic_music_note)!!
    private val kosmos =
        testKosmosNew().apply {
            val appInfo = mock<ApplicationInfo>()
            whenever(packageManager.getApplicationInfoAsUser(anyString(), anyInt(), anyInt()))
                .thenReturn(appInfo)
            whenever(packageManager.getApplicationIcon(any<ApplicationInfo>())).thenReturn(drawable)
            context.setMockPackageManager(packageManager)
            visualStabilityProvider = mockVisualStabilityProvider
        }
    private val testScope = kosmos.testScope
    private lateinit var session: MediaSession

    private val underTest: MediaRepositoryImpl = kosmos.mediaRepository

    @Before
    fun setUp() {
        session = MediaSession(context, "MediaRepositoryTestSession")
    }

    @After
    fun tearDown() {
        session.release()
        kosmos.fakeMediaControllerFactory.reset()
    }

    @Test
    fun addCurrentUserMediaEntry_activeThenInactivate() =
        testScope.runTest {
            val currentUserEntries by collectLastValue(underTest.currentUserEntries)

            val instanceId = InstanceId.fakeInstanceId(123)
            val userMedia =
                MediaData()
                    .copy(token = session.sessionToken, active = true, instanceId = instanceId)

            addCurrentUserMediaEntry(userMedia)

            assertThat(currentUserEntries?.get(instanceId)).isEqualTo(userMedia)

            addCurrentUserMediaEntry(userMedia.copy(active = false))

            assertThat(currentUserEntries?.get(instanceId)).isNotEqualTo(userMedia)
            assertThat(currentUserEntries?.get(instanceId)?.active).isFalse()
        }

    @Test
    fun addCurrentUserMediaEntry_thenRemove_returnsBoolean() =
        testScope.runTest {
            val currentUserEntries by collectLastValue(underTest.currentUserEntries)

            val instanceId = InstanceId.fakeInstanceId(123)
            val userMedia = MediaData().copy(token = session.sessionToken, instanceId = instanceId)

            addCurrentUserMediaEntry(userMedia)

            assertThat(currentUserEntries?.get(instanceId)).isEqualTo(userMedia)
            assertThat(underTest.removeCurrentUserMediaEntry(instanceId, userMedia)).isTrue()
        }

    @Test
    fun addCurrentUserMediaEntry_thenRemove_returnsValue() =
        testScope.runTest {
            val currentUserEntries by collectLastValue(underTest.currentUserEntries)

            val instanceId = InstanceId.fakeInstanceId(123)
            val userMedia = MediaData().copy(token = session.sessionToken, instanceId = instanceId)

            addCurrentUserMediaEntry(userMedia)

            assertThat(currentUserEntries?.get(instanceId)).isEqualTo(userMedia)

            assertThat(underTest.removeCurrentUserMediaEntry(instanceId)).isEqualTo(userMedia)
        }

    @Test
    fun addMultipleCurrentUserMediaEntries_thenRemove_returnsValues() =
        testScope.runTest {
            val currentUserEntries by collectLastValue(underTest.currentUserEntries)

            val firstInstanceId = InstanceId.fakeInstanceId(123)
            val secondInstanceId = InstanceId.fakeInstanceId(321)
            val firstUserMedia = createMediaData("app1", false, LOCAL, false, firstInstanceId)
            val secondUserMedia = createMediaData("app2", true, LOCAL, false, secondInstanceId)

            addCurrentUserMediaEntry(firstUserMedia)
            addCurrentUserMediaEntry(secondUserMedia)

            assertThat(currentUserEntries?.get(firstInstanceId)).isEqualTo(firstUserMedia)
            assertThat(currentUserEntries?.get(secondInstanceId)).isEqualTo(secondUserMedia)

            assertThat(underTest.removeCurrentUserMediaEntry(firstInstanceId))
                .isEqualTo(firstUserMedia)
            assertThat(underTest.removeCurrentUserMediaEntry(secondInstanceId))
                .isEqualTo(secondUserMedia)
        }

    @Test
    fun addMediaEntry_activeThenInactivate() =
        testScope.runTest {
            val allMediaEntries by collectLastValue(underTest.allMediaEntries)

            val userMedia = MediaData().copy(active = true)

            underTest.addMediaEntry(KEY, userMedia)

            assertThat(allMediaEntries?.get(KEY)).isEqualTo(userMedia)

            underTest.addMediaEntry(KEY, userMedia.copy(active = false))

            assertThat(allMediaEntries?.get(KEY)).isNotEqualTo(userMedia)
            assertThat(allMediaEntries?.get(KEY)?.active).isFalse()
        }

    @Test
    fun addMediaEntry_thenRemove_returnsValue() =
        testScope.runTest {
            val allMediaEntries by collectLastValue(underTest.allMediaEntries)

            val userMedia = MediaData()

            underTest.addMediaEntry(KEY, userMedia)

            assertThat(allMediaEntries?.get(KEY)).isEqualTo(userMedia)

            assertThat(underTest.removeMediaEntry(KEY)).isEqualTo(userMedia)
        }

    @Test
    fun addMediaControlPlayingThenRemote() =
        testScope.runTest {
            val playingInstanceId = InstanceId.fakeInstanceId(123)
            val remoteInstanceId = InstanceId.fakeInstanceId(321)
            val playingData = createMediaData("app1", true, LOCAL, false, playingInstanceId)
            val remoteData = createMediaData("app2", true, REMOTE, false, remoteInstanceId)

            addCurrentUserMediaEntry(playingData)
            addCurrentUserMediaEntry(remoteData)

            assertThat(underTest.currentMedia.size).isEqualTo(2)
            assertThat(underTest.currentMedia)
                .containsExactly(
                    playingData.toDataModel(underTest.currentMedia[0]),
                    remoteData.toDataModel(underTest.currentMedia[1]),
                )
                .inOrder()
        }

    @Test
    fun switchMediaControlsPlaying() =
        testScope.runTest {
            val playingInstanceId1 = InstanceId.fakeInstanceId(123)
            val playingInstanceId2 = InstanceId.fakeInstanceId(321)
            var playingData1 = createMediaData("app1", true, LOCAL, false, playingInstanceId1)
            var playingData2 = createMediaData("app2", false, LOCAL, false, playingInstanceId2)

            addCurrentUserMediaEntry(playingData1)
            addCurrentUserMediaEntry(playingData2)

            assertThat(underTest.currentMedia.size).isEqualTo(2)
            assertThat(underTest.currentMedia)
                .containsExactly(
                    playingData1.toDataModel(underTest.currentMedia[0]),
                    playingData2.toDataModel(underTest.currentMedia[1]),
                )
                .inOrder()

            playingData1 = createMediaData("app1", false, LOCAL, false, playingInstanceId1)
            playingData2 = createMediaData("app2", true, LOCAL, false, playingInstanceId2)

            addCurrentUserMediaEntry(playingData1)
            addCurrentUserMediaEntry(playingData2)

            assertThat(underTest.currentMedia.size).isEqualTo(2)
            assertThat(underTest.currentMedia)
                .containsExactly(
                    playingData1.toDataModel(underTest.currentMedia[0]),
                    playingData2.toDataModel(underTest.currentMedia[1]),
                )
                .inOrder()

            underTest.reorderMedia()
            runCurrent()

            assertThat(underTest.currentMedia.size).isEqualTo(2)
            assertThat(underTest.currentMedia)
                .containsExactly(
                    playingData2.toDataModel(underTest.currentMedia[0]),
                    playingData1.toDataModel(underTest.currentMedia[1]),
                )
                .inOrder()
        }

    @Test
    fun fullOrderTest() =
        testScope.runTest {
            val instanceId1 = InstanceId.fakeInstanceId(123)
            val instanceId2 = InstanceId.fakeInstanceId(456)
            val instanceId3 = InstanceId.fakeInstanceId(321)
            val instanceId4 = InstanceId.fakeInstanceId(654)
            val instanceId5 = InstanceId.fakeInstanceId(124)
            val playingAndLocalData = createMediaData("app1", true, LOCAL, false, instanceId1)
            val playingAndRemoteData = createMediaData("app2", true, REMOTE, false, instanceId2)
            val stoppedAndLocalData = createMediaData("app3", false, LOCAL, false, instanceId3)
            val stoppedAndRemoteData = createMediaData("app4", false, REMOTE, false, instanceId4)
            val canResumeData = createMediaData("app5", false, LOCAL, true, instanceId5)

            addCurrentUserMediaEntry(stoppedAndLocalData)

            addCurrentUserMediaEntry(stoppedAndRemoteData)

            addCurrentUserMediaEntry(canResumeData)

            addCurrentUserMediaEntry(playingAndLocalData)

            addCurrentUserMediaEntry(playingAndRemoteData)

            underTest.reorderMedia()
            runCurrent()

            assertThat(underTest.currentMedia.size).isEqualTo(5)
            assertThat(underTest.currentMedia)
                .containsExactly(
                    playingAndLocalData.toDataModel(underTest.currentMedia[0]),
                    playingAndRemoteData.toDataModel(underTest.currentMedia[1]),
                    stoppedAndRemoteData.toDataModel(underTest.currentMedia[2]),
                    stoppedAndLocalData.toDataModel(underTest.currentMedia[3]),
                    canResumeData.toDataModel(underTest.currentMedia[4]),
                )
                .inOrder()
        }

    @Test
    fun toggleMediaControlsOnLockscreen() =
        testScope.runTest {
            val allowMediaOnLockscreen by collectLastValue(underTest.allowMediaPlayerOnLockscreen)

            assertThat(allowMediaOnLockscreen).isTrue()

            kosmos.fakeSettings.putBoolForUser(
                Settings.Secure.MEDIA_CONTROLS_LOCK_SCREEN,
                value = false,
                UserHandle.USER_CURRENT,
            )

            assertThat(allowMediaOnLockscreen).isFalse()

            kosmos.fakeSettings.putBoolForUser(
                Settings.Secure.MEDIA_CONTROLS_LOCK_SCREEN,
                value = true,
                UserHandle.USER_CURRENT,
            )

            assertThat(allowMediaOnLockscreen).isTrue()
        }

    @Test
    fun loadMediaFromSecondaryProfile() {
        testScope.runTest {
            val instanceId = InstanceId.fakeInstanceId(123)
            val secondUserAppInfo = mock<ApplicationInfo>()
            val secondUserIcon = context.getDrawable(R.drawable.ic_media_prev)!!

            whenever(kosmos.packageManager.getApplicationInfoAsUser(anyString(), anyInt(), eq(2)))
                .thenReturn(secondUserAppInfo)
            whenever(kosmos.packageManager.getApplicationIcon(secondUserAppInfo))
                .thenReturn(secondUserIcon)

            val secondUserMedia =
                MediaData().copy(instanceId = instanceId, userId = 2, resumption = true)
            addCurrentUserMediaEntry(secondUserMedia)

            val entry = underTest.currentMedia.find { it.instanceId == instanceId }!!
            val expectedIcon = Icon.Loaded(secondUserIcon, null)
            assertThat(entry.appIcon).isEqualTo(expectedIcon)
        }
    }

    @Test
    fun metadataAndStateSupportSeeking() =
        testScope.runTest {
            val state =
                PlaybackState.Builder().run {
                    setState(PlaybackState.STATE_PAUSED, 200L, 1f)
                    setActions(PlaybackState.ACTION_SEEK_TO)
                    build()
                }
            val metadata =
                MediaMetadata.Builder().run {
                    putLong(MediaMetadata.METADATA_KEY_DURATION, 400L)
                    build()
                }

            val mockController = mock<MediaController>()
            whenever(mockController.metadata).thenReturn(metadata)
            whenever(mockController.playbackState).thenReturn(state)
            kosmos.fakeMediaControllerFactory.setControllerForToken(
                session.sessionToken,
                mockController,
            )

            val instanceId = InstanceId.fakeInstanceId(123)
            val userMedia =
                createMediaData(
                        app = "TEST_APP",
                        playing = false,
                        playbackLocation = LOCAL,
                        isResume = false,
                        instanceId = instanceId,
                    )
                    .copy(token = session.sessionToken)
            addCurrentUserMediaEntry(userMedia)

            val callbackCaptor = argumentCaptor<MediaController.Callback>()
            verify(mockController).registerCallback(callbackCaptor.capture())

            val entry = underTest.currentMedia.find { it.instanceId == instanceId }
            assertThat(entry).isNotNull()
            assertThat(entry!!.canShowSeekbar).isTrue()
            assertThat(entry.canBeScrubbed).isTrue()
        }

    @Test
    fun addNewMedia_registerCallback_updateMedia_callbackIsNotRegistered() =
        testScope.runTest {
            val state =
                PlaybackState.Builder().run {
                    setState(PlaybackState.STATE_PAUSED, 200L, 1f)
                    setActions(PlaybackState.ACTION_SEEK_TO)
                    build()
                }
            val metadata =
                MediaMetadata.Builder().run {
                    putLong(MediaMetadata.METADATA_KEY_DURATION, 400L)
                    build()
                }

            val mockController = mock<MediaController>()
            whenever(mockController.metadata).thenReturn(metadata)
            whenever(mockController.playbackState).thenReturn(state)
            kosmos.fakeMediaControllerFactory.setControllerForToken(
                session.sessionToken,
                mockController,
            )

            val instanceId = InstanceId.fakeInstanceId(123)
            val userMedia =
                createMediaData(
                        app = "TEST_APP",
                        playing = false,
                        playbackLocation = LOCAL,
                        isResume = false,
                        instanceId = instanceId,
                    )
                    .copy(token = session.sessionToken)
            addCurrentUserMediaEntry(userMedia)

            val callbackCaptor = argumentCaptor<MediaController.Callback>()
            verify(mockController).registerCallback(callbackCaptor.capture())
            reset(mockController)

            addCurrentUserMediaEntry(userMedia.copy(isPlaying = true))
            verify(mockController, never()).registerCallback(callbackCaptor.capture())
        }

    @Test
    fun metadataCannotShowSeekbar() =
        testScope.runTest {
            val state =
                PlaybackState.Builder().run {
                    setState(PlaybackState.STATE_PAUSED, 200L, 1f)
                    setActions(PlaybackState.ACTION_SEEK_TO)
                    build()
                }
            val metadata =
                MediaMetadata.Builder().run {
                    putLong(MediaMetadata.METADATA_KEY_DURATION, 400L)
                    build()
                }

            val mockController = mock<MediaController>()
            whenever(mockController.metadata).thenReturn(metadata)
            whenever(mockController.playbackState).thenReturn(state)
            kosmos.fakeMediaControllerFactory.setControllerForToken(
                session.sessionToken,
                mockController,
            )

            val instanceId = InstanceId.fakeInstanceId(123)
            val userMedia =
                createMediaData(
                        app = "TEST_APP",
                        playing = false,
                        playbackLocation = LOCAL,
                        isResume = false,
                        instanceId = instanceId,
                    )
                    .copy(token = session.sessionToken)
            addCurrentUserMediaEntry(userMedia)

            val callbackCaptor = argumentCaptor<MediaController.Callback>()
            verify(mockController).registerCallback(callbackCaptor.capture())

            val entry = underTest.currentMedia.find { it.instanceId == instanceId }
            assertThat(entry!!.canShowSeekbar).isTrue()

            val noSeekbarMetadata =
                MediaMetadata.Builder().run {
                    putLong(MediaMetadata.METADATA_KEY_DURATION, 0L)
                    build()
                }
            callbackCaptor.lastValue.onMetadataChanged(noSeekbarMetadata)
            runCurrent()

            val updatedEntry = underTest.currentMedia.find { it.instanceId == instanceId }
            assertThat(updatedEntry).isNotNull()
            assertThat(updatedEntry!!.canShowSeekbar).isFalse()
        }

    fun playbackStateCannotSeek() =
        testScope.runTest {
            val state =
                PlaybackState.Builder().run {
                    setState(PlaybackState.STATE_PAUSED, 200L, 1f)
                    setActions(PlaybackState.ACTION_SEEK_TO)
                    build()
                }
            val metadata =
                MediaMetadata.Builder().run {
                    putLong(MediaMetadata.METADATA_KEY_DURATION, 400L)
                    build()
                }

            val mockController = mock<MediaController>()
            whenever(mockController.metadata).thenReturn(metadata)
            whenever(mockController.playbackState).thenReturn(state)
            kosmos.fakeMediaControllerFactory.setControllerForToken(
                session.sessionToken,
                mockController,
            )

            val instanceId = InstanceId.fakeInstanceId(123)
            val userMedia =
                createMediaData(
                        app = "TEST_APP",
                        playing = false,
                        playbackLocation = LOCAL,
                        isResume = false,
                        instanceId = instanceId,
                    )
                    .copy(token = session.sessionToken)
            addCurrentUserMediaEntry(userMedia)

            val callbackCaptor = argumentCaptor<MediaController.Callback>()
            verify(mockController).registerCallback(callbackCaptor.capture())

            val entry = underTest.currentMedia.find { it.instanceId == instanceId }
            assertThat(entry!!.canBeScrubbed).isTrue()

            // Update the state so it can't seek
            val noSeekState =
                PlaybackState.Builder().run {
                    setState(PlaybackState.STATE_PAUSED, 200L, 1f)
                    build()
                }
            callbackCaptor.lastValue.onPlaybackStateChanged(noSeekState)
            runCurrent()

            val updatedEntry = underTest.currentMedia.find { it.instanceId == instanceId }
            assertThat(updatedEntry).isNotNull()
            assertThat(updatedEntry!!.canBeScrubbed).isFalse()
        }

    @Test
    fun swipeToDismiss_pausedAndResumeOff_userInitiated() {
        testScope.runTest {
            val instanceId = InstanceId.fakeInstanceId(123)
            val mediaData = createMediaData("app1", false, LOCAL, false, instanceId)
            // When resumption is disabled, paused media should be dismissed after being swiped away
            kosmos.fakeSettings.putInt(Settings.Secure.MEDIA_CONTROLS_RESUME, 0)

            addCurrentUserMediaEntry(mediaData)

            whenever(kosmos.visualStabilityProvider.isReorderingAllowed).thenReturn(false)
            // Swipe away the media entry
            val inactiveMedia = mediaData.copy(active = false)
            underTest.setSwipedAwayState()
            addCurrentUserMediaEntry(inactiveMedia)

            assertThat(underTest.isUserInitiatedRemovalQueued).isTrue()
            assertThat(underTest.keysNeedRemoval.contains(instanceId)).isTrue()
        }
    }

    @Test
    fun pausedAndResumeOff_inactive_notUserInitiated() {
        testScope.runTest {
            val instanceId = InstanceId.fakeInstanceId(123)
            val mediaData = createMediaData("app1", false, LOCAL, false, instanceId)
            // When resumption is disabled, paused media should be dismissed after being swiped away
            kosmos.fakeSettings.putInt(Settings.Secure.MEDIA_CONTROLS_RESUME, 0)

            addCurrentUserMediaEntry(mediaData)

            whenever(kosmos.visualStabilityProvider.isReorderingAllowed).thenReturn(false)
            // Media becomes inactive
            val inactiveMedia = mediaData.copy(active = false)
            addCurrentUserMediaEntry(inactiveMedia)

            assertThat(underTest.isUserInitiatedRemovalQueued).isFalse()
            assertThat(underTest.keysNeedRemoval.contains(instanceId)).isTrue()
        }
    }

    @Test
    fun pausedAndResumeOff_inactive_orderingAllowed_immediateRemoval() {
        testScope.runTest {
            val instanceId = InstanceId.fakeInstanceId(123)
            val mediaData = createMediaData("app1", false, LOCAL, false, instanceId)
            // When resumption is disabled, paused media should be dismissed after being swiped away
            kosmos.fakeSettings.putInt(Settings.Secure.MEDIA_CONTROLS_RESUME, 0)

            addCurrentUserMediaEntry(mediaData)

            whenever(kosmos.visualStabilityProvider.isReorderingAllowed).thenReturn(true)
            // Media becomes inactive
            val inactiveMedia = mediaData.copy(active = false)
            addCurrentUserMediaEntry(inactiveMedia)

            assertThat(
                    underTest.currentMedia
                        .find { it.instanceId == instanceId }
                        ?.needsImmediateRemoval
                )
                .isTrue()
        }
    }

    @Test
    fun pausedAndResumeOff_active_orderingAllowed_noImmediateRemoval() {
        testScope.runTest {
            whenever(kosmos.visualStabilityProvider.isReorderingAllowed).thenReturn(false)
            val instanceId = InstanceId.fakeInstanceId(123)
            val mediaData = createMediaData("app1", false, LOCAL, false, instanceId)
            kosmos.fakeSettings.putInt(Settings.Secure.MEDIA_CONTROLS_RESUME, 0)

            addCurrentUserMediaEntry(mediaData)

            assertThat(
                    underTest.currentMedia
                        .find { it.instanceId == instanceId }
                        ?.needsImmediateRemoval
                )
                .isFalse()

            whenever(kosmos.visualStabilityProvider.isReorderingAllowed).thenReturn(true)

            addCurrentUserMediaEntry(mediaData)

            assertThat(
                    underTest.currentMedia
                        .find { it.instanceId == instanceId }
                        ?.needsImmediateRemoval
                )
                .isFalse()
        }
    }

    @Test
    fun isPlayingNullAndResumeOff_active_orderingAllowed_noImmediateRemoval() {
        testScope.runTest {
            whenever(kosmos.visualStabilityProvider.isReorderingAllowed).thenReturn(false)
            val instanceId = InstanceId.fakeInstanceId(123)
            val mediaData = createMediaData("app1", null, LOCAL, false, instanceId)
            kosmos.fakeSettings.putInt(Settings.Secure.MEDIA_CONTROLS_RESUME, 0)

            addCurrentUserMediaEntry(mediaData)

            assertThat(
                    underTest.currentMedia
                        .find { it.instanceId == instanceId }
                        ?.needsImmediateRemoval
                )
                .isFalse()

            whenever(kosmos.visualStabilityProvider.isReorderingAllowed).thenReturn(true)

            addCurrentUserMediaEntry(mediaData)

            assertThat(
                    underTest.currentMedia
                        .find { it.instanceId == instanceId }
                        ?.needsImmediateRemoval
                )
                .isFalse()
        }
    }

    private fun TestScope.addCurrentUserMediaEntry(data: MediaData) {
        underTest.addCurrentUserMediaEntry(data)
        runCurrent()
    }

    private fun createMediaData(
        app: String,
        playing: Boolean?,
        playbackLocation: Int,
        isResume: Boolean,
        instanceId: InstanceId,
    ): MediaData {
        return MediaData(
            token = session.sessionToken,
            packageName = "packageName",
            playbackLocation = playbackLocation,
            resumption = isResume,
            notificationKey = "key: $app",
            isPlaying = playing,
            instanceId = instanceId,
        )
    }

    private fun MediaData.toDataModel(mediaModel: MediaDataModel): MediaDataModel {
        return MediaDataModel(
            instanceId = instanceId,
            appUid = appUid,
            packageName = packageName,
            appName = app.toString(),
            appIcon = Icon.Loaded(drawable, null),
            background = null,
            title = song.toString(),
            subtitle = artist.toString(),
            colorScheme = mediaModel.colorScheme,
            notificationActions = actions,
            notificationActionsCompressed = mutableListOf(),
            playbackStateActions = semanticActions,
            outputDevice = device,
            clickIntent = clickIntent,
            state = mediaModel.state,
            durationMs = mediaModel.durationMs,
            positionMs = mediaModel.positionMs,
            canShowSeekbar = mediaModel.canShowSeekbar,
            canBeScrubbed = mediaModel.canBeScrubbed,
            canBeDismissed = isClearable,
            isActive = active,
            isResume = resumption,
            resumeAction = resumeAction,
            isExplicit = isExplicit,
            suggestionData = mediaModel.suggestionData,
            token = session.sessionToken,
            needsImmediateRemoval = mediaModel.needsImmediateRemoval,
        )
    }

    companion object {
        private const val LOCAL = MediaData.PLAYBACK_LOCAL
        private const val REMOTE = MediaData.PLAYBACK_CAST_LOCAL
        private const val KEY = "KEY"
    }
}
