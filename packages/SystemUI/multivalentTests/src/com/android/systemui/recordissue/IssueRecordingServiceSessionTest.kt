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

package com.android.systemui.recordissue

import android.app.IActivityManager
import android.app.NotificationManager
import android.media.projection.StopReason
import android.net.Uri
import android.os.Handler
import android.os.UserHandle
import android.testing.TestableLooper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.animation.DialogTransitionAnimator
import com.android.systemui.animation.dialogTransitionAnimator
import com.android.systemui.kosmos.testCase
import com.android.systemui.qs.pipeline.domain.interactor.PanelInteractor
import com.android.systemui.screenrecord.domain.interactor.ScreenRecordingServiceInteractor
import com.android.systemui.settings.UserContextProvider
import com.android.systemui.settings.userFileManager
import com.android.systemui.settings.userTracker
import com.android.systemui.testKosmos
import com.android.systemui.util.settings.fakeGlobalSettings
import com.android.traceur.TraceConfig
import com.google.common.truth.Truth
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.kotlin.any
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

@SmallTest
@RunWith(AndroidJUnit4::class)
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@TestableLooper.RunWithLooper(setAsMainLooper = true)
class IssueRecordingServiceSessionTest : SysuiTestCase() {

    private val kosmos = testKosmos().also { it.testCase = this }
    private val testDispatcher = StandardTestDispatcher()
    private val backgroundContext: CoroutineContext = testDispatcher
    private val testCoroutineScope: CoroutineScope = TestScope(testDispatcher)
    private val userContextProvider: UserContextProvider = kosmos.userTracker
    private val dialogTransitionAnimator: DialogTransitionAnimator = kosmos.dialogTransitionAnimator
    private lateinit var traceurConnection: TraceurConnection
    private val issueRecordingState =
        IssueRecordingState(
            kosmos.userTracker,
            kosmos.userFileManager,
            Handler.getMain(),
            mContext.contentResolver,
            kosmos.fakeGlobalSettings,
        )

    private val iActivityManager = mock<IActivityManager>()
    private val notificationManager = mock<NotificationManager>()
    private val panelInteractor = mock<PanelInteractor>()
    private val screenRecordingStartTimeStore = mock<ScreenRecordingStartTimeStore>()
    private val mockScreenRecordingServiceInteractor: ScreenRecordingServiceInteractor = mock()

    private lateinit var underTest: IssueRecordingServiceSession

    @Before
    fun setup() {
        traceurConnection = mock<TraceurConnection>()
        underTest =
            IssueRecordingServiceSession(
                testCoroutineScope,
                backgroundContext,
                dialogTransitionAnimator,
                panelInteractor,
                traceurConnection,
                issueRecordingState,
                iActivityManager,
                notificationManager,
                userContextProvider,
                screenRecordingStartTimeStore,
                mockScreenRecordingServiceInteractor,
            )
    }

    @Test
    fun startsTracing_afterReceivingActionStartCommand() =
        runTest(testDispatcher.scheduler) {
            underTest.start(0)
            advanceUntilIdle()

            Truth.assertThat(issueRecordingState.isRecording).isTrue()
            verify(traceurConnection).startTracing(any<TraceConfig>())
        }

    @Test
    fun stopsTracing_afterReceivingStopTracingCommand() =
        runTest(testDispatcher.scheduler) {
            underTest.stop(StopReason.STOP_USER_SWITCH)
            advanceUntilIdle()

            Truth.assertThat(issueRecordingState.isRecording).isFalse()
            verify(traceurConnection).stopTracing()
        }

    @Test
    fun cancelsNotification_afterReceivingShareCommand() =
        runTest(testDispatcher.scheduler) {
            underTest.share(0, null)
            advanceUntilIdle()

            verify(notificationManager).cancelAsUser(isNull(), anyInt(), any<UserHandle>())
        }

    @Test
    fun requestBugreport_afterReceivingShareCommand_withTakeBugreportTrue() =
        runTest(testDispatcher.scheduler) {
            underTest.takeBugReport = true
            val uri = mock<Uri>()

            underTest.share(0, uri)
            advanceUntilIdle()

            verify(iActivityManager).requestBugReportWithExtraAttachments(any())
        }

    @Ignore("b/392753499")
    @Test
    fun sharesTracesDirectly_afterReceivingShareCommand_withTakeBugreportFalse() =
        runTest(testDispatcher.scheduler) {
            underTest.takeBugReport = false
            val uri = mock<Uri>()

            underTest.share(0, uri)
            advanceUntilIdle()

            verify(traceurConnection).shareTraces(any())
        }

    @Test
    fun closesShade_afterReceivingShareCommand() =
        runTest(testDispatcher.scheduler) {
            val uri = mock<Uri>()

            underTest.share(0, uri)
            advanceUntilIdle()

            verify(panelInteractor).collapsePanels()
        }
}
