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

package com.android.systemui.volume.dialog

import android.app.ActivityManager
import android.content.applicationContext
import android.content.packageManager
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.session.PlaybackState
import android.provider.Settings
import android.view.View
import com.android.settingslib.notification.data.repository.updateNotificationPolicy
import com.android.settingslib.volume.shared.model.RingerMode
import com.android.systemui.accessibility.data.repository.accessibilityRepository
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.testScope
import com.android.systemui.plugins.VolumeDialogController
import com.android.systemui.plugins.fakeVolumeDialogController
import com.android.systemui.res.R
import com.android.systemui.statusbar.policy.data.repository.zenModeRepository
import com.android.systemui.statusbar.policy.devicePostureController
import com.android.systemui.statusbar.policy.fakeDevicePostureController
import com.android.systemui.statusbar.policy.fakeDeviceProvisionedController
import com.android.systemui.volume.data.repository.audioSystemRepository
import com.android.systemui.volume.data.repository.fakeAudioRepository
import com.android.systemui.volume.dialog.data.repository.volumeDialogVisibilityRepository
import com.android.systemui.volume.dialog.shared.model.VolumeDialogVisibilityModel
import com.android.systemui.volume.dialog.sliders.domain.model.VolumeDialogSliderType
import com.android.systemui.volume.dialog.sliders.domain.model.volumeDialogSliderType
import com.android.systemui.volume.localMediaController
import com.android.systemui.volume.localPlaybackStateBuilder
import com.android.systemui.volume.mediaControllerRepository
import com.android.systemui.volume.panel.component.mediaoutput.domain.interactor.FakeMediaControllerInteractor
import com.android.systemui.volume.panel.component.mediaoutput.domain.interactor.mediaControllerInteractor
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.mockito.kotlin.whenever
import platform.test.screenshot.utils.compose.ComposeScreenshotTestRule

/** Timeout for the data and domain logic to be ready for the screenshot */
private val initializationTimeout = 5.seconds

/**
 * Timeout of each test.
 *
 * This is a sum of the TestScope#runTest function default timeout and
 * ComposeScreenshotTestRule#screenshotTest function timeout.
 */
private val testTimeout = 20.seconds

object VolumeDialogScreenshotTestCommons {

    private fun Kosmos.setupTestDefaults() {
        accessibilityRepository.setRecommendedTimeout(10.seconds)
        devicePostureController = fakeDevicePostureController
        whenever(packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)).thenReturn(false)

        mediaControllerInteractor = FakeMediaControllerInteractor()
        localPlaybackStateBuilder.setState(
            /* state = */ PlaybackState.STATE_STOPPED,
            /* position = */ 0,
            /* playbackSpeed = */ 1f,
        )
        mediaControllerRepository.setActiveSessions(listOf(localMediaController))
        audioSystemRepository.setIsSingleVolume(false)
        setRingerMode(AudioManager.RINGER_MODE_NORMAL)
        volumeDialogSliderType = VolumeDialogSliderType.Stream(AudioManager.STREAM_MUSIC)

        with(fakeDeviceProvisionedController) { setUserSetup(currentUser, true) }

        with(fakeVolumeDialogController) {
            setHasVibrator(true)
            setActiveStream(AudioManager.STREAM_MUSIC, true)
            updateState {
                states[AudioManager.STREAM_MUSIC] =
                    VolumeDialogController.StreamState().also {
                        it.level = 10
                        it.levelMin = 0
                        it.levelMax = 20
                        it.name = R.string.stream_music
                    }
                states[AudioManager.STREAM_RING] =
                    VolumeDialogController.StreamState().also {
                        it.level = 4
                        it.levelMin = 0
                        it.levelMax = 7
                        it.name = R.string.stream_ring
                    }
            }
        }

        zenModeRepository.apply {
            updateNotificationPolicy()
            updateZenMode(Settings.Global.ZEN_MODE_OFF)
        }

        volumeDialogVisibilityRepository.updateVisibility {
            VolumeDialogVisibilityModel.Visible(
                reason = 0,
                keyguardLocked = false,
                lockTaskModeState = ActivityManager.LOCK_TASK_MODE_NONE,
            )
        }
    }

    fun Kosmos.screenshotState(
        screenshotRule: ComposeScreenshotTestRule,
        identifier: String,
        prepareState: suspend Kosmos.() -> Unit,
        onDialogCreated: Kosmos.() -> Unit = {},
    ): TestResult =
        testScope.runTest(testTimeout) {
            screenshotRule.dialogScreenshotTest(
                goldenIdentifier = identifier,
                frameLimit = 500,
                shouldWaitForTheDialog = { dialog ->
                    val isDialogFullyVisible =
                        dialog.requireViewById<View>(R.id.volume_dialog).translationX == 0f
                    !isDialogFullyVisible
                },
                waitForIdle = { onDialogCreated() },
            ) { activity ->
                runBlocking {
                    withTimeout(initializationTimeout) {
                        applicationContext = activity
                        setupTestDefaults()
                        prepareState()
                    }
                }

                volumeDialog
            }
        }

    fun Kosmos.setRingerMode(mode: Int) {
        fakeVolumeDialogController.updateState { this.ringerModeInternal = mode }
        fakeAudioRepository.setRingerMode(RingerMode(mode))
    }
}
