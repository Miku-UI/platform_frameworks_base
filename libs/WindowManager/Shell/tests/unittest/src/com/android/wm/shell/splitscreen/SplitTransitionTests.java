/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.wm.shell.splitscreen;

import static android.app.WindowConfiguration.ACTIVITY_TYPE_HOME;
import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;
import static android.view.Display.DEFAULT_DISPLAY;
import static android.view.WindowManager.TRANSIT_CHANGE;
import static android.view.WindowManager.TRANSIT_CLOSE;
import static android.view.WindowManager.TRANSIT_OPEN;
import static android.view.WindowManager.TRANSIT_TO_BACK;
import static android.view.WindowManager.TRANSIT_TO_FRONT;
import static android.window.WindowContainerTransaction.HierarchyOp.HIERARCHY_OP_TYPE_CHILDREN_TASKS_REPARENT;
import static android.window.WindowContainerTransaction.HierarchyOp.HIERARCHY_OP_TYPE_REORDER;
import static android.window.WindowContainerTransaction.HierarchyOp.HIERARCHY_OP_TYPE_SET_ANIMATION_DELEGATE;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.spyOn;
import static com.android.wm.shell.common.split.SplitScreenUtils.getNewParentTokenForStage;
import static com.android.wm.shell.shared.split.SplitScreenConstants.SNAP_TO_2_10_90;
import static com.android.wm.shell.shared.split.SplitScreenConstants.SNAP_TO_2_50_50;
import static com.android.wm.shell.shared.split.SplitScreenConstants.SNAP_TO_2_90_10;
import static com.android.wm.shell.splitscreen.SplitScreen.STAGE_TYPE_MAIN;
import static com.android.wm.shell.splitscreen.SplitScreen.STAGE_TYPE_SIDE;
import static com.android.wm.shell.splitscreen.SplitScreenController.EXIT_REASON_APP_DOES_NOT_SUPPORT_MULTIWINDOW;
import static com.android.wm.shell.splitscreen.SplitScreenController.EXIT_REASON_APP_FINISHED;
import static com.android.wm.shell.splitscreen.SplitScreenController.EXIT_REASON_DRAG_DIVIDER;
import static com.android.wm.shell.splitscreen.SplitTestUtils.createMockSurface;
import static com.android.wm.shell.transition.Transitions.TRANSIT_SPLIT_SCREEN_PAIR_OPEN;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.animation.Animator;
import android.annotation.NonNull;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningTaskInfo;
import android.app.IActivityTaskManager;
import android.app.IApplicationThread;
import android.graphics.Rect;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.platform.test.annotations.EnableFlags;
import android.view.SurfaceControl;
import android.window.DisplayAreaInfo;
import android.window.IRemoteTransition;
import android.window.IRemoteTransitionFinishedCallback;
import android.window.RemoteTransition;
import android.window.RemoteTransitionStub;
import android.window.TransitionInfo;
import android.window.TransitionRequestInfo;
import android.window.WindowContainerToken;
import android.window.WindowContainerTransaction;
import android.window.WindowContainerTransaction.HierarchyOp;

import androidx.test.annotation.UiThreadTest;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.launcher3.icons.IconProvider;
import com.android.testing.wm.util.ChangeBuilder;
import com.android.testing.wm.util.MockToken;
import com.android.testing.wm.util.TransitionInfoBuilder;
import com.android.wm.shell.Flags;
import com.android.wm.shell.RootDisplayAreaOrganizer;
import com.android.wm.shell.RootTaskDisplayAreaOrganizer;
import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.ShellTestCase;
import com.android.wm.shell.TestRunningTaskInfoBuilder;
import com.android.wm.shell.TestShellExecutor;
import com.android.wm.shell.bubbles.BubbleController;
import com.android.wm.shell.common.DisplayController;
import com.android.wm.shell.common.DisplayImeController;
import com.android.wm.shell.common.DisplayInsetsController;
import com.android.wm.shell.common.LaunchAdjacentController;
import com.android.wm.shell.common.ShellExecutor;
import com.android.wm.shell.common.SyncTransactionQueue;
import com.android.wm.shell.common.split.SplitDecorManager;
import com.android.wm.shell.common.split.SplitLayout;
import com.android.wm.shell.common.split.SplitState;
import com.android.wm.shell.shared.TransactionPool;
import com.android.wm.shell.shared.desktopmode.FakeDesktopState;
import com.android.wm.shell.shared.split.SplitScreenConstants;
import com.android.wm.shell.transition.DefaultMixedHandler;
import com.android.wm.shell.transition.TestRemoteTransition;
import com.android.wm.shell.transition.Transitions;
import com.android.wm.shell.windowdecor.WindowDecorViewModel;

import com.google.android.msdl.domain.MSDLPlayer;

import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.Optional;
import java.util.function.Consumer;

