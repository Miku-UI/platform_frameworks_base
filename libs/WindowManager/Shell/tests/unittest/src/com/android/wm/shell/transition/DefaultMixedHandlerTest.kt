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

package com.android.wm.shell.transition

import android.app.ActivityManager.RunningTaskInfo
import android.app.WindowConfiguration.ACTIVITY_TYPE_STANDARD
import android.os.Binder
import android.platform.test.annotations.EnableFlags
import android.view.SurfaceControl
import android.view.WindowManager.TRANSIT_CLOSE
import android.view.WindowManager.TRANSIT_FLAG_KEYGUARD_GOING_AWAY
import android.view.WindowManager.TRANSIT_OPEN
import android.window.IRemoteTransition
import android.window.RemoteTransition
import android.window.TransitionInfo
import android.window.TransitionRequestInfo
import android.window.WindowContainerToken
import android.window.WindowContainerTransaction
import androidx.test.filters.SmallTest
import com.android.dx.mockito.inline.extended.ExtendedMockito.spyOn
import com.android.testing.wm.util.MockToken
import com.android.wm.shell.Flags.FLAG_ENABLE_CREATE_ANY_BUBBLE
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.TestShellExecutor
import com.android.wm.shell.activityembedding.ActivityEmbeddingController
import com.android.wm.shell.bubbles.BubbleController
import com.android.wm.shell.bubbles.BubbleData
import com.android.wm.shell.bubbles.BubbleHelperImpl
import com.android.wm.shell.bubbles.BubbleRootTask
import com.android.wm.shell.bubbles.transitions.BubbleTransitions
import com.android.wm.shell.desktopmode.DesktopTasksController
import com.android.wm.shell.desktopmode.NormalAppLayerHandler
import com.android.wm.shell.desktopmode.desktoptaskshandlers.DesktopTasksTransitionHandler
import com.android.wm.shell.keyguard.KeyguardTransitionHandler
import com.android.wm.shell.pinnedlayer.phone.PinnedLayerHandler
import com.android.wm.shell.pip.PipTransitionController
import com.android.wm.shell.pip2.phone.PipScheduler
import com.android.wm.shell.recents.RecentsTransitionHandler
import com.android.wm.shell.splitscreen.SplitScreenController
import com.android.wm.shell.splitscreen.StageCoordinator
import com.android.wm.shell.sysui.ShellInit
import com.android.wm.shell.transition.DefaultMixedHandler.MixedTransition.TYPE_LAUNCH_OR_CONVERT_TO_BUBBLE
import com.android.wm.shell.unfold.UnfoldTransitionHandler
import com.google.common.truth.Truth.assertThat
import java.util.Optional
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.spy
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify

/** Tests for [DefaultMixedHandler] Build & Run: atest WMShellUnitTests:DefaultMixedHandlerTest */
@SmallTest
class DefaultMixedHandlerTest : ShellTestCase() {

    private val transitions = mock<Transitions>()
    private val splitScreenController =
        mock<SplitScreenController> { on { transitionHandler } doReturn mock<StageCoordinator>() }
    private val pipTransitionController = mock<PipTransitionController>()
    private val recentsTransitionHandler = mock<RecentsTransitionHandler>()
    private val keyguardTransitionHandler = mock<KeyguardTransitionHandler>()
    private val desktopTasksController = mock<DesktopTasksController>()
    private val desktopTasksTransitionHandler = mock<DesktopTasksTransitionHandler>()
    private val unfoldTransitionHandler = mock<UnfoldTransitionHandler>()
    private val activityEmbeddingController = mock<ActivityEmbeddingController>()
    private val bubbleController = mock<BubbleController>()
    private val bubbleRootTask = mock<BubbleRootTask>()
    private val bubbleHelper = spy(BubbleHelperImpl(bubbleRootTask, mock<BubbleData>()))
    private val pinnedLayerHandler = mock<PinnedLayerHandler>()
    private val normalAppLayerHandler = mock<NormalAppLayerHandler>()
    private val pipScheduler = mock<PipScheduler>()
    private val shellInit: ShellInit = ShellInit(TestShellExecutor())

    private lateinit var bubbleTransitions: BubbleTransitions
    private lateinit var mixedHandler: DefaultMixedHandler

