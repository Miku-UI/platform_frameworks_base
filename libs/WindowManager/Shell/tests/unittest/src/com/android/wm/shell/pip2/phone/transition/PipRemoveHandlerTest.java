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

package com.android.wm.shell.pip2.phone.transition;

import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;
import static android.app.WindowConfiguration.WINDOWING_MODE_PINNED;
import static android.view.WindowManager.TRANSIT_CLOSE;
import static android.view.WindowManager.TRANSIT_TO_BACK;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;
import static org.mockito.kotlin.VerificationKt.never;

import android.annotation.Nullable;
import android.app.ActivityManager;
import android.content.Context;
import android.graphics.Rect;
import android.os.IBinder;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import android.view.SurfaceControl;
import android.view.WindowManager;
import android.window.TransitionInfo;
import android.window.TransitionRequestInfo;
import android.window.WindowContainerToken;
import android.window.WindowContainerTransaction;

import androidx.test.filters.SmallTest;

import com.android.testing.wm.util.TransitionInfoBuilder;
import com.android.wm.shell.common.pip.PipBoundsState;
import com.android.wm.shell.pip2.PipSurfaceTransactionHelper;
import com.android.wm.shell.pip2.animation.PipAlphaAnimator;
import com.android.wm.shell.pip2.phone.PipInteractionHandler;
import com.android.wm.shell.pip2.phone.PipTransitionState;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit test against {@link PipRemoveHandler}
 */
@SmallTest
@TestableLooper.RunWithLooper
@RunWith(AndroidTestingRunner.class)
public class PipRemoveHandlerTest {
    private static final Rect PIP_BOUNDS = new Rect(0, 0, 100, 100);
    private static final Rect DISPLAY_BOUNDS = new Rect(0, 0, 1000, 1000);

    @Mock private Context mMockContext;
    @Mock private PipBoundsState mMockPipBoundsState;
    @Mock private PipTransitionState mMockPipTransitionState;
    @Mock private PipInteractionHandler mMockPipInteractionHandler;
    @Mock private PipSurfaceTransactionHelper mMockPipSurfaceTransactionHelper;

    @Mock private IBinder mMockTransitionToken;
    @Mock private SurfaceControl mPipLeash;
    @Mock private SurfaceControl.Transaction mStartTx;
    @Mock private SurfaceControl.Transaction mFinishTx;
    @Mock private PipAlphaAnimator mMockPipAlphaAnimator;

