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

package com.android.systemui.volume

import android.app.activityManager
import android.app.keyguardManager
import android.content.Intent
import android.content.applicationContext
import android.content.packageManager
import android.content.testableContext
import android.media.AudioManager
import android.media.session.MediaSession
import android.os.Handler
import android.os.Process
import android.os.looper
import android.os.testableLooper
import android.platform.test.flag.junit.SetFlagsRule
import android.testing.TestableLooper
import android.view.accessibility.accessibilityManager
import androidx.core.util.isEmpty
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.settingslib.volume.MediaSessions
import com.android.settingslib.volume.MediaSessions.SessionId.Companion.from
import com.android.settingslib.volume.data.model.VolumeControllerEvent
import com.android.systemui.SysuiTestCase
import com.android.systemui.broadcast.broadcastDispatcher
import com.android.systemui.broadcast.broadcastDispatcherContext
import com.android.systemui.dump.dumpManager
import com.android.systemui.keyguard.WakefulnessLifecycle
import com.android.systemui.keyguard.wakefulnessLifecycle
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.plugins.VolumeDialogController
import com.android.systemui.testKosmosNew
import com.android.systemui.util.RingerModeLiveData
import com.android.systemui.util.concurrency.FakeExecutor
import com.android.systemui.util.concurrency.FakeThreadFactory
import com.android.systemui.util.kotlin.javaAdapter
import com.android.systemui.util.time.fakeSystemClock
import com.android.systemui.volume.data.repository.audioRepository
import com.android.systemui.volume.domain.interactor.FakeAudioSharingInteractor
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.KArgumentCaptor
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
@SmallTest
@TestableLooper.RunWithLooper
class VolumeDialogControllerImplTestKt : SysuiTestCase() {

    @get:Rule val setFlagsRule = SetFlagsRule()

    private val kosmos: Kosmos = testKosmosNew()
    private val audioManager: AudioManager = mock {}
    private val callbacks: VolumeDialogController.Callbacks = mock {}
    private val stateCaptor: KArgumentCaptor<VolumeDialogController.State> = argumentCaptor()
    private val fakeAudioSharingInteractor: FakeAudioSharingInteractor =
        FakeAudioSharingInteractor()

    private lateinit var threadFactory: FakeThreadFactory
    private lateinit var underTest: VolumeDialogControllerImpl

    @Before
    fun setUp() =
        with(kosmos) {
            threadFactory =
                FakeThreadFactory(FakeExecutor(fakeSystemClock)).apply { setLooper(looper) }
            broadcastDispatcherContext = testableContext
            fakeAudioSharingInteractor.setAudioSharingVolumeBarAvailable(true)

            underTest =
                VolumeDialogControllerImpl(
                        applicationContext,
                        broadcastDispatcher,
                        mock {
                            on { ringerMode }.thenReturn(mock<RingerModeLiveData> {})
                            on { ringerModeInternal }.thenReturn(mock<RingerModeLiveData> {})
                        },
                        threadFactory,
                        audioManager,
                        mock {},
                        mock {},
                        mock {},
                        volumeControllerAdapter,
                        accessibilityManager,
                        packageManager,
                        wakefulnessLifecycle,
                        keyguardManager,
                        activityManager,
                        mock {},
                        mock {},
                        mock { on { userContext }.thenReturn(applicationContext) },
                        dumpManager,
                        fakeAudioSharingInteractor,
                        kosmos.javaAdapter,
                        mock {},
                    )
                    .apply {
                        setEnableDialogs(true, true)
                        addCallback(callbacks, Handler(looper))
                    }
        }

    @Test
    fun broadcastEvent_sendsChangesOnce() =
        kosmos.runTest {
            whenever(audioManager.getLastAudibleStreamVolume(any())).thenReturn(1)
            broadcastDispatcher.sendIntentToMatchingReceiversOnly(
                applicationContext,
                Intent(AudioManager.ACTION_VOLUME_CHANGED).apply {
                    putExtra(AudioManager.EXTRA_VOLUME_STREAM_TYPE, AudioManager.STREAM_SYSTEM)
                },
            )
            testableLooper.processAllMessages()

            verify(callbacks) { 1 * { onStateChanged(any()) } }
        }

    @Test
    fun listensToVolumeController() = testVolumeController { stream: Int, flags: Int ->
        audioRepository.sendVolumeControllerEvent(
            VolumeControllerEvent.VolumeChanged(streamType = stream, flags = flags)
        )
    }

    @Test
    fun testOnRemoteRemove_oldStream_removed() {
        kosmos.runTest {
            val sessionId = from(MediaSession.Token(Process.myUid(), null))
            underTest.mMediaSessionsCallbacksW.onRemoteUpdate(
                sessionId,
                "test-remote",
                MediaSessions.VolumeInfo(50, 100),
            )

            underTest.mMediaSessionsCallbacksW.onRemoteRemoved(sessionId)
            testableLooper.processAllMessages()

            verify(callbacks, atLeastOnce()).onStateChanged(stateCaptor.capture())
            assertThat(stateCaptor.lastValue.states.isEmpty()).isTrue()
        }
    }

    private fun testVolumeController(
        emitVolumeChange: suspend Kosmos.(stream: Int, flags: Int) -> Unit
    ) =
        with(kosmos) {
            testScope.runTest {
                whenever(wakefulnessLifecycle.wakefulness)
                    .thenReturn(WakefulnessLifecycle.WAKEFULNESS_AWAKE)
                underTest.setVolumeController()
                runCurrent()

                emitVolumeChange(AudioManager.STREAM_SYSTEM, AudioManager.FLAG_SHOW_UI)
                runCurrent()
                TestableLooper.get(this@VolumeDialogControllerImplTestKt).processAllMessages()
                testScheduler.advanceUntilIdle()

                verify(callbacks) { 1 * { onShowRequested(any(), any(), any()) } }
            }
        }
}