    @Before
    fun setUp() {
        bubbleTransitions =
            spy(
                BubbleTransitions(
                    mContext,
                    transitions,
                    mock(),
                    mock(),
                    mock(),
                    mock(),
                    mock(),
                    bubbleHelper,
                )
            )
        mixedHandler =
            DefaultMixedHandler(
                shellInit,
                transitions,
                Optional.of(splitScreenController),
                pipTransitionController,
                Optional.of(pipScheduler),
                normalAppLayerHandler,
                pinnedLayerHandler,
                Optional.of(recentsTransitionHandler),
                keyguardTransitionHandler,
                Optional.of(desktopTasksController),
                desktopTasksTransitionHandler,
                Optional.of(unfoldTransitionHandler),
                Optional.of(activityEmbeddingController),
                bubbleTransitions,
                bubbleHelper,
            )
        shellInit.init()
        bubbleTransitions.setBubbleController(bubbleController)
    }

    @Test
    @EnableFlags(FLAG_ENABLE_CREATE_ANY_BUBBLE)
    fun test_requestHasBubbleEnter_noTriggerTask_notHandled() {
        val noTriggerTaskRequest = createTransitionRequestInfo()

        assertThat(mixedHandler.requestHasBubbleEnter(noTriggerTaskRequest)).isFalse()
    }

    @Test
    @EnableFlags(FLAG_ENABLE_CREATE_ANY_BUBBLE)
    fun test_requestHasBubbleEnter_noPendingEnterTransition_notHandled() {
        val runningTask = createRunningTask()
        val request = createTransitionRequestInfo(runningTask)

        bubbleTransitions.stub { on { hasPendingEnterTransition(request) } doReturn false }

        assertThat(mixedHandler.requestHasBubbleEnter(request)).isFalse()
    }

    @Test
    @EnableFlags(FLAG_ENABLE_CREATE_ANY_BUBBLE)
    fun test_requestHasBubbleEnter_notShowingAsBubbleBar() {
        val runningTask = createRunningTask()
        val request = createTransitionRequestInfo(runningTask)

        bubbleTransitions.stub { on { hasPendingEnterTransition(request) } doReturn true }

        assertThat(mixedHandler.requestHasBubbleEnter(request)).isTrue()
    }

    @Test
    @EnableFlags(FLAG_ENABLE_CREATE_ANY_BUBBLE)
    fun test_requestHasBubbleEnter() {
        val runningTask = createRunningTask()
        val request = createTransitionRequestInfo(runningTask)

        bubbleTransitions.stub { on { hasPendingEnterTransition(request) } doReturn true }

        assertThat(mixedHandler.requestHasBubbleEnter(request)).isTrue()
    }

    @Test
    @EnableFlags(FLAG_ENABLE_CREATE_ANY_BUBBLE)
    fun test_requestBubbleEnterConsumesRemote() {
        val runningTask = createRunningTask()
        val remoteTransition = mock<IRemoteTransition>()
        val request = createTransitionRequestInfo(runningTask, RemoteTransition(remoteTransition))

        bubbleTransitions.stub { on { hasPendingEnterTransition(request) } doReturn true }
        doReturn(mock<Transitions.TransitionHandler>())
            .`when`(bubbleTransitions)
            .storePendingEnterTransition(any(), any())

        mixedHandler.handleRequestOnly(Binder(), request)
        verify(remoteTransition).onTransitionConsumed(any(), eq(false))
    }

    @Test
    @EnableFlags(FLAG_ENABLE_CREATE_ANY_BUBBLE)
    fun test_requestHasBubbleEnterFromAppBubbleOrExistingBubble_noTriggerTask_notHandled() {
        val noTriggerTaskRequest = createTransitionRequestInfo()

        assertThat(
                mixedHandler.requestHasBubbleEnterFromAppBubbleOrExistingBubble(
                    noTriggerTaskRequest
                )
            )
            .isFalse()
    }

    @Test
    @EnableFlags(FLAG_ENABLE_CREATE_ANY_BUBBLE)
    fun test_requestHasBubbleEnterFromAppBubbleOrExistingBubble_notAppBubble_notHandled() {
        val runningTask = createRunningTask(100)
        val request = createTransitionRequestInfo(runningTask)

        bubbleHelper.stub { onGeneric { isAppBubbleTask(any()) } doReturn false }

        assertThat(mixedHandler.requestHasBubbleEnterFromAppBubbleOrExistingBubble(request))
            .isFalse()
    }

    @Test
    @EnableFlags(FLAG_ENABLE_CREATE_ANY_BUBBLE)
    fun test_requestHasBubbleEnterFromAppBubbleOrExistingBubble_notShowingAsBubbleBar() {
        val runningTask = createRunningTask(100)
        val request = createTransitionRequestInfo(runningTask)

        bubbleHelper.stub { onGeneric { isAppBubbleTask(any()) } doReturn true }

        assertThat(mixedHandler.requestHasBubbleEnterFromAppBubbleOrExistingBubble(request))
            .isTrue()
    }

