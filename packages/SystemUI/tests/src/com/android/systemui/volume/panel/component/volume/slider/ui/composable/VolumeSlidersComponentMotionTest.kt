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
package com.android.systemui.volume.panel.component.volume.slider.ui.composable

import android.media.AudioManager
import android.media.session.PlaybackState
import android.platform.test.annotations.MotionTest
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnySibling
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.swipeLeft
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.android.settingslib.volume.shared.model.AudioStream
import com.android.systemui.SysuiTestCase
import com.android.systemui.compose.modifiers.resIdToTestTag
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.testScope
import com.android.systemui.motion.createSysUiComposeMotionTestRule
import com.android.systemui.testKosmos
import com.android.systemui.volume.localMediaController
import com.android.systemui.volume.localMediaRepository
import com.android.systemui.volume.localPlaybackStateBuilder
import com.android.systemui.volume.mediaControllerRepository
import com.android.systemui.volume.panel.component.mediaoutput.domain.interactor.TestMediaDevicesFactory
import com.android.systemui.volume.panel.component.mediaoutput.domain.interactor.fakeMediaControllerInteractor
import com.android.systemui.volume.panel.component.mediaoutput.domain.interactor.mediaControllerInteractor
import com.android.systemui.volume.panel.component.mediaoutput.domain.model.MediaControllerChangeModel
import com.android.systemui.volume.panel.component.volume.slider.ui.viewmodel.AudioStreamSliderTestTags
import com.android.systemui.volume.panel.component.volume.ui.composable.ColumnVolumeSlidersMotionTestKeys
import com.android.systemui.volume.panel.component.volume.ui.composable.VolumeSlidersMotionTestKeys
import com.android.systemui.volume.panel.component.volume.volumeSlidersComponent
import com.android.systemui.volume.panel.ui.composable.VolumePanelComposeScope
import com.android.systemui.volume.panel.ui.viewmodel.volumePanelViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import platform.test.motion.compose.ComposeFeatureCaptures.positionInRoot
import platform.test.motion.compose.ComposeFeatureCaptures.size
import platform.test.motion.compose.ComposeRecordingSpec
import platform.test.motion.compose.MotionControl
import platform.test.motion.compose.MotionControlScope
import platform.test.motion.compose.feature
import platform.test.motion.compose.hasMotionTestValue
import platform.test.motion.compose.motionTestValueOfNode
import platform.test.motion.compose.recordMotion
import platform.test.motion.compose.runTest

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@LargeTest
@MotionTest
@Ignore("b/328332487, need to figure out why androidx update causes the test to fail")
class VolumeSlidersComponentMotionTest : SysuiTestCase() {
    private val kosmos = testKosmos()
    @get:Rule val motionTestRule = createSysUiComposeMotionTestRule(kosmos)

    @Before
    fun setUp() {
        with(kosmos) {
            mediaControllerInteractor = fakeMediaControllerInteractor
            mediaControllerRepository.setActiveSessions(listOf(localMediaController))
            localMediaRepository.updateCurrentConnectedDevice(
                TestMediaDevicesFactory.builtInMediaDevice(deviceIcon = null)
            )
        }
    }

    @Test
    fun testCollapsedToExpanded() =
        motionTestRule.runTest {
            kosmos.localPlaybackStateBuilder.setState(PlaybackState.STATE_PLAYING, 0, 0f)
            val motion =
                recordMotion(
                    content = { kosmos.Sliders() },
                    recordingSpec =
                        ComposeRecordingSpec(
                            MotionControl(
                                recording = {
                                    recordSliding(PlaybackState.STATE_PAUSED) {
                                        awaitCondition {
                                            motionTestValueOfNode(
                                                ColumnVolumeSlidersMotionTestKeys
                                                    .isSlidersTransitionIdle
                                            )
                                        }
                                    }
                                }
                            )
                        ) {
                            val list =
                                listOf(
                                    AudioStreamSliderTestTags.testTagsByStream[
                                            AudioStream(AudioManager.STREAM_MUSIC)] ?: "",
                                    AudioStreamSliderTestTags.testTagsByStream[
                                            AudioStream(AudioManager.STREAM_VOICE_CALL)] ?: "",
                                )
                            for (tag in list) {
                                feature(
                                    hasTestTag(resIdToTestTag(tag)),
                                    positionInRoot,
                                    "${tag}_position",
                                )
                                feature(hasTestTag(resIdToTestTag(tag)), size, "${tag}_size")
                            }
                        },
                )
            assertThat(motion)
                .timeSeriesMatchesGolden("VolumePanel_VolumeSliders_testCollapsedToExpanded")
        }

