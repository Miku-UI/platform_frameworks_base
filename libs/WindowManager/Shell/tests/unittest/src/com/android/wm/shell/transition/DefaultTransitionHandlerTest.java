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

package com.android.wm.shell.transition;

import static android.app.WindowConfiguration.ACTIVITY_TYPE_STANDARD;
import static android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM;
import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;
import static android.view.WindowManager.TRANSIT_CHANGE;
import static android.view.WindowManager.TRANSIT_FLAG_KEYGUARD_APPEARING;
import static android.view.WindowManager.TRANSIT_OPEN;
import static android.view.WindowManager.TRANSIT_SLEEP;
import static android.view.WindowManager.TRANSIT_TO_BACK;
import static android.window.TransitionInfo.FLAG_SYNC;
import static android.window.TransitionInfo.FLAG_TRANSLUCENT;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atMostOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.animation.ValueAnimator;
import android.app.ActivityManager.RunningTaskInfo;
import android.content.Context;
import android.graphics.Point;
import android.graphics.PointF;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.util.Log;
import android.view.SurfaceControl;
import android.view.animation.AlphaAnimation;
import android.window.TransitionInfo;
import android.window.WindowContainerToken;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.internal.jank.InteractionJankMonitor;
import com.android.testing.wm.util.ChangeBuilder;
import com.android.testing.wm.util.TransitionInfoBuilder;
import com.android.wm.shell.RootTaskDisplayAreaOrganizer;
import com.android.wm.shell.ShellTestCase;
import com.android.wm.shell.TestShellExecutor;
import com.android.wm.shell.common.DisplayController;
import com.android.wm.shell.common.DisplayInsetsController;
import com.android.wm.shell.shared.TransactionPool;
import com.android.wm.shell.sysui.ShellInit;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for the default animation handler that is used if no other special-purpose handler picks
 * up an animation request.
 *
 * Build/Install/Run:
 * atest WMShellUnitTests:DefaultTransitionHandlerTest
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class DefaultTransitionHandlerTest extends ShellTestCase {

    private final Context mContext =
            InstrumentationRegistry.getInstrumentation().getTargetContext();

    private final DisplayController mDisplayController = mock(DisplayController.class);
    private final DisplayInsetsController mDisplayInsetsController =
            mock(DisplayInsetsController.class);
    private final TransactionPool mTransactionPool = new MockTransactionPool();
    private final TestShellExecutor mMainExecutor = new TestShellExecutor();
    private final TestShellExecutor mAnimExecutor = new TestShellExecutor();
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    private ShellInit mShellInit;
    private RootTaskDisplayAreaOrganizer mRootTaskDisplayAreaOrganizer;
    private DefaultTransitionHandler mTransitionHandler;

    static final String TAG = "DefaultHandlerTest";

    @Before
    public void setUp() {
        mShellInit = new ShellInit(mMainExecutor);
        mRootTaskDisplayAreaOrganizer = new RootTaskDisplayAreaOrganizer(
                mMainExecutor,
                mContext,
                mShellInit);
        mTransitionHandler = new DefaultTransitionHandler(
                mContext, mShellInit, mDisplayController, mDisplayInsetsController,
                mTransactionPool, mMainExecutor, mMainHandler, mAnimExecutor,
                mRootTaskDisplayAreaOrganizer, mock(InteractionJankMonitor.class));
        mShellInit.init();
    }

    @After
    public void tearDown() {
        flushHandlers();
    }

    private void flushHandlers() {
        mMainHandler.runWithScissors(() -> {
            mAnimExecutor.flushAll();
            mMainExecutor.flushAll();
        }, 1000L);
    }

    @Test
    public void testAnimationBackgroundCreatedForTaskTransition() {
        final TransitionInfo.Change openTask = new ChangeBuilder(createTaskInfo(1), TRANSIT_OPEN)
                .build();
        final TransitionInfo.Change closeTask =
                new ChangeBuilder(createTaskInfo(2), TRANSIT_TO_BACK).build();

        final IBinder token = new Binder();
        final TransitionInfo info = new TransitionInfoBuilder(TRANSIT_OPEN)
                .addChange(openTask)
                .addChange(closeTask)
                .build();
        final SurfaceControl.Transaction startT = MockTransactionPool.create();
        final SurfaceControl.Transaction finishT = MockTransactionPool.create();

        mTransitionHandler.startAnimation(token, info, startT, finishT,
                mock(Transitions.TransitionFinishCallback.class));

        mergeSync(mTransitionHandler, token);
        flushHandlers();

        verify(startT).setColor(any(), any());
    }

    @Test
    public void testNoAnimationBackgroundForSingleChangeTransition() {
        // This test verifies that a background color layer is not created when there is only
        // a single change in the transition, even if it's an opaque task. This prevents the
        // background from incorrectly occluding other UI elements.
        final TransitionInfo.Change closeTask = new ChangeBuilder(createTaskInfo(1),
                TRANSIT_TO_BACK).build();

        final IBinder token = new Binder();
        final TransitionInfo info = new TransitionInfoBuilder(TRANSIT_TO_BACK)
                .addChange(closeTask)
                .build();
        final SurfaceControl.Transaction startT = MockTransactionPool.create();
        final SurfaceControl.Transaction finishT = MockTransactionPool.create();

        mTransitionHandler.startAnimation(token, info, startT, finishT,
                mock(Transitions.TransitionFinishCallback.class));

        mergeSync(mTransitionHandler, token);
        flushHandlers();

        // Verify that no background color is set because there's only one change.
        verify(startT, never()).setColor(any(), any());
    }

    @Test
    public void testNoAnimationBackgroundForTranslucentTasks() {
        final TransitionInfo.Change openTask = new ChangeBuilder(createTaskInfo(1), TRANSIT_OPEN)
                .setFlags(FLAG_TRANSLUCENT)
                .build();
        final TransitionInfo.Change closeTask =
                new ChangeBuilder(createTaskInfo(2), TRANSIT_TO_BACK)
                        .setFlags(FLAG_TRANSLUCENT)
                        .build();

        final IBinder token = new Binder();
        final TransitionInfo info = new TransitionInfoBuilder(TRANSIT_OPEN)
                .addChange(openTask)
                .addChange(closeTask)
                .build();
        final SurfaceControl.Transaction startT = MockTransactionPool.create();
        final SurfaceControl.Transaction finishT = MockTransactionPool.create();

        mTransitionHandler.startAnimation(token, info, startT, finishT,
                mock(Transitions.TransitionFinishCallback.class));

        mergeSync(mTransitionHandler, token);
        flushHandlers();

        verify(startT, never()).setColor(any(), any());
    }

    @Test
    public void testNoAnimationBackgroundForWallpapers() {
        final TransitionInfo.Change openWallpaper = new ChangeBuilder(TRANSIT_OPEN)
                .setFlags(TransitionInfo.FLAG_IS_WALLPAPER)
                .build();
        final TransitionInfo.Change closeWallpaper = new ChangeBuilder(TRANSIT_TO_BACK)
                .setFlags(TransitionInfo.FLAG_IS_WALLPAPER)
                .build();

        final IBinder token = new Binder();
        final TransitionInfo info = new TransitionInfoBuilder(TRANSIT_OPEN)
                .addChange(openWallpaper)
                .addChange(closeWallpaper)
                .build();
        final SurfaceControl.Transaction startT = MockTransactionPool.create();
        final SurfaceControl.Transaction finishT = MockTransactionPool.create();

        mTransitionHandler.startAnimation(token, info, startT, finishT,
                mock(Transitions.TransitionFinishCallback.class));

        mergeSync(mTransitionHandler, token);
        flushHandlers();

        verify(startT, never()).setColor(any(), any());
    }

    @Test
    public void testBuildSurfaceAnimation() {
        final AlphaAnimation animation = new AlphaAnimation(0, 1);
        final long durationMs = 500;
        animation.setDuration(durationMs);
        final long[] lastCurrentPlayTime = new long[1];
        final int[] finishCount = new int[1];
        final Runnable finishCallback = () -> finishCount[0]++;
        final ValueAnimator animator = DefaultSurfaceAnimator.buildSurfaceAnimation(
                animation, finishCallback,
                mTransactionPool, mMainExecutor,
                new DefaultSurfaceAnimator.AnimationAdapter(mock(SurfaceControl.class)) {
                    @Override
                    void applyTransformation(ValueAnimator animator, long currentPlayTime) {
                        lastCurrentPlayTime[0] = currentPlayTime;
                    }
                });
        mAnimExecutor.execute(() -> {
            animator.start();
            animator.end();
        });
        flushHandlers();
        assertEquals(durationMs, lastCurrentPlayTime[0]);
        assertEquals(1f, animator.getAnimatedFraction(), 0f /* delta */);
        assertEquals(1, finishCount[0]);
    }

    @Test
    public void startAnimation_freeformOpenChange_doesntReparentTask() {
        final TransitionInfo.Change openChange = new ChangeBuilder(createTaskInfo(
                /* taskId= */ 1, /* windowingMode= */ WINDOWING_MODE_FULLSCREEN), TRANSIT_OPEN)
                .build();
        final TransitionInfo info = new TransitionInfoBuilder(TRANSIT_OPEN)
                .addChange(openChange)
                .build();
        final IBinder token = new Binder();
        final SurfaceControl.Transaction startT = MockTransactionPool.create();
        final SurfaceControl.Transaction finishT = MockTransactionPool.create();

        mTransitionHandler.startAnimation(token, info, startT, finishT,
                mock(Transitions.TransitionFinishCallback.class));

        verify(startT, never()).reparent(any(), any());
    }

    @Test
    public void startAnimation_freeformMinimizeChange_underFullscreenChange_doesntReparentTask() {
        final TransitionInfo.Change openChange = new ChangeBuilder(createTaskInfo(
                /* taskId= */ 1, /* windowingMode= */ WINDOWING_MODE_FULLSCREEN),
                TRANSIT_OPEN).build();
        final TransitionInfo.Change toBackChange = new ChangeBuilder(createTaskInfo(
                /* taskId= */ 2, /* windowingMode= */ WINDOWING_MODE_FREEFORM), TRANSIT_TO_BACK)
                .build();
        final TransitionInfo info = new TransitionInfoBuilder(TRANSIT_OPEN)
                .addChange(openChange)
                .addChange(toBackChange)
                .build();
        final IBinder token = new Binder();
        final SurfaceControl.Transaction startT = MockTransactionPool.create();
        final SurfaceControl.Transaction finishT = MockTransactionPool.create();

        mTransitionHandler.startAnimation(token, info, startT, finishT,
                mock(Transitions.TransitionFinishCallback.class));

        verify(startT, never()).reparent(any(), any());
    }

    @Test
    public void startAnimation_neverFindsErrors_animationMode() {
        final TransitionInfo.Change open = new ChangeBuilder(TRANSIT_OPEN).build();
        final TransitionInfo.Change close = new ChangeBuilder(TRANSIT_TO_BACK).build();
        final TransitionInfo info = new TransitionInfoBuilder(TRANSIT_OPEN)
                .addChange(open)
                .addChange(close)
                .build();
        final IBinder token = mock(Binder.class);

        TransitionDispatchState dispatchState = new TransitionDispatchState(token, info);

        boolean hasPlayed = mTransitionHandler
                .startAnimation(token, info, dispatchState, MockTransactionPool.create(),
                        MockTransactionPool.create(),
                        mock(Transitions.TransitionFinishCallback.class));

        Log.v(TAG, "dispatchState: \n" + dispatchState.getDebugInfo());
        assertTrue(hasPlayed);
        assertFalse(dispatchState.hasErrors(mTransitionHandler));
    }

    @Test
    public void startAnimation_neverFindsErrors_dataCollectionMode() {
        final TransitionInfo.Change open = new ChangeBuilder(TRANSIT_OPEN).build();
        final TransitionInfo.Change close = new ChangeBuilder(TRANSIT_TO_BACK).build();
        final TransitionInfo info = new TransitionInfoBuilder(TRANSIT_OPEN)
                .addChange(open)
                .addChange(close)
                .build();
        final IBinder token = mock(Binder.class);

        TransitionDispatchState dispatchState = new TransitionDispatchState(token, info);

        boolean hasPlayed = mTransitionHandler
                .startAnimation(token, null, dispatchState, MockTransactionPool.create(),
                        MockTransactionPool.create(),
                        mock(Transitions.TransitionFinishCallback.class));

        Log.v(TAG, "dispatchState: \n" + dispatchState.getDebugInfo());
        assertFalse(hasPlayed);
        assertFalse(dispatchState.hasErrors(mTransitionHandler));
    }

    @Test
    public void startAnimation_freeform_minimizeAnimation_reparentsTask() {
        final TransitionInfo.Change openChange = new ChangeBuilder(createTaskInfo(
                /* taskId= */ 1, /* windowingMode= */ WINDOWING_MODE_FREEFORM), TRANSIT_OPEN)
                .build();
        final TransitionInfo.Change toBackChange = new ChangeBuilder(createTaskInfo(
                /* taskId= */ 2, /* windowingMode= */ WINDOWING_MODE_FREEFORM), TRANSIT_TO_BACK)
                .build();
        final TransitionInfo info = new TransitionInfoBuilder(TRANSIT_OPEN)
                .addChange(openChange)
                .addChange(toBackChange)
                .build();
        final IBinder token = new Binder();
        final SurfaceControl.Transaction startT = MockTransactionPool.create();
        final SurfaceControl.Transaction finishT = MockTransactionPool.create();

        mTransitionHandler.startAnimation(token, info, startT, finishT,
                mock(Transitions.TransitionFinishCallback.class));

        verify(startT).reparent(any(), any());
    }

    @Test
    @RequiresFlagsEnabled(com.android.window.flags.Flags.FLAG_ENABLE_MERGE_ANIMATIONS)
    public void testMergeAnimation_allowedFlag_mergesAnimation() {
        final RunningTaskInfo taskInfo = createTaskInfo(1);
        final TransitionInfo.Change openTask = new ChangeBuilder(taskInfo, TRANSIT_OPEN)
                .build();
        final IBinder token = new Binder();
        final TransitionInfo info = new TransitionInfoBuilder(TRANSIT_OPEN)
                .addChange(openTask)
                .build();
        final SurfaceControl.Transaction startT = MockTransactionPool.create();
        final SurfaceControl.Transaction finishT = MockTransactionPool.create();
        final Transitions.TransitionFinishCallback finishCallback =
                mock(Transitions.TransitionFinishCallback.class);

        mTransitionHandler.startAnimation(token, info, startT, finishT, finishCallback);

        final IBinder mergeToken = new Binder();
        final TransitionInfo mergeInfo = new TransitionInfoBuilder(TRANSIT_OPEN)
                .addChange(new ChangeBuilder(taskInfo, TRANSIT_OPEN).build())
                .build();
        final Transitions.TransitionFinishCallback mergeFinishCallback =
                mock(Transitions.TransitionFinishCallback.class);

        mTransitionHandler.mergeAnimation(mergeToken, mergeInfo, MockTransactionPool.create(),
                MockTransactionPool.create(), token, mergeFinishCallback);

        verify(mergeFinishCallback).onTransitionFinished(any());
    }

    @Test
    @RequiresFlagsEnabled(com.android.window.flags.Flags.FLAG_ENABLE_MERGE_ANIMATIONS)
    public void testMergeAnimation_disallowedFlag_doesNotMerge() {
        final RunningTaskInfo taskInfo = createTaskInfo(1);
        final TransitionInfo.Change openTask = new ChangeBuilder(taskInfo, TRANSIT_OPEN)
                .build();
        final IBinder token = new Binder();
        final TransitionInfo info = new TransitionInfoBuilder(TRANSIT_OPEN)
                .addChange(openTask)
                .build();
        final SurfaceControl.Transaction startT = MockTransactionPool.create();
        final SurfaceControl.Transaction finishT = MockTransactionPool.create();
        final Transitions.TransitionFinishCallback finishCallback =
                mock(Transitions.TransitionFinishCallback.class);

        mTransitionHandler.startAnimation(token, info, startT, finishT, finishCallback);

        // Should NOT merge for specific transition flags eg. TRANSIT_FLAG_KEYGUARD_APPEARING
        final IBinder mergeToken = new Binder();
        final TransitionInfo mergeInfo = new TransitionInfoBuilder(TRANSIT_OPEN,
                TRANSIT_FLAG_KEYGUARD_APPEARING)
                .addChange(new ChangeBuilder(taskInfo, TRANSIT_OPEN).build())
                .build();
        final Transitions.TransitionFinishCallback mergeFinishCallback =
                mock(Transitions.TransitionFinishCallback.class);

        mTransitionHandler.mergeAnimation(mergeToken, mergeInfo, MockTransactionPool.create(),
                MockTransactionPool.create(), token, mergeFinishCallback);

        verify(mergeFinishCallback, never()).onTransitionFinished(any());
    }

    @Test
    @RequiresFlagsEnabled(com.android.window.flags.Flags.FLAG_CROSS_DISPLAY_TRANSITION_V2)
    public void startAnimation_crossDisplayMoveAnimation() {
        final int startDisplayId = 0;
        final int endDisplayId = 1;

        final TransitionInfo.Change change = new ChangeBuilder(
                createTaskInfo(1, WINDOWING_MODE_FULLSCREEN), TRANSIT_CHANGE).build();
        change.setDisplayId(startDisplayId, endDisplayId);
        final SurfaceControl mockSnapshot = mock(SurfaceControl.class);
        change.setSnapshot(mockSnapshot, 0);
        change.setParent(null);

        final IBinder token = new Binder();
        final TransitionInfo info = new TransitionInfoBuilder(TRANSIT_CHANGE)
                .addChange(change)
                .build();
        info.addRootLeash(startDisplayId, mock(SurfaceControl.class), 0, 0);

        final SurfaceControl.Transaction startT = MockTransactionPool.create();
        final SurfaceControl.Transaction finishT = MockTransactionPool.create();
        final Transitions.TransitionFinishCallback finishCallback =
                mock(Transitions.TransitionFinishCallback.class);

        when(mDisplayController.getRelativeTranslationDp(anyInt(), anyInt(), any(), any(),
                anyFloat())).thenReturn(new PointF(1, 0));
        when(mDisplayController.convertDpVectorToPxVector(anyInt(), any()))
                .thenReturn(new PointF(1, 0));

        mTransitionHandler.startAnimation(token, info, startT, finishT, finishCallback);
        flushHandlers();

        verify(startT).setAlpha(change.getLeash(), 0f);
        verify(startT).reparent(eq(mockSnapshot), any());
        verify(startT, times(2)).reparent(any(), any());
        verify(startT).show(mockSnapshot);
    }

    @Test
    @RequiresFlagsEnabled(com.android.window.flags.Flags.FLAG_CROSS_DISPLAY_TRANSITION_V2)
    public void startAnimation_crossDisplayMoveWithoutSnapshot() {
        final int startDisplayId = 0;
        final int endDisplayId = 1;

        final TransitionInfo.Change change = new ChangeBuilder(
                createTaskInfo(1, WINDOWING_MODE_FULLSCREEN), TRANSIT_CHANGE).build();
        change.setDisplayId(startDisplayId, endDisplayId);
        change.setParent(null);

        final IBinder token = new Binder();
        final TransitionInfo info = new TransitionInfoBuilder(TRANSIT_CHANGE)
                .addChange(change)
                .build();
        info.addRootLeash(startDisplayId, mock(SurfaceControl.class), 0, 0);

        final SurfaceControl.Transaction startT = MockTransactionPool.create();
        final SurfaceControl.Transaction finishT = MockTransactionPool.create();

        when(mDisplayController.getRelativeTranslationDp(anyInt(), anyInt(), any(), any(),
                anyFloat())).thenReturn(new PointF(1, 0));
        when(mDisplayController.convertDpVectorToPxVector(anyInt(), any()))
                .thenReturn(new PointF(1, 0));

        mTransitionHandler.startAnimation(token, info, startT, finishT,
                mock(Transitions.TransitionFinishCallback.class));
        flushHandlers();

        verify(startT).setAlpha(change.getLeash(), 0f);
        verify(startT, atMostOnce()).reparent(any(), any());
        verify(startT, never()).show(any());
    }

    @Test
    @RequiresFlagsEnabled(com.android.window.flags.Flags.FLAG_CROSS_DISPLAY_TRANSITION_V2)
    public void startAnimation_childTask() {
        // 1. Cross-display move
        final int startDisplayId = 0;
        final int endDisplayId = 1;

        final RunningTaskInfo parentTaskInfo = createTaskInfo(1, WINDOWING_MODE_FREEFORM);
        final TransitionInfo.Change parentChange = new ChangeBuilder(parentTaskInfo, TRANSIT_CHANGE)
                .build();

        final RunningTaskInfo childTaskInfo = createTaskInfo(2, WINDOWING_MODE_FREEFORM);
        childTaskInfo.positionInParent = new Point(100, 100);
        final TransitionInfo.Change childChange = new ChangeBuilder(childTaskInfo, TRANSIT_CHANGE)
                .build();
        childChange.setDisplayId(startDisplayId, endDisplayId);
        childChange.setParent(parentChange.getContainer());
        final SurfaceControl mockSnapshot = mock(SurfaceControl.class);
        childChange.setSnapshot(mockSnapshot, 0);

        final IBinder token = new Binder();
        final TransitionInfo info = new TransitionInfoBuilder(TRANSIT_CHANGE)
                .addChange(parentChange)
                .addChange(childChange)
                .build();
        info.addRootLeash(startDisplayId, mock(SurfaceControl.class), 0, 0);

        final SurfaceControl.Transaction startT = MockTransactionPool.create();
        final SurfaceControl.Transaction finishT = MockTransactionPool.create();

        mTransitionHandler.startAnimation(token, info, startT, finishT,
                mock(Transitions.TransitionFinishCallback.class));
        flushHandlers();

        // Should follow display move path: set alpha to 0 and reparent snapshot
        verify(startT).setAlpha(eq(childChange.getLeash()), eq(0f));
        verify(startT).reparent(eq(mockSnapshot), any());

        // 2. Non-cross-display move
        final int sameDisplayId = 0;
        final RunningTaskInfo childTaskInfo2 = createTaskInfo(3, WINDOWING_MODE_FREEFORM);
        final Point positionInParent = new Point(200, 200);
        childTaskInfo2.positionInParent = positionInParent;
        final TransitionInfo.Change childChange2 = new ChangeBuilder(childTaskInfo2, TRANSIT_CHANGE)
                .build();
        childChange2.setDisplayId(sameDisplayId, sameDisplayId);
        childChange2.setParent(parentChange.getContainer());

        final IBinder token2 = new Binder();
        final TransitionInfo info2 = new TransitionInfoBuilder(TRANSIT_CHANGE)
                .addChange(parentChange)
                .addChange(childChange2)
                .build();

        final SurfaceControl.Transaction startT2 = MockTransactionPool.create();
        mTransitionHandler.startAnimation(token2, info2, startT2, MockTransactionPool.create(),
                mock(Transitions.TransitionFinishCallback.class));
        flushHandlers();

        // Should NOT follow display move path
        verify(startT2, never()).setAlpha(eq(childChange2.getLeash()), anyFloat());
        verify(startT2, never()).reparent(any(), any());
    }

    private static void mergeSync(Transitions.TransitionHandler handler, IBinder token) {
        handler.mergeAnimation(
                new Binder(),
                new TransitionInfoBuilder(TRANSIT_SLEEP, FLAG_SYNC).build(),
                MockTransactionPool.create(),
                MockTransactionPool.create(),
                token,
                mock(Transitions.TransitionFinishCallback.class));
    }

    private static RunningTaskInfo createTaskInfo(int taskId) {
        return createTaskInfo(taskId, WINDOWING_MODE_FULLSCREEN);
    }

    private static RunningTaskInfo createTaskInfo(int taskId, int windowingMode) {
        RunningTaskInfo taskInfo = new RunningTaskInfo();
        taskInfo.taskId = taskId;
        taskInfo.topActivityType = ACTIVITY_TYPE_STANDARD;
        taskInfo.configuration.windowConfiguration.setWindowingMode(windowingMode);
        taskInfo.configuration.windowConfiguration.setActivityType(taskInfo.topActivityType);
        taskInfo.token = mock(WindowContainerToken.class);
        return taskInfo;
    }
}