    @Test
    @EnableFlags(FLAG_ENABLE_CREATE_ANY_BUBBLE)
    fun test_requestHasBubbleEnterFromAppBubbleOrExistingBubble() {
        val runningTask = createRunningTask(100)
        val request = createTransitionRequestInfo(runningTask)

        bubbleHelper.stub { onGeneric { isAppBubbleTask(any()) } doReturn true }

        assertThat(mixedHandler.requestHasBubbleEnterFromAppBubbleOrExistingBubble(request))
            .isTrue()
    }

    @Test
    @EnableFlags(FLAG_ENABLE_CREATE_ANY_BUBBLE)
    fun test_handleRequest_bubbleEnterFromAppBubble_consumesRemote() {
        val runningTask = createRunningTask(100)
        val remoteTransition = mock<IRemoteTransition>()
        val request = createTransitionRequestInfo(runningTask, RemoteTransition(remoteTransition))

        bubbleHelper.stub { onGeneric { isAppBubbleTask(any()) } doReturn true }

        mixedHandler.handleRequestOnly(Binder(), request)

        verify(remoteTransition).onTransitionConsumed(any(), eq(false))
    }

    @Test
    @EnableFlags(FLAG_ENABLE_CREATE_ANY_BUBBLE)
    fun test_startAnimation_NoBubbleEnterFromAppBubble() {
        val info = TransitionInfo(TRANSIT_OPEN, 0)

        bubbleHelper.stub { onGeneric { isAppBubbleTask(any()) } doReturn true }

        mixedHandler.startAnimation(
            Binder(),
            info,
            mock<SurfaceControl.Transaction>(),
            mock<SurfaceControl.Transaction>(),
            mock<Transitions.TransitionFinishCallback>(),
        )

        verify(bubbleTransitions, never())
            .startExpandAndSelectBubbleForExistingTransition(any(), any(), any())
    }

    @Test
    @EnableFlags(FLAG_ENABLE_CREATE_ANY_BUBBLE)
    fun test_startAnimation_bubbleEnterFromAppBubble() {
        val change = TransitionInfo.Change(mock<WindowContainerToken>(), mock<SurfaceControl>())
        change.mode = TRANSIT_OPEN
        change.taskInfo = createRunningTask()
        val info = TransitionInfo(TRANSIT_OPEN, 0)
        info.addChange(change)

        bubbleHelper.stub { onGeneric { isAppBubbleTask(any()) } doReturn true }

        mixedHandler.startAnimation(
            Binder(),
            info,
            mock<SurfaceControl.Transaction>(),
            mock<SurfaceControl.Transaction>(),
            mock<Transitions.TransitionFinishCallback>(),
        )

        verify(bubbleTransitions)
            .startExpandAndSelectBubbleForExistingTransition(any(), any(), any())
    }

    @Test
    @EnableFlags(FLAG_ENABLE_CREATE_ANY_BUBBLE)
    fun test_startAnimation_taskTrampolineBubbleLaunch() {
        val openingChange =
            TransitionInfo.Change(mock<WindowContainerToken>(), mock<SurfaceControl>())
        openingChange.mode = TRANSIT_OPEN
        openingChange.taskInfo = createRunningTask()
        val closingChange =
            TransitionInfo.Change(mock<WindowContainerToken>(), mock<SurfaceControl>())
        closingChange.mode = TRANSIT_CLOSE
        closingChange.taskInfo = createRunningTask()
        val info = TransitionInfo(TRANSIT_OPEN, 0)
        info.addChange(openingChange)
        info.addChange(closingChange)

        bubbleHelper.stub { onGeneric { isAppBubbleTask(any()) } doReturn true }

        mixedHandler.startAnimation(
            Binder(),
            info,
            mock<SurfaceControl.Transaction>(),
            mock<SurfaceControl.Transaction>(),
            mock<Transitions.TransitionFinishCallback>(),
        )

        verify(bubbleTransitions)
            .startTaskTrampolineBubbleLaunch(
                any(),
                eq(openingChange.taskInfo!!),
                eq(closingChange.taskInfo!!),
                any(),
            )
    }

