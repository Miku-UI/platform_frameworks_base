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

package com.android.systemui.keyguard.data.repository

import android.app.ActivityManager.RunningTaskInfo
import android.app.WindowConfiguration
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.FlagsParameterization
import android.service.dreams.Flags.FLAG_DRIVE_DREAM_STATE_FROM_OCCLUSION
import android.view.Display
import androidx.test.filters.SmallTest
import com.android.keyguard.logging.KeyguardLogger
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.applicationCoroutineScope
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.log.logcatLogBuffer
import com.android.systemui.testKosmos
import com.android.wm.shell.ShellTaskOrganizer.KeyguardOccludingTaskListener
import com.android.wm.shell.keyguard.keyguardTransitions
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import platform.test.runner.parameterized.ParameterizedAndroidJunit4
import platform.test.runner.parameterized.Parameters

@SmallTest
@RunWith(ParameterizedAndroidJunit4::class)
@OptIn(ExperimentalCoroutinesApi::class)
class KeyguardOcclusionRepositoryTest(flags: FlagsParameterization) : SysuiTestCase() {

    private val kosmos = testKosmos().useUnconfinedTestDispatcher()
    private val Kosmos.underTest: KeyguardOcclusionRepository by
        Kosmos.Fixture {
            KeyguardOcclusionRepositoryImpl(
                keyguardTransitions = keyguardTransitions,
                logger = KeyguardLogger(logcatLogBuffer()),
                applicationScope = applicationCoroutineScope,
            )
        }

    companion object {
        @JvmStatic
        @Parameters(name = "{0}")
        fun getParams(): List<FlagsParameterization> {
            return FlagsParameterization.allCombinationsOf(FLAG_DRIVE_DREAM_STATE_FROM_OCCLUSION)
        }
    }

    init {
        mSetFlagsRule.setFlagsParameterization(flags)
    }

    @Test
    fun showWhenLockedActivityInfo_initialValue() =
        kosmos.runTest {
            val info by collectLastValue(underTest.showWhenLockedActivityInfo)
            assertThat(info?.isOnTop).isFalse()
            assertThat(info?.taskInfo).isNull()
        }

    @Test
    fun setOccludedFromRemoteAnimation_updatesFlow() =
        kosmos.runTest {
            val info by collectLastValue(underTest.showWhenLockedActivityInfo)
            val taskInfo =
                RunningTaskInfo().apply {
                    topActivityType = WindowConfiguration.ACTIVITY_TYPE_STANDARD
                }

            underTest.setOccludedFromRemoteAnimation(onTop = true, taskInfo = taskInfo)

            assertThat(info?.isOnTop).isTrue()
            assertThat(info?.taskInfo).isEqualTo(taskInfo)
        }

    @Test
    @EnableFlags(FLAG_DRIVE_DREAM_STATE_FROM_OCCLUSION)
    fun setOccludedFromWm_updatesFlow_whenFlagOn() =
        kosmos.runTest {
            val info by collectLastValue(underTest.showWhenLockedActivityInfo)

            underTest.setOccludedFromWm(true)
            assertThat(info?.isOnTop).isTrue()

            underTest.setOccludedFromWm(false)
            assertThat(info?.isOnTop).isFalse()
            assertThat(info?.taskInfo).isNull()
        }

    @Test
    @EnableFlags(FLAG_DRIVE_DREAM_STATE_FROM_OCCLUSION)
    fun taskChanged_then_setOccluded_updatesFlow() =
        kosmos.runTest {
            val info by collectLastValue(underTest.showWhenLockedActivityInfo)
            val taskInfo =
                RunningTaskInfo().apply {
                    topActivityType = WindowConfiguration.ACTIVITY_TYPE_STANDARD
                }
            val listener = getOccludingTaskListener()

            // Pre-signal from Shell
            listener.onKeyguardOccludingTaskChanged(Display.DEFAULT_DISPLAY, taskInfo)
            // State shouldn't be occluded yet, but task info is buffered
            assertThat(info?.isOnTop).isFalse()
            assertThat(info?.taskInfo).isEqualTo(taskInfo)

            // Confirmation from WM
            underTest.setOccludedFromWm(true)
            assertThat(info?.isOnTop).isTrue()
            assertThat(info?.taskInfo).isEqualTo(taskInfo)
        }

