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
import android.media.session.MediaSession
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
import com.android.systemui.media.remedia.data.model.MediaDataModel
import com.android.systemui.res.R
import com.android.systemui.testKosmos
import com.android.systemui.util.settings.fakeSettings
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
@SmallTest
@RunWith(AndroidJUnit4::class)
@UiThreadTest
class MediaRepositoryTest : SysuiTestCase() {

    private val drawable = context.getDrawable(R.drawable.ic_music_note)!!
    private val kosmos =
        testKosmos().apply {
            whenever(packageManager.getApplicationIcon(anyString())).thenReturn(drawable)
            context.setMockPackageManager(packageManager)
        }
    private val testScope = kosmos.testScope
    private val session = MediaSession(context, "MediaRepositoryTestSession")

    private val underTest: MediaRepositoryImpl = kosmos.mediaRepository

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

    private fun TestScope.addCurrentUserMediaEntry(data: MediaData) {
        underTest.addCurrentUserMediaEntry(data)
        runCurrent()
    }

    private fun createMediaData(
        app: String,
        playing: Boolean,
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
            playbackStateActions = semanticActions,
            outputDevice = device,
            clickIntent = clickIntent,
            state = mediaModel.state,
            durationMs = mediaModel.durationMs,
            positionMs = mediaModel.positionMs,
            canBeScrubbed = mediaModel.canBeScrubbed,
            canBeDismissed = isClearable,
            isActive = active,
            isResume = resumption,
            resumeAction = resumeAction,
            isExplicit = isExplicit,
            suggestionData = mediaModel.suggestionData,
            token = session.sessionToken,
        )
    }

    companion object {
        private const val LOCAL = MediaData.PLAYBACK_LOCAL
        private const val REMOTE = MediaData.PLAYBACK_CAST_LOCAL
        private const val KEY = "KEY"
    }
}