    @Test
    fun test_startAnimation_prevMixedCanNotAnimateTransition() {
        spyOn(mixedHandler)
        val transition = Binder()
        val mixedTransition =
            spy(
                DefaultMixedTransition(
                    TYPE_LAUNCH_OR_CONVERT_TO_BUBBLE,
                    transition,
                    transitions,
                    mixedHandler,
                    pipTransitionController,
                    splitScreenController.getTransitionHandler(),
                    keyguardTransitionHandler,
                    unfoldTransitionHandler,
                    activityEmbeddingController,
                    desktopTasksController,
                    bubbleTransitions,
                    bubbleHelper,
                    pinnedLayerHandler,
                )
            )
        mixedHandler.mActiveTransitions.add(mixedTransition)
        val info = TransitionInfo(TRANSIT_OPEN, 0)
        doReturn(false).`when`(mixedTransition).canAnimateTransition(transition, info)

        mixedHandler.startAnimation(
            transition,
            info,
            mock<SurfaceControl.Transaction>(),
            mock<SurfaceControl.Transaction>(),
            mock<Transitions.TransitionFinishCallback>(),
        )

        verify(mixedTransition).onTransitionConsumed(eq(transition), eq(true), any())
        assertThat(mixedHandler.mActiveTransitions.contains(mixedTransition)).isFalse()
    }

    @Test
    @EnableFlags(FLAG_ENABLE_CREATE_ANY_BUBBLE)
    fun test_startAnimation_bubbleOpensFromKeyguard() {
        val transition = Binder()
        val info = TransitionInfo(TRANSIT_OPEN, TRANSIT_FLAG_KEYGUARD_GOING_AWAY)

        // Set up bubble change
        val change = TransitionInfo.Change(mock<WindowContainerToken>(), mock<SurfaceControl>())
        change.mode = TRANSIT_OPEN
        change.taskInfo = createRunningTask()
        info.addChange(change)
        bubbleHelper.stub {
            onGeneric { isAppBubbleTask(any()) } doReturn true
            onGeneric { getEnterBubbleTask(any()) } doReturn change
        }
        bubbleTransitions.stub { on { canAnimateTransition(any(), any()) } doReturn true }

        val mixedTransition =
            DefaultMixedTransition(
                TYPE_LAUNCH_OR_CONVERT_TO_BUBBLE,
                transition,
                transitions,
                mixedHandler,
                pipTransitionController,
                splitScreenController.getTransitionHandler(),
                keyguardTransitionHandler,
                unfoldTransitionHandler,
                activityEmbeddingController,
                desktopTasksController,
                bubbleTransitions,
                bubbleHelper,
                pinnedLayerHandler,
            )
        mixedHandler.mActiveTransitions.add(mixedTransition)

        // Make sure keyguard handles it
        keyguardTransitionHandler.stub {
            on { startAnimation(eq(transition), any(), any(), any(), any()) } doReturn true
        }

        val startT = mock<SurfaceControl.Transaction>()
        val finishT = mock<SurfaceControl.Transaction>()
        val finishCallback = mock<Transitions.TransitionFinishCallback>()
        mixedHandler.startAnimation(transition, info, startT, finishT, finishCallback)

        // Verify Keyguard handler started
        val callbackCaptor = argumentCaptor<Transitions.TransitionFinishCallback>()
        verify(keyguardTransitionHandler)
            .startAnimation(
                eq(transition),
                any(),
                eq(startT),
                eq(finishT),
                callbackCaptor.capture(),
            )

        // Verify bubble NOT started yet
        verify(bubbleTransitions, never())
            .startExpandAndSelectBubbleForExistingTransition(any(), any(), any())

        // Check that bubble starts after keyguard finishes
        val wct = mock<WindowContainerTransaction>()
        callbackCaptor.firstValue.onTransitionFinished(wct)

        verify(bubbleTransitions)
            .startExpandAndSelectBubbleForExistingTransition(
                eq(transition),
                eq(change.taskInfo!!),
                any(),
            )
    }

    private fun createTransitionRequestInfo(
        runningTask: RunningTaskInfo? = null,
        remote: RemoteTransition? = null,
    ): TransitionRequestInfo {
        val remoteInfo =
            if (remote != null) TransitionRequestInfo.RemoteTransitionInfo(remote) else null
        return TransitionRequestInfo(TRANSIT_OPEN, runningTask, remoteInfo)
    }

    private fun createRunningTask(taskId: Int = 0): RunningTaskInfo {
        return RunningTaskInfo().apply {
            this.taskId = taskId
            this.token = MockToken().token()
            this.configuration.windowConfiguration.activityType = ACTIVITY_TYPE_STANDARD
        }
    }
}