    @Test
    fun recordMediaIconPosition() =
        motionTestRule.runTest() {
            val tag =
                checkNotNull(
                    AudioStreamSliderTestTags.testTagsByStream[
                            AudioStream(AudioManager.STREAM_MUSIC)]
                )

            val motion =
                recordMotion(
                    content = { kosmos.Sliders() },
                    recordingSpec =
                        ComposeRecordingSpec(
                            MotionControl(
                                recording = {
                                    performTouchInputAsync(
                                        onNode(hasTestTag(resIdToTestTag(tag)))
                                    ) {
                                        swipeLeft(startX = right, endX = left, durationMillis = 500)
                                    }
                                }
                            )
                        ) {
                            feature(
                                hasTestTag(VolumeSlidersMotionTestKeys.ACTIVE_ICON_TAG)
                                    .and(hasAnyAncestor(hasTestTag(resIdToTestTag(tag)))),
                                positionInRoot,
                                "${tag}_position_activeStartIcon",
                                true,
                            )
                            feature(
                                hasTestTag(VolumeSlidersMotionTestKeys.INACTIVE_ICON_TAG)
                                    .and(hasAnyAncestor(hasTestTag(resIdToTestTag(tag)))),
                                positionInRoot,
                                "${tag}_position_inactiveStartIcon",
                                true,
                            )
                        },
                )
            assertThat(motion)
                .timeSeriesMatchesGolden("VolumePanel_VolumeSliders_recordMediaIconPosition")
        }

    @Test
    fun testMuteRingerMutesNotification() =
        motionTestRule.runTest() {
            val ringTag =
                checkNotNull(
                    AudioStreamSliderTestTags.testTagsByStream[
                            AudioStream(AudioManager.STREAM_RING)]
                )
            val notificationTag =
                checkNotNull(
                    AudioStreamSliderTestTags.testTagsByStream[
                            AudioStream(AudioManager.STREAM_NOTIFICATION)]
                )
            val motion =
                recordMotion(
                    content = { kosmos.Sliders() },
                    recordingSpec =
                        ComposeRecordingSpec(
                            MotionControl(
                                recording = {
                                    performTouchInputAsync(
                                        onNode(hasTestTag(resIdToTestTag(ringTag)))
                                    ) {
                                        swipeLeft(
                                            startX = right / 5,
                                            endX = left,
                                            durationMillis = 500,
                                        )
                                    }
                                }
                            )
                        ) {
                            feature(
                                hasTestTag(VolumeSlidersMotionTestKeys.DISABLED_MESSAGE_TAG)
                                    .and(
                                        hasAnySibling(hasTestTag(resIdToTestTag(notificationTag)))
                                    ),
                                positionInRoot,
                                "disabled_message_position",
                                true,
                            )
                            feature(
                                hasTestTag(resIdToTestTag(ringTag)),
                                positionInRoot,
                                "ring_slider_position",
                                true,
                            )
                        },
                )
            assertThat(motion)
                .timeSeriesMatchesGolden(
                    "VolumePanel_VolumeSliders_testMuteRingerMutesNotification"
                )
        }

    @Test
    fun testExpandedToCollapsed() =
        motionTestRule.runTest {
            kosmos.localPlaybackStateBuilder.setState(PlaybackState.STATE_PAUSED, 0, 0f)
            val motion =
                recordMotion(
                    content = { kosmos.Sliders() },
                    recordingSpec =
                        ComposeRecordingSpec(
                            MotionControl(
                                recording = {
                                    recordSliding(PlaybackState.STATE_PLAYING) {
                                        awaitCondition {
                                            onAllNodes(
                                                    hasMotionTestValue(
                                                        ColumnVolumeSlidersMotionTestKeys
                                                            .isSlidersTransitionIdle
                                                    )
                                                )
                                                .fetchSemanticsNodes()
                                                .isEmpty()
                                        }
                                    }
                                }
                            )
                        ) {
                            val list =
                                listOf(
                                    AudioStreamSliderTestTags.testTagsByStream[
                                            AudioStream(AudioManager.STREAM_MUSIC)] ?: "",
                                    AudioStreamSliderTestTags.testTagsByStream[
                                            AudioStream(AudioManager.STREAM_VOICE_CALL)] ?: "",
                                )
                            for (tag in list) {
                                feature(
                                    hasTestTag(resIdToTestTag(tag)),
                                    positionInRoot,
                                    "${tag}_position",
                                )
                                feature(hasTestTag(resIdToTestTag(tag)), size, "${tag}_size")
                            }
                        },
                )
            assertThat(motion)
                .timeSeriesMatchesGolden("VolumePanel_VolumeSliders_testExpandedToCollapsed")
        }

    private suspend fun MotionControlScope.recordSliding(
        targetState: Int,
        awaitTerminate: suspend () -> Unit,
    ) {
        with(kosmos) {
            localPlaybackStateBuilder.setState(targetState, 0, 0f)
            fakeMediaControllerInteractor.updateState(
                MediaControllerChangeModel.PlaybackStateChanged(localPlaybackStateBuilder.build())
            )
            testScope.runCurrent()
        }
        awaitFrames()
        awaitTerminate.invoke()
    }

    @Composable
    fun Kosmos.Sliders() {
        val volumePanelState by volumePanelViewModel.volumePanelState.collectAsState()
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            with(VolumePanelComposeScope(volumePanelState)) {
                with(volumeSlidersComponent) { Content(modifier = Modifier) }
            }
        }
    }
}