    @Test
    @EnableFlags(FLAG_DRIVE_DREAM_STATE_FROM_OCCLUSION)
    fun setOccluded_then_taskChanged_updatesFlow() =
        kosmos.runTest {
            val info by collectLastValue(underTest.showWhenLockedActivityInfo)
            val taskInfo =
                RunningTaskInfo().apply {
                    topActivityType = WindowConfiguration.ACTIVITY_TYPE_STANDARD
                }
            val listener = getOccludingTaskListener()

            // Confirmation from WM
            underTest.setOccludedFromWm(true)
            assertThat(info?.isOnTop).isTrue()
            assertThat(info?.taskInfo).isNull()

            // Signal from Shell
            listener.onKeyguardOccludingTaskChanged(Display.DEFAULT_DISPLAY, taskInfo)
            assertThat(info?.isOnTop).isTrue()
            assertThat(info?.taskInfo).isEqualTo(taskInfo)
        }

    @Test
    @EnableFlags(FLAG_DRIVE_DREAM_STATE_FROM_OCCLUSION)
    fun unoccludeSequence_maintainsTemporalStability() =
        kosmos.runTest {
            val info by collectLastValue(underTest.showWhenLockedActivityInfo)
            val taskInfo =
                RunningTaskInfo().apply {
                    topActivityType = WindowConfiguration.ACTIVITY_TYPE_STANDARD
                }
            val listener = getOccludingTaskListener()

            // Start occluded
            listener.onKeyguardOccludingTaskChanged(Display.DEFAULT_DISPLAY, taskInfo)
            underTest.setOccludedFromWm(true)
            assertThat(info?.isOnTop).isTrue()
            assertThat(info?.taskInfo).isEqualTo(taskInfo)

            // Shell says task is gone (pre-signal for unocclude)
            listener.onKeyguardOccludingTaskChanged(Display.DEFAULT_DISPLAY, null)

            // State should NOT drop taskInfo yet, because we are still occluded!
            assertThat(info?.isOnTop).isTrue()
            assertThat(info?.taskInfo).isEqualTo(taskInfo)

            // WM confirms unoccluded
            underTest.setOccludedFromWm(false)
            assertThat(info?.isOnTop).isFalse()
            assertThat(info?.taskInfo).isNull()
        }

    @Test
    @EnableFlags(FLAG_DRIVE_DREAM_STATE_FROM_OCCLUSION)
    fun wmOcclusionSignal_filtersDisplay_whenFlagOn() =
        kosmos.runTest {
            val info by collectLastValue(underTest.showWhenLockedActivityInfo)
            val taskInfo =
                RunningTaskInfo().apply {
                    topActivityType = WindowConfiguration.ACTIVITY_TYPE_STANDARD
                }
            val listener = getOccludingTaskListener()

            // Signal for non-default display
            listener.onKeyguardOccludingTaskChanged(Display.DEFAULT_DISPLAY + 1, taskInfo)

            // Should remain at initial value
            assertThat(info?.isOnTop).isFalse()
        }

    @Test
    @DisableFlags(FLAG_DRIVE_DREAM_STATE_FROM_OCCLUSION)
    fun wmOcclusionSignal_doesNotRun_whenFlagOff() =
        kosmos.runTest {
            val info by collectLastValue(underTest.showWhenLockedActivityInfo)
            assertThat(info?.isOnTop).isFalse()

            verify(keyguardTransitions, never()).registerOccludingTaskListener(any())
        }

    private fun Kosmos.getOccludingTaskListener(): KeyguardOccludingTaskListener {
        val captor = argumentCaptor<KeyguardOccludingTaskListener>()
        verify(keyguardTransitions).registerOccludingTaskListener(captor.capture())
        return captor.firstValue
    }
}