    private PipRemoveHandler mPipRemoveHandler;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mPipRemoveHandler = new PipRemoveHandler(mMockContext, mMockPipSurfaceTransactionHelper,
                mMockPipBoundsState, mMockPipTransitionState, mMockPipInteractionHandler);
        mPipRemoveHandler.setPipAlphaAnimatorSupplier((context, pipSurfaceTransactionHelper,
                leash, startTransaction, finishTransaction, direction) -> mMockPipAlphaAnimator);
    }

    @Test
    public void handleRequest_closingTriggerPipTask_returnsEmptyWCT() {
        final ActivityManager.RunningTaskInfo pipTask = createTaskInfo(1, WINDOWING_MODE_PINNED);
        // Make sure the current PiP task token is the trigger task to CLOSE
        when(mMockPipTransitionState.getPipTaskToken()).thenReturn(pipTask.getToken());
        final TransitionRequestInfo requestInfo = new TransitionRequestInfo(
                TRANSIT_CLOSE, pipTask, null);

        final WindowContainerTransaction wct =
                mPipRemoveHandler.handleRequest(mMockTransitionToken, requestInfo);
        assertTrue(wct.isEmpty());
        assertTrue(mPipRemoveHandler.isRemoveTransition(mMockTransitionToken));
    }

    @Test
    public void handleRequest_closeNonPipTask_returnsNull() {
        final ActivityManager.RunningTaskInfo pipTask = createTaskInfo(1, WINDOWING_MODE_PINNED);
        final ActivityManager.RunningTaskInfo closingTask =
                createTaskInfo(2, WINDOWING_MODE_FULLSCREEN);
        // Make sure the current PiP task token is the trigger task to CLOSE
        when(mMockPipTransitionState.getPipTaskToken()).thenReturn(pipTask.getToken());
        final TransitionRequestInfo requestInfo = new TransitionRequestInfo(
                TRANSIT_CLOSE, closingTask, null);

        final WindowContainerTransaction wct =
                mPipRemoveHandler.handleRequest(mMockTransitionToken, requestInfo);
        assertNull(wct);
        assertFalse(mPipRemoveHandler.isRemoveTransition(mMockTransitionToken));
    }

    @Test
    public void startAnimation_withFadeout_toBackPipTask_startAlphaAnimator() {
        final ActivityManager.RunningTaskInfo pipTask =
                createTaskInfo(1, WINDOWING_MODE_FULLSCREEN);
        when(mMockPipTransitionState.getPipTaskToken()).thenReturn(pipTask.getToken());

        final TransitionInfo info = getRemovePipTransitionInfo(TRANSIT_TO_BACK,
                TRANSIT_TO_BACK, pipTask);
        mPipRemoveHandler.setPendingRemoveWithFadeout(true);
        mPipRemoveHandler.startAnimation(mMockTransitionToken, info, mStartTx, mFinishTx,
                (wct) -> {});
        // Verify state advancement requirement
        verify(mMockPipTransitionState, times(1))
                .setState(eq(PipTransitionState.EXITING_PIP));

        // Check whether the jank cuj tag was triggered.
        verify(mMockPipInteractionHandler, times(1)).begin(any(),
                eq(PipInteractionHandler.INTERACTION_REMOVE_PIP));

        // Check that animator would be started and the right start/finishTx transforms are applied.
        verify(mMockPipAlphaAnimator, times(1)).start();
        verify(mStartTx, times(1)).setWindowCrop(eq(mPipLeash),
                eq(PIP_BOUNDS.width()), eq(PIP_BOUNDS.height()));
        verify(mFinishTx, times(1)).setAlpha(eq(mPipLeash), eq(0f));
    }

    @Test
    public void startAnimation_withoutFadeout_toBackPipTask_startAlphaAnimator() {
        final ActivityManager.RunningTaskInfo pipTask =
                createTaskInfo(1, WINDOWING_MODE_FULLSCREEN);
        when(mMockPipTransitionState.getPipTaskToken()).thenReturn(pipTask.getToken());

        final TransitionInfo info = getRemovePipTransitionInfo(TRANSIT_TO_BACK,
                TRANSIT_TO_BACK, pipTask);
        mPipRemoveHandler.startAnimation(mMockTransitionToken, info, mStartTx, mFinishTx,
                (wct) -> {});
        // Verify state advancement requirement
        verify(mMockPipTransitionState, times(1))
                .setState(eq(PipTransitionState.EXITING_PIP));

        // Check whether the jank cuj tag was triggered.
        verify(mMockPipInteractionHandler, times(1)).begin(any(),
                eq(PipInteractionHandler.INTERACTION_REMOVE_PIP));

        // Check that animator isn't started, but the right start/finishTx transforms are applied.
        verify(mMockPipAlphaAnimator, never()).start();
        verify(mStartTx, times(1)).setAlpha(eq(mPipLeash), eq(0f));
        verify(mFinishTx, times(1)).setAlpha(eq(mPipLeash), eq(0f));
    }

    private static ActivityManager.RunningTaskInfo createTaskInfo(int taskId, int windowingMode) {
        ActivityManager.RunningTaskInfo taskInfo = new ActivityManager.RunningTaskInfo();
        taskInfo.taskId = taskId;
        taskInfo.configuration.windowConfiguration.setWindowingMode(windowingMode);
        taskInfo.token = mock(WindowContainerToken.class);
        return taskInfo;
    }

    private TransitionInfo getRemovePipTransitionInfo(@WindowManager.TransitionType int type,
            @TransitionInfo.TransitionMode int mode,
            @Nullable ActivityManager.RunningTaskInfo pipTaskInfo) {
        final TransitionInfo info = new TransitionInfoBuilder(type)
                .addChange(mode, pipTaskInfo).build();
        final TransitionInfo.Change pipChange = info.getChanges().getFirst();
        pipChange.setStartAbsBounds(PIP_BOUNDS);
        pipChange.setEndAbsBounds(DISPLAY_BOUNDS);
        pipChange.setLeash(mPipLeash);
        return info;
    }
}