/** Tests for {@link StageCoordinator} */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class SplitTransitionTests extends ShellTestCase {
    private static final int LAUNCHER_TASK_ID = 2;
    private static final int MAIN_TASK_ID = 3;
    private static final int SIDE_TASK_ID = 11;

    @Mock private ShellTaskOrganizer mTaskOrganizer;
    @Mock private SyncTransactionQueue mSyncQueue;
    @Mock private RootTaskDisplayAreaOrganizer mRootTDAOrganizer;
    @Mock private RootDisplayAreaOrganizer mRootDisplayAreaOrganizer;
    @Mock private DisplayController mDisplayController;
    @Mock private DisplayImeController mDisplayImeController;
    @Mock private DisplayInsetsController mDisplayInsetsController;
    @Mock private TransactionPool mTransactionPool;
    @Mock private Transitions mTransitions;
    @Mock private IconProvider mIconProvider;
    @Mock private WindowDecorViewModel mWindowDecorViewModel;
    @Mock private SplitState mSplitState;
    @Mock private ShellExecutor mMainExecutor;
    @Mock private Handler mMainHandler;
    @Mock private LaunchAdjacentController mLaunchAdjacentController;
    @Mock private DefaultMixedHandler mMixedHandler;
    @Mock private SplitScreen.SplitInvocationListener mInvocationListener;
    private FakeDesktopState mDesktopState;
    @Mock private IActivityTaskManager mActivityTaskManager;
    @Mock private MSDLPlayer mMSDLPlayer;
    @Mock private BubbleController mBubbleController;
    private final TestShellExecutor mTestShellExecutor = new TestShellExecutor();
    private SplitLayout mSplitLayout;
    private StageTaskListener mMainStage;
    private StageTaskListener mSideStage;
    private StageCoordinator mStageCoordinator;
    private SplitScreenTransitions mSplitScreenTransitions;
    private SplitTransitionAnimations mSplitTransitionAnimations;
    @Mock private Animator mSplitDismissAnimator;
    private final DisplayAreaInfo mDisplayAreaInfo =
            new DisplayAreaInfo(new MockToken("DisplayAreaInfo").token(), DEFAULT_DISPLAY, 0);
    private final DisplayAreaInfo mSecondaryDisplayAreaInfo =
            new DisplayAreaInfo(new MockToken("SecondaryDisplayAreaInfo").token(), 5, 0);

    private WindowContainerTransaction mLastStartedTransitionWCT = null;

    private RunningTaskInfo mMainChild;
    private RunningTaskInfo mSideChild;
    private RunningTaskInfo mHomeTask;

    @Before
    @UiThreadTest
    public void setup() {
        MockitoAnnotations.initMocks(this);
        mDesktopState = new FakeDesktopState();
        doReturn(mMainExecutor).when(mTransitions).getMainExecutor();
        doReturn(mock(SurfaceControl.Transaction.class)).when(mTransactionPool).acquire();
        doReturn(mock(WindowContainerToken.class))
                .when(mRootDisplayAreaOrganizer).getDisplayTokenForDisplay(anyInt());
        mSplitLayout = SplitTestUtils.createMockSplitLayout();
        mMainStage = spy(new StageTaskListener(mContext, mTaskOrganizer, DEFAULT_DISPLAY, mock(
                StageTaskListener.StageListenerCallbacks.class), mSyncQueue,
                mIconProvider, Optional.of(mWindowDecorViewModel), STAGE_TYPE_MAIN,
                Optional.of(mBubbleController)));
        mMainStage.onTaskAppeared(
                new TestRunningTaskInfoBuilder("MainStage").build(),
                createMockSurface());
        mSideStage = spy(new StageTaskListener(mContext, mTaskOrganizer, DEFAULT_DISPLAY, mock(
                StageTaskListener.StageListenerCallbacks.class), mSyncQueue,
                mIconProvider, Optional.of(mWindowDecorViewModel), STAGE_TYPE_SIDE,
                Optional.of(mBubbleController)));
        mSideStage.onTaskAppeared(
                new TestRunningTaskInfoBuilder("SideStage").build(),
                createMockSurface());
        mStageCoordinator = new SplitTestUtils.TestStageCoordinator(mContext, DEFAULT_DISPLAY,
                mSyncQueue, mTaskOrganizer, mMainStage, mSideStage, mDisplayController,
                mDisplayImeController, mDisplayInsetsController, mSplitLayout, mTransitions,
                mTransactionPool, mMainExecutor, mMainHandler, Optional.empty(),
                mLaunchAdjacentController, Optional.empty(), mSplitState, Optional.empty(),
                Optional.empty(),
                mRootTDAOrganizer, mRootDisplayAreaOrganizer, mDesktopState, mActivityTaskManager,
                mMSDLPlayer);
        when(mRootTDAOrganizer.getDisplayAreaInfo(DEFAULT_DISPLAY)).thenReturn(mDisplayAreaInfo);

        mStageCoordinator.setMixedHandler(mMixedHandler);
        mSplitScreenTransitions = mStageCoordinator.getSplitTransitions();
        mSplitTransitionAnimations = mSplitScreenTransitions.getSplitTransitionAnimations();
        spyOn(mSplitTransitionAnimations);
        doAnswer(new Answer<Animator>() {
            @Override
            public Animator answer(InvocationOnMock invocation) throws Throwable {
                final Animator result = (Animator) invocation.callRealMethod();
                return result != null ? mSplitDismissAnimator : null;
            }
        }).when(mSplitTransitionAnimations).buildDismissAnimation(any(), any(), any(), any(),
                anyFloat(), any());
        doAnswer((Answer<IBinder>) invocation -> {
            mLastStartedTransitionWCT = invocation.getArgument(1);
            return mock(IBinder.class);
        }).when(mTransitions).startTransition(anyInt(), any(), any());

        mMainChild = new TestRunningTaskInfoBuilder("MainChild")
                .setTaskId(MAIN_TASK_ID)
                .setVisible(true)
                .setVisibleRequested(true)
                .setParentTaskId(mMainStage.mRootTaskInfo.taskId).build();
        mSideChild = new TestRunningTaskInfoBuilder("SideChild")
                .setTaskId(SIDE_TASK_ID)
                .setVisible(true)
                .setVisibleRequested(true)
                .setParentTaskId(mSideStage.mRootTaskInfo.taskId).build();
        mHomeTask = new TestRunningTaskInfoBuilder("HomeTask")
                .setTaskId(LAUNCHER_TASK_ID)
                .setWindowingMode(WINDOWING_MODE_FULLSCREEN)
                .setActivityType(ACTIVITY_TYPE_HOME)
                .build();

        doReturn(mock(SplitDecorManager.class)).when(mMainStage).getSplitDecorManager();
        doReturn(mock(SplitDecorManager.class)).when(mSideStage).getSplitDecorManager();
        mStageCoordinator.registerSplitAnimationListener(mInvocationListener, mTestShellExecutor);
    }

    @Test
    @UiThreadTest
    public void testLaunchToSide() {
        ActivityManager.RunningTaskInfo newTask = new TestRunningTaskInfoBuilder()
                .setParentTaskId(mSideStage.mRootTaskInfo.taskId).build();
        ActivityManager.RunningTaskInfo reparentTask = new TestRunningTaskInfoBuilder()
                .setParentTaskId(mMainStage.mRootTaskInfo.taskId).build();

        // Create a request to start a new task in side stage
        TransitionRequestInfo request = new TransitionRequestInfo(TRANSIT_TO_FRONT, newTask, null);
        IBinder transition = mock(IBinder.class);
        WindowContainerTransaction result =
                mStageCoordinator.handleRequest(transition, request);

        // it should handle the transition to enter split screen.
        assertNotNull(result);
        assertTrue(containsSplitEnter(result));

        // simulate the transition
        final TransitionInfo info = new TransitionInfoBuilder(TRANSIT_TO_FRONT, 0)
                .addChange(TRANSIT_OPEN, newTask)
                .addChange(TRANSIT_CHANGE, reparentTask)
                .build();
        mSideStage.onTaskAppeared(newTask, createMockSurface());
        mMainStage.onTaskAppeared(reparentTask, createMockSurface());
        boolean accepted = mStageCoordinator.startAnimation(transition, info,
                mock(SurfaceControl.Transaction.class),
                mock(SurfaceControl.Transaction.class),
                mock(Transitions.TransitionFinishCallback.class));
        assertTrue(accepted);
        assertTrue(mStageCoordinator.isSplitScreenVisible());
    }

    @Test
    @UiThreadTest
    public void testLaunchPair() {
        TransitionInfo info = createEnterPairInfo();

        TestRemoteTransition testRemote = new TestRemoteTransition();

        IBinder transition = mSplitScreenTransitions.startEnterTransition(
                TRANSIT_OPEN, new WindowContainerTransaction(),
                new RemoteTransition(testRemote, "Test"), mStageCoordinator,
                TRANSIT_SPLIT_SCREEN_PAIR_OPEN, false, SNAP_TO_2_50_50);
        mMainStage.onTaskAppeared(mMainChild, createMockSurface());
        mSideStage.onTaskAppeared(mSideChild, createMockSurface());
        boolean accepted = mStageCoordinator.startAnimation(transition, info,
                mock(SurfaceControl.Transaction.class),
                mock(SurfaceControl.Transaction.class),
                mock(Transitions.TransitionFinishCallback.class));
        assertTrue(accepted);

        // Make sure split-screen is now visible
        assertTrue(mStageCoordinator.isSplitScreenVisible());
        assertTrue(testRemote.isCalled());
    }

    @Test
    @UiThreadTest
    public void testRemoteDelegate() {
        TestRemoteTransition testRemote = new TestRemoteTransition();
        IApplicationThread stubThread = mock(IApplicationThread.class);

        mSplitScreenTransitions.startEnterTransition(
                TRANSIT_OPEN, new WindowContainerTransaction(),
                new RemoteTransition(testRemote, stubThread, "Test"), mStageCoordinator,
                TRANSIT_SPLIT_SCREEN_PAIR_OPEN, false, SNAP_TO_2_50_50);
        assertTrue(mLastStartedTransitionWCT.getHierarchyOps().stream().anyMatch(
                hop -> hop.getType() == HIERARCHY_OP_TYPE_SET_ANIMATION_DELEGATE));
    }

    @Test
    @UiThreadTest
    public void testRemoteTransitionConsumedForStartAnimation() {
        // Omit side child change
        TransitionInfo info = new TransitionInfoBuilder(TRANSIT_OPEN, 0)
                .addChange(TRANSIT_OPEN, mMainChild)
                .build();
        TestRemoteTransition testRemote = new TestRemoteTransition();

        IBinder transition = mSplitScreenTransitions.startEnterTransition(
                TRANSIT_OPEN, new WindowContainerTransaction(),
                new RemoteTransition(testRemote, "Test"), mStageCoordinator,
                TRANSIT_SPLIT_SCREEN_PAIR_OPEN, false, SNAP_TO_2_50_50);
        mMainStage.onTaskAppeared(mMainChild, createMockSurface());
        boolean accepted = mStageCoordinator.startAnimation(transition, info,
                mock(SurfaceControl.Transaction.class),
                mock(SurfaceControl.Transaction.class),
                mock(Transitions.TransitionFinishCallback.class));
        assertTrue(accepted);

        assertTrue(testRemote.isConsumed());
    }

    @Test
    @UiThreadTest
    public void testRemoteTransitionConsumed() {
        // Omit side child change
        TransitionInfo info = new TransitionInfoBuilder(TRANSIT_OPEN, 0)
                .addChange(TRANSIT_OPEN, mMainChild)
                .build();
        TestRemoteTransition testRemote = new TestRemoteTransition();

        IBinder transition = mSplitScreenTransitions.startEnterTransition(
                TRANSIT_OPEN, new WindowContainerTransaction(),
                new RemoteTransition(testRemote, "Test"), mStageCoordinator,
                TRANSIT_SPLIT_SCREEN_PAIR_OPEN, false, SNAP_TO_2_50_50);
        mMainStage.onTaskAppeared(mMainChild, createMockSurface());
        mStageCoordinator.startAnimation(transition, info,
                mock(SurfaceControl.Transaction.class),
                mock(SurfaceControl.Transaction.class),
                mock(Transitions.TransitionFinishCallback.class));
        mStageCoordinator.onTransitionConsumed(transition, false /*aborted*/,
                mock(SurfaceControl.Transaction.class));

        assertTrue(testRemote.isConsumed());
    }

    @Test
    @UiThreadTest
    public void testRemoteTransitionFails() {
        TransitionInfo info = createEnterPairInfo();

        RemoteTransitionStub testRemote = new RemoteTransitionStub() {

            @Override
            public void startAnimation(IBinder token, TransitionInfo info,
                    SurfaceControl.Transaction t,
                    IRemoteTransitionFinishedCallback finishCallback) throws RemoteException {
                throw new RemoteException("startAnimation is not implemented!");
            }
        };

        IBinder transition = mSplitScreenTransitions.startEnterTransition(
                TRANSIT_OPEN, new WindowContainerTransaction(),
                new RemoteTransition(testRemote, "Test"), mStageCoordinator,
                TRANSIT_SPLIT_SCREEN_PAIR_OPEN, false, SNAP_TO_2_50_50);
        Transitions.TransitionFinishCallback testFinish =
                mock(Transitions.TransitionFinishCallback.class);
        mMainStage.onTaskAppeared(mMainChild, createMockSurface());
        mSideStage.onTaskAppeared(mSideChild, createMockSurface());
        mStageCoordinator.startAnimation(transition, info,
                mock(SurfaceControl.Transaction.class),
                mock(SurfaceControl.Transaction.class),
                testFinish);
        verify(testFinish).onTransitionFinished(isNull());

        assertFalse(mSplitScreenTransitions.hasActiveRemoteHandler());
    }

    @Test
    @UiThreadTest
    public void testMonitorInSplit() {
        enterSplit();

        ActivityManager.RunningTaskInfo newTask = new TestRunningTaskInfoBuilder()
                .setParentTaskId(mSideStage.mRootTaskInfo.taskId).build();

        // Create a request to start a new task in side stage
        TransitionRequestInfo request = new TransitionRequestInfo(TRANSIT_TO_FRONT, newTask, null);
        IBinder transition = mock(IBinder.class);
        WindowContainerTransaction result =
                mStageCoordinator.handleRequest(transition, request);

        // while in split, it should handle everything:
        assertNotNull(result);

        // Not exiting, just opening up another side-stage task.
        assertFalse(containsSplitExit(result));

        // simulate the transition
        TransitionInfo info = new TransitionInfoBuilder(TRANSIT_TO_FRONT, 0)
                .addChange(TRANSIT_TO_FRONT, newTask)
                .addChange(TRANSIT_TO_BACK, mSideChild)
                .build();
        mSideStage.onTaskAppeared(newTask, createMockSurface());
        boolean accepted = mStageCoordinator.startAnimation(transition, info,
                mock(SurfaceControl.Transaction.class),
                mock(SurfaceControl.Transaction.class),
                mock(Transitions.TransitionFinishCallback.class));
        assertFalse(accepted);
        assertTrue(mStageCoordinator.isSplitScreenVisible());

        // same, but create request to close the new task
        request = new TransitionRequestInfo(TRANSIT_CLOSE, newTask, null);
        transition = mock(IBinder.class);
        result = mStageCoordinator.handleRequest(transition, request);
        assertNotNull(result);
        assertFalse(containsSplitExit(result));

        info = new TransitionInfoBuilder(TRANSIT_CLOSE, 0)
                .addChange(TRANSIT_TO_FRONT, mSideChild)
                .addChange(TRANSIT_CLOSE, newTask)
                .build();
        mSideStage.onTaskVanished(newTask);
        accepted = mStageCoordinator.startAnimation(transition, info,
                mock(SurfaceControl.Transaction.class),
                mock(SurfaceControl.Transaction.class),
                mock(Transitions.TransitionFinishCallback.class));
        assertFalse(accepted);
        assertTrue(mStageCoordinator.isSplitScreenVisible());
    }

    @Test
    @UiThreadTest
    public void testEnterRecentsAndCommit() {
        enterSplit();

        // Create a request to bring home forward
        TransitionRequestInfo request = new TransitionRequestInfo(TRANSIT_TO_FRONT, mHomeTask,
                mock(TransitionRequestInfo.RemoteTransitionInfo.class));
        IBinder transition = mock(IBinder.class);
        WindowContainerTransaction result = mStageCoordinator.handleRequest(transition, request);
        // Don't handle recents opening
        assertNull(result);

        // make sure we haven't made any local changes yet (need to wait until transition is ready)
        assertTrue(mStageCoordinator.isSplitScreenVisible());

        // simulate the start of recents transition
        mMainStage.onTaskVanished(mMainChild);
        mSideStage.onTaskVanished(mSideChild);
        mStageCoordinator.onRecentsInSplitAnimationStart(mock(TransitionInfo.class));
        assertTrue(mStageCoordinator.isSplitScreenVisible());

        // Make sure it cleans-up if recents doesn't restore
        WindowContainerTransaction commitWCT = new WindowContainerTransaction();
        mStageCoordinator.onRecentsInSplitAnimationFinishing(false /* returnToApp */, commitWCT,
                mock(SurfaceControl.Transaction.class));
        assertFalse(mStageCoordinator.isSplitScreenVisible());
    }

    @Test
    @UiThreadTest
    public void testRemotePassThroughInvoked() throws RemoteException {
        TransitionRequestInfo.RemoteTransitionInfo remoteWrapper =
                mock(TransitionRequestInfo.RemoteTransitionInfo.class);
        IRemoteTransition remoteTransition = mock(IRemoteTransition.class);
        IBinder remoteBinder = mock(IBinder.class);
        doReturn(remoteBinder).when(remoteTransition).asBinder();
        doReturn(remoteTransition).when(remoteWrapper).getRemoteTransition();

        TransitionRequestInfo request = new TransitionRequestInfo(TRANSIT_CHANGE, null,
                remoteWrapper);
        IBinder transition = mock(IBinder.class);
        mMainStage.activate(new WindowContainerTransaction(), false);
        mStageCoordinator.handleRequest(transition, request);
        TransitionInfo info = new TransitionInfoBuilder(TRANSIT_CHANGE, 0)
                .build();
        boolean accepted = mStageCoordinator.startAnimation(transition, info,
                mock(SurfaceControl.Transaction.class),
                mock(SurfaceControl.Transaction.class),
                mock(Transitions.TransitionFinishCallback.class));
        assertTrue(accepted);

        verify(remoteTransition, times(1)).startAnimation(any(),
                any(), any(), any());
    }

    @Test
    @UiThreadTest
    public void testEnterRecentsAndRestore() {
        enterSplit();

        // Create a request to bring home forward
        TransitionRequestInfo request = new TransitionRequestInfo(TRANSIT_TO_FRONT, mHomeTask,
                mock(TransitionRequestInfo.RemoteTransitionInfo.class));
        IBinder transition = mock(IBinder.class);
        WindowContainerTransaction result = mStageCoordinator.handleRequest(transition, request);
        // Don't handle recents opening
        assertNull(result);

        // make sure we haven't made any local changes yet (need to wait until transition is ready)
        assertTrue(mStageCoordinator.isSplitScreenVisible());

        // simulate the start of recents transition
        mMainStage.onTaskVanished(mMainChild);
        mSideStage.onTaskVanished(mSideChild);
        mStageCoordinator.onRecentsInSplitAnimationStart(mock(TransitionInfo.class));
        assertTrue(mStageCoordinator.isSplitScreenVisible());

        // Make sure we remain in split after recents restores.
        WindowContainerTransaction restoreWCT = new WindowContainerTransaction();
        restoreWCT.reorder(mMainChild.token, true /* toTop */);
        restoreWCT.reorder(mSideChild.token, true /* toTop */);
        // simulate the restoreWCT being applied:
        mMainStage.onTaskAppeared(mMainChild, mock(SurfaceControl.class));
        mSideStage.onTaskAppeared(mSideChild, mock(SurfaceControl.class));
        mStageCoordinator.onRecentsInSplitAnimationFinishing(true /* returnToApp */, restoreWCT,
                mock(SurfaceControl.Transaction.class));
        assertTrue(mStageCoordinator.isSplitScreenVisible());
    }

    @Test
    @UiThreadTest
    public void testDismissFromMultiWindowSupport() {
        enterSplit();

        // simulate the transition
        TransitionInfo info = new TransitionInfoBuilder(TRANSIT_TO_BACK, 0)
                .addChange(TRANSIT_TO_BACK, mMainChild)
                .addChange(TRANSIT_TO_BACK, mSideChild)
                .build();
        IBinder transition = mSplitScreenTransitions.startDismissTransition(
                new WindowContainerTransaction(), mStageCoordinator,
                STAGE_TYPE_SIDE, EXIT_REASON_APP_DOES_NOT_SUPPORT_MULTIWINDOW);
        mMainStage.onTaskVanished(mMainChild);
        mSideStage.onTaskVanished(mSideChild);
        boolean accepted = mStageCoordinator.startAnimation(transition, info,
                mock(SurfaceControl.Transaction.class),
                mock(SurfaceControl.Transaction.class),
                mock(Transitions.TransitionFinishCallback.class));
        assertTrue(accepted);
        assertFalse(mStageCoordinator.isSplitScreenVisible());
    }

    @Test
    @UiThreadTest
    public void testDismissSnap() {
        enterSplit();

        // simulate the transition
        TransitionInfo info = new TransitionInfoBuilder(TRANSIT_TO_BACK, 0)
                .addChange(TRANSIT_TO_BACK, mMainChild)
                .addChange(TRANSIT_CHANGE, mSideChild)
                .build();
        IBinder transition = mSplitScreenTransitions.startDismissTransition(
                new WindowContainerTransaction(), mStageCoordinator, STAGE_TYPE_SIDE,
                EXIT_REASON_DRAG_DIVIDER);
        mMainStage.onTaskVanished(mMainChild);
        mSideStage.onTaskVanished(mSideChild);
        boolean accepted = mStageCoordinator.startAnimation(transition, info,
                mock(SurfaceControl.Transaction.class),
                mock(SurfaceControl.Transaction.class),
                mock(Transitions.TransitionFinishCallback.class));
        assertTrue(accepted);
        assertFalse(mStageCoordinator.isSplitScreenVisible());
    }

    @Test
    @UiThreadTest
    public void testDismissFromAppFinish() {
        enterSplit();

        // Create a request to exit the "last" task on side stage
        TransitionRequestInfo request = new TransitionRequestInfo(TRANSIT_CLOSE, mSideChild, null);
        IBinder transition = mock(IBinder.class);
        WindowContainerTransaction result = mStageCoordinator.handleRequest(transition, request);

        assertTrue(containsSplitExit(result));

        // make sure we haven't made any local changes yet (need to wait until transition is ready)
        assertTrue(mStageCoordinator.isSplitScreenVisible());

        // simulate the transition
        TransitionInfo info = new TransitionInfoBuilder(TRANSIT_CLOSE, 0)
                .addChange(TRANSIT_CHANGE, mMainChild)
                .addChange(TRANSIT_CLOSE, mSideChild)
                .build();
        mMainStage.onTaskVanished(mMainChild);
        mSideStage.onTaskVanished(mSideChild);
        boolean accepted = mStageCoordinator.startAnimation(transition, info,
                mock(SurfaceControl.Transaction.class),
                mock(SurfaceControl.Transaction.class),
                mock(Transitions.TransitionFinishCallback.class));
        assertTrue(accepted);
        assertFalse(mStageCoordinator.isSplitScreenVisible());
    }

    @Test
    @UiThreadTest
    public void testDismissFromDisplayMove() {
        enterSplit();
        mSplitScreenTransitions.mPendingEnter.mFinishedCallback.onFinished(
                new WindowContainerTransaction(), new SurfaceControl.Transaction());
        org.mockito.Mockito.clearInvocations(mSyncQueue);
        org.mockito.Mockito.clearInvocations(mTransitions);

        final var sideStageInfo = mSideStage.getRunningTaskInfo();
        final var mainStageInfo = mMainStage.getRunningTaskInfo();

        // Create a request to relaunch the main task in fullscreen on another display.
        final var movedMainChild = new TestRunningTaskInfoBuilder("MainChild(moved)")
                .setToken(mMainChild.getToken())
                .setTaskId(MAIN_TASK_ID)
                .setVisible(true)
                .setVisibleRequested(true)
                .setDisplayId(mSecondaryDisplayAreaInfo.displayId)
                .build();

        // Create a request to relaunch the main task in fullscreen on another display.
        IBinder transition = mock(IBinder.class);
        TransitionRequestInfo request = new TransitionRequestInfo(TRANSIT_OPEN, mMainChild, null);

        // There is not enough information yet to know to exit split.
        WindowContainerTransaction result = mStageCoordinator.handleRequest(transition, request);
        assertFalse(containsSplitExit(result));

        // The full transition contains the change of displayId for mMainChild, and can also
        // include the launcher becoming visible underneath.
        TransitionInfo info = new TransitionInfoBuilder(TRANSIT_OPEN, 0)
                .addChange(TRANSIT_TO_FRONT, TransitionInfo.FLAG_SHOW_WALLPAPER, mHomeTask)
                .addChange(TRANSIT_TO_FRONT, TransitionInfo.FLAG_IS_WALLPAPER)
                .addChange(new ChangeBuilder(movedMainChild, TRANSIT_CHANGE)
                        .moveToDisplay(DEFAULT_DISPLAY, mSecondaryDisplayAreaInfo.displayId)
                        .build())
                .build();

        // Simulate the transition. Split does not need to play it, but does need to know about it.
        assertFalse("Display-move transition should not be played by StageCoordinator",
                mStageCoordinator.startAnimation(transition, info,
                        mock(SurfaceControl.Transaction.class),
                        mock(SurfaceControl.Transaction.class),
                        mock(Transitions.TransitionFinishCallback.class)));

        // The main task should be allowed to continue going fullscreen on the other display,
        // and we should explit from split to fullscreen.
        assertThat(mLastStartedTransitionWCT.getHierarchyOps(), allOf(
                not(hasItem(wctContaining(mMainChild))),
                hasItems(
                        isChildrenTasksReparent(sideStageInfo, mDisplayAreaInfo, true),
                        isChildrenTasksReparent(mainStageInfo, mDisplayAreaInfo, false))));
    }

    @Test
    public void testRequestingFocusForDefaultLaunch() throws RemoteException {
        enterSplit();
        verify(mActivityTaskManager, times(0)).setFocusedTask(anyInt());
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_FLEXIBLE_TWO_APP_SPLIT)
    public void testRequestingFocusFor9010Launch() throws RemoteException {
        enterSplit(SNAP_TO_2_90_10);
        mSplitScreenTransitions.mPendingEnter.mFinishedCallback.onFinished(
                new WindowContainerTransaction(), new SurfaceControl.Transaction());
        // We're assuming main stage is topLeft position, so we use that stage's taskId for focus
        verify(mActivityTaskManager, times(1))
                .setFocusedTask(mMainChild.getTaskId());
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_FLEXIBLE_TWO_APP_SPLIT)
    public void testRequestingFocusFor1090Launch() throws RemoteException {
        enterSplit(SNAP_TO_2_10_90);
        mSplitScreenTransitions.mPendingEnter.mFinishedCallback.onFinished(
                new WindowContainerTransaction(), new SurfaceControl.Transaction());
        // We're assuming side stage is bottomRight position, so we use that stage's taskId for
        // focus
        verify(mActivityTaskManager, times(1))
                .setFocusedTask(mSideChild.getTaskId());
    }

    @Test
    @UiThreadTest
    @EnableFlags(com.android.window.flags.Flags.FLAG_ENABLE_SPLIT_DISMISS_ANIMATION_V2)
    public void testPlayDismissAnimation_invoked() {
        enterSplit();

        // Simulate dismissing the Left (Main) stage
        TransitionInfo info = new TransitionInfoBuilder(TRANSIT_CLOSE, 0)
                // Closing task (Left/Main)
                .addChange(TRANSIT_TO_BACK, mMainChild)
                // Expanding task (Right/Side)
                .addChange(createChange(TRANSIT_CHANGE, mSideChild))
                // ...rest
                .addChange(TRANSIT_TO_BACK, mMainStage.mRootTaskInfo)
                .addChange(TRANSIT_TO_BACK, mSideStage.mRootTaskInfo)
                .addChange(TRANSIT_TO_BACK, mStageCoordinator.mSplitRootTaskInfo)
                .build();

        Rect topLeft = mSplitLayout.getTopLeftBounds();
        Rect bottomRight = mSplitLayout.getBottomRightBounds();

        // Add snapshot to the expanding change
        info.getChanges().get(1).setSnapshot(createMockSurface(), 0f);
        info.getChanges().get(1).setStartAbsBounds(bottomRight);
        info.getChanges().get(1).setEndAbsBounds(mSplitLayout.getRootBounds());
        info.getChanges().get(1).setLastParent(mSideStage.mRootTaskInfo.token);

        // Closing change bounds
        info.getChanges().get(0).setStartAbsBounds(topLeft);
        info.getChanges().get(0).setLastParent(mMainStage.mRootTaskInfo.token);

        IBinder transition = mSplitScreenTransitions.startDismissTransition(
                new WindowContainerTransaction(), mStageCoordinator,
                STAGE_TYPE_SIDE, EXIT_REASON_APP_FINISHED);

        boolean accepted = mStageCoordinator.startAnimation(transition, info,
                mock(SurfaceControl.Transaction.class),
                mock(SurfaceControl.Transaction.class),
                mock(Transitions.TransitionFinishCallback.class));

        assertTrue("Dismiss animation should be accepted", accepted);

        // Verify invocation + 2 animations started (one for closing, one for expanding)
        verify(mSplitTransitionAnimations, times(2)).buildDismissAnimation(any(),
                any(), eq(topLeft), eq(bottomRight), anyFloat(), any());
        verify(mSplitDismissAnimator, times(2)).start();

        // check animator created for expanding surface
        assertNotNull("Dismiss animation should be played for expanding surface",
                mSplitTransitionAnimations.buildDismissAnimation(info.getChanges().get(1),
                        mock(SurfaceControl.Transaction.class), topLeft, bottomRight, 0f,
                        mock(Consumer.class)));
        // check animator created for closing surface
        assertNotNull("Dismiss animation should be played for closing surface",
                mSplitTransitionAnimations.buildDismissAnimation(info.getChanges().get(0),
                        mock(SurfaceControl.Transaction.class), topLeft, bottomRight, 0f,
                        mock(Consumer.class)));
    }

    private TransitionInfo createEnterPairInfo() {
        return new TransitionInfoBuilder(TRANSIT_OPEN, 0)
                .addChange(TRANSIT_OPEN, mMainChild)
                .addChange(TRANSIT_OPEN, mSideChild)
                .build();
    }

    private void enterSplit() {
        enterSplit(SNAP_TO_2_50_50);
    }

    private void enterSplit(@SplitScreenConstants.PersistentSnapPosition int snapPosition) {
        TransitionInfo enterInfo = createEnterPairInfo();
        IBinder enterTransit = mSplitScreenTransitions.startEnterTransition(
                TRANSIT_OPEN, new WindowContainerTransaction(),
                new RemoteTransition(new TestRemoteTransition(), "Test"),
                mStageCoordinator, TRANSIT_SPLIT_SCREEN_PAIR_OPEN, false, snapPosition);
        mMainStage.onTaskAppeared(mMainChild, createMockSurface());
        mSideStage.onTaskAppeared(mSideChild, createMockSurface());
        mStageCoordinator.startAnimation(enterTransit, enterInfo,
                mock(SurfaceControl.Transaction.class),
                mock(SurfaceControl.Transaction.class),
                mock(Transitions.TransitionFinishCallback.class));
        mMainStage.activate(new WindowContainerTransaction(), true /* includingTopTask */);
    }

    @Test
    @UiThreadTest
    public void testSplitInvocationCallback() {
        enterSplit();
        mTestShellExecutor.flushAll();
        verify(mInvocationListener, times(1))
                .onSplitAnimationInvoked(eq(true));
    }

    /**
     * Matcher for any {@link HierarchyOp} containing a given task.
     */
    private static TypeSafeMatcher<HierarchyOp> wctContaining(RunningTaskInfo task) {
        return new TypeSafeMatcher<HierarchyOp>() {
            @Override
            public boolean matchesSafely(HierarchyOp hop) {
                return hop.getContainer() == task.token.asBinder();
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("task token=" + task.token);
            }
        };
    }

    /**
     * Matcher for a {@link HierarchyOp} reparenting a given task to
     * the given display area.
     */
    private static TypeSafeMatcher<HierarchyOp> isChildrenTasksReparent(
            RunningTaskInfo oldParent, DisplayAreaInfo newParent, boolean toTop) {
        return new TypeSafeMatcher<HierarchyOp>() {
            @Override
            public boolean matchesSafely(HierarchyOp hop) {
                return hop.getType() == HIERARCHY_OP_TYPE_CHILDREN_TASKS_REPARENT
                        && hop.getContainer() == oldParent.token.asBinder()
                        && hop.getNewParent() == newParent.token.asBinder()
                        && hop.getToTop() == toTop;
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("{reparent child tasks of " + oldParent.token.asBinder()
                        + " to " + newParent.token.asBinder() + ", toTop=" + toTop + "}");
            }
        };
    }

    private boolean containsSplitEnter(@NonNull WindowContainerTransaction wct) {
        for (int i = 0; i < wct.getHierarchyOps().size(); ++i) {
            WindowContainerTransaction.HierarchyOp op = wct.getHierarchyOps().get(i);
            if (op.getType() == HIERARCHY_OP_TYPE_REORDER
                    && op.getContainer() == mStageCoordinator.mSplitRootTaskInfo.token.asBinder()) {
                return true;
            }
        }
        return false;
    }

    private boolean containsSplitExit(@NonNull WindowContainerTransaction wct) {
        // reparenting of child tasks to null constitutes exiting split.
        boolean reparentedMain = false;
        boolean reparentedSide = false;
        for (int i = 0; i < wct.getHierarchyOps().size(); ++i) {
            WindowContainerTransaction.HierarchyOp op = wct.getHierarchyOps().get(i);
            if (op.getType() == HIERARCHY_OP_TYPE_CHILDREN_TASKS_REPARENT) {
                IBinder expectedNewParentBinder = Optional.ofNullable(
                        getNewParentTokenForStage(mMainStage, mRootTDAOrganizer))
                                .map(WindowContainerToken::asBinder)
                                .orElse(null);
                if (op.getContainer() == mMainStage.mRootTaskInfo.token.asBinder()
                        && op.getNewParent() == expectedNewParentBinder) {
                    reparentedMain = true;
                } else if (op.getContainer() == mSideStage.mRootTaskInfo.token.asBinder()
                        && op.getNewParent() == expectedNewParentBinder) {
                    reparentedSide = true;
                }
            }
        }
        return reparentedMain && reparentedSide;
    }

    private static TransitionInfo.Change createChange(@TransitionInfo.TransitionMode int mode,
            ActivityManager.RunningTaskInfo taskInfo) {
        TransitionInfo.Change out = new TransitionInfo.Change(taskInfo.token, createMockSurface());
        out.setMode(mode);
        out.setTaskInfo(taskInfo);
        return out;
    }

}
