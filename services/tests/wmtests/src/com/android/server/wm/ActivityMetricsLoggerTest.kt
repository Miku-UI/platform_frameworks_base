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

package com.android.server.wm

import android.app.ActivityManager.START_SUCCESS
import android.app.ActivityOptions
import android.app.WindowConfiguration.WINDOWING_MODE_MULTI_WINDOW
import android.content.pm.ActivityInfo
import android.platform.test.annotations.EnableFlags
import android.platform.test.annotations.Presubmit
import android.util.ArrayMap
import androidx.test.filters.SmallTest
import com.android.server.wm.ActivityMetricsLogger.LaunchingState
import com.android.server.wm.ActivityTaskManagerInternal.APP_TRANSITION_WINDOWS_DRAWN
import com.android.server.wm.WindowTestsBase.ActivityBuilder.DEFAULT_FAKE_UID
import com.android.window.flags.Flags.FLAG_INHERIT_CONSECUTIVE_LAUNCH_CALLER_UID
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for the [ActivityMetricsLogger] class.
 *
 * Build/Install/Run:
 *  atest WmTests:ActivityMetricsLoggerTest
 */
@SmallTest
@Presubmit
@RunWith(WindowTestRunner::class)
class ActivityMetricsLoggerTest : WindowTestsBase() {

    private val metricsLogger: ActivityMetricsLogger
        get() = mSupervisor.activityMetricsLogger

    private val rootHomeTask: Task
        get() = mRootWindowContainer.defaultTaskDisplayArea.rootHomeTask!!

    @Test
    fun testLaunchingStateTracksOriginator_freshLaunchSameLaunchingState_returnsFirstCallingUid() {
        val originalCallerUid = DEFAULT_FAKE_UID + 1
        val initialActivity = createActivityRecord(mDisplayContent).apply {
            info.applicationInfo.uid = originalCallerUid
        }
        val launchingState = LaunchingState()

        assertWithMessage("First launch must track initial activity uid as originator")
            .that(launchingState.tracksOriginator(caller = initialActivity))
            .isEqualTo(originalCallerUid)

        val nextActivity = createActivityRecord(mDisplayContent)
        assertWithMessage("Subsequent launch in the same launching state must return originator")
            .that(launchingState.tracksOriginator(caller = nextActivity))
            .isEqualTo(originalCallerUid)
    }

    @Test
    @EnableFlags(FLAG_INHERIT_CONSECUTIVE_LAUNCH_CALLER_UID)
    fun testLaunchingStateTracksOriginator_consecutiveLaunch_carriesOriginatorAcrossTransition() {
        val homeUid = DEFAULT_FAKE_UID + 1
        val homeActivity = createActivityRecord(rootHomeTask).apply {
            info.applicationInfo.uid = homeUid
        }
        val loadingActivity = createActivityRecord(mDisplayContent)
        metricsLogger.notifyActivityLaunching(caller = homeActivity, target = loadingActivity).run {
            tracksOriginator(caller = homeActivity, callingActivityInfo = loadingActivity.info)
            metricsLogger.notifyActivityLaunched(this, launched = loadingActivity)
            metricsLogger.notifyWindowsDrawn(loadingActivity)
        }

        val targetActivity = createNonAttachedActivityRecord(mDisplayContent)
        val launchingState =
            metricsLogger.notifyActivityLaunching(caller = loadingActivity, target = targetActivity)
        val originalCallerUid =
            launchingState.tracksOriginator(
                caller = loadingActivity,
                callingActivityInfo = targetActivity.info,
            )

        assertWithMessage("Consecutive launch should carry originator uid across transition")
            .that(originalCallerUid)
            .isEqualTo(homeUid)
    }

    @Test
    @EnableFlags(FLAG_INHERIT_CONSECUTIVE_LAUNCH_CALLER_UID)
    fun testLaunchingStateTracksOriginator_callerInMultiWindow_returnsCurrentCallerUid() {
        val callerUid = DEFAULT_FAKE_UID + 1
        val callerActivity = createActivityRecord(mDisplayContent).apply {
            info.applicationInfo.uid = callerUid
            task.windowingMode = WINDOWING_MODE_MULTI_WINDOW
        }
        val callingActivity = createNonAttachedActivityRecord(mDisplayContent)

        val uid = metricsLogger.notifyActivityLaunching(callerActivity, callingActivity).run {
            tracksOriginator(callerActivity, callingActivityInfo = callingActivity.info)
        }

        assertWithMessage("Skip originator inheritance on non-fullscreen top running activity")
            .that(uid)
            .isEqualTo(callerUid)
    }

    @Test
    @EnableFlags(FLAG_INHERIT_CONSECUTIVE_LAUNCH_CALLER_UID)
    fun testLaunchingStateTracksOriginator_callerPackageMismatch_returnsCurrentCallerUid() {
        val callerUid = DEFAULT_FAKE_UID + 1
        val callerActivity = ActivityBuilder(mAtm)
            .setComponent(getUniqueComponentName("com.other.package"))
            .setUid(callerUid)
            .build()
        val callingActivity = createActivityRecord(mDisplayContent)

        val uid = metricsLogger.notifyActivityLaunching(callerActivity, callingActivity).run {
            tracksOriginator(callerActivity, callingActivityInfo = callingActivity.info)
        }

        assertWithMessage("Skip originator inheritance on caller/top running activity mismatch")
            .that(uid)
            .isEqualTo(callerUid)
    }

    @Test
    fun testNotifyActivityLaunching_taskTrampolineWithinActiveTransition_returnsSameLaunchState() {
        val homeActivity = createActivityRecord(rootHomeTask)
        val initialActivity = createActivityRecord(mDisplayContent)
        val initLaunchingState =
            metricsLogger.notifyActivityLaunching(caller = homeActivity, target = initialActivity)
        metricsLogger.notifyActivityLaunched(initLaunchingState, launched = initialActivity)

        val nextActivity = createActivityRecord(mDisplayContent)
        val nextLaunchingState =
            metricsLogger.notifyActivityLaunching(caller = initialActivity, target = nextActivity)

        assertWithMessage("Active transition launch must return the matching state")
            .that(nextLaunchingState)
            .isEqualTo(initLaunchingState)
    }

    @Test
    fun testNotifyActivityLaunching_taskTrampolineAfterWindowDrawn_returnsNewLaunchState() {
        val homeActivity = createActivityRecord(rootHomeTask)
        val initialActivity = createActivityRecord(mDisplayContent)
        val initLaunchingState =
            metricsLogger.notifyActivityLaunching(caller = homeActivity, target = initialActivity)
        metricsLogger.notifyActivityLaunched(initLaunchingState, launched = initialActivity)
        metricsLogger.notifyWindowsDrawn(initialActivity)

        val nextActivity = createActivityRecord(mDisplayContent)
        val nextLaunchingState =
            metricsLogger.notifyActivityLaunching(caller = initialActivity, target = nextActivity)

        assertWithMessage("New transition launch after window drawn must return a new state")
            .that(nextLaunchingState)
            .isNotEqualTo(initLaunchingState)
    }

    @Test
    fun testNotifyActivityLaunching_taskTrampolineAfterTransitionStarting_returnsNewLaunchState() {
        val homeActivity = createActivityRecord(rootHomeTask)
        val initialActivity = createActivityRecord(mDisplayContent)
        val initLaunchingState =
            metricsLogger.notifyActivityLaunching(caller = homeActivity, target = initialActivity)
        metricsLogger.notifyActivityLaunched(initLaunchingState, launched = initialActivity)

        val interActivity = createActivityRecord(initialActivity.task)
        val interLaunchingState =
            metricsLogger.notifyActivityLaunching(caller = initialActivity, target = interActivity)
        interActivity.mReportedDrawn = true
        metricsLogger.notifyActivityLaunched(interLaunchingState, launched = interActivity)
        val reasons = ArrayMap<WindowContainer<*>, Int>().apply {
            put(interActivity, APP_TRANSITION_WINDOWS_DRAWN)
        }
        metricsLogger.notifyTransitionStarting(reasons)

        val finalActivity = createActivityRecord(mDisplayContent)
        val finalLaunchingState =
            metricsLogger.notifyActivityLaunching(caller = interActivity, target = finalActivity)

        assertWithMessage("New transition launch after transition starting must return a new state")
            .that(finalLaunchingState)
            .isNotEqualTo(interLaunchingState)
    }

    private fun ActivityMetricsLogger.notifyActivityLaunching(
        caller: ActivityRecord,
        target: ActivityRecord,
    ): LaunchingState = notifyActivityLaunching(target.intent, caller, caller.uid)

    private fun ActivityMetricsLogger.notifyActivityLaunched(
        launchingState: LaunchingState,
        launched: ActivityRecord,
        resultCode: Int = START_SUCCESS,
        newActivityCreated: Boolean = true,
        options: ActivityOptions? = null,
    ) = notifyActivityLaunched(launchingState, resultCode, newActivityCreated, launched, options)

    private fun LaunchingState.tracksOriginator(
        caller: ActivityRecord,
        callingActivityInfo: ActivityInfo? = null,
    ): Int = tracksOriginator {
        metricsLogger.getOriginatorForConsecutiveLaunch(caller, callingActivityInfo, caller.uid)
    }
}
