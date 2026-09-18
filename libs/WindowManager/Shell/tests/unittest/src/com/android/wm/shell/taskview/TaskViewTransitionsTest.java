/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.wm.shell.taskview;

import static android.view.WindowManager.TRANSIT_CHANGE;
import static android.view.WindowManager.TRANSIT_CLOSE;
import static android.view.WindowManager.TRANSIT_OPEN;
import static android.view.WindowManager.TRANSIT_TO_BACK;
import static android.view.WindowManager.TRANSIT_TO_FRONT;

import static com.android.window.flags.Flags.FLAG_ENABLE_BUBBLE_ROOT_TASK;
import static com.android.wm.shell.Flags.FLAG_ENABLE_BUBBLE_ANYTHING;
import static com.android.wm.shell.Flags.FLAG_ENABLE_CREATE_ANY_BUBBLE;
import static com.android.wm.shell.bubbles.util.BubbleTestUtils.verifyExitBubbleTransaction;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.ActivityManager;
import android.graphics.Rect;
import android.os.Binder;
import android.os.IBinder;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.FlagsParameterization;
import android.testing.TestableLooper;
import android.view.SurfaceControl;
import android.window.TransitionInfo;
import android.window.WindowContainerToken;
import android.window.WindowContainerTransaction;

import androidx.annotation.Nullable;
import androidx.test.filters.SmallTest;

import com.android.testing.wm.util.MockToken;
import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.ShellTestCase;
import com.android.wm.shell.bubbles.BubbleHelper;
import com.android.wm.shell.shared.bubbles.BubbleFlagHelper;
import com.android.wm.shell.transition.Transitions;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import platform.test.runner.parameterized.ParameterizedAndroidJunit4;
import platform.test.runner.parameterized.Parameters;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;

/**
 * Tests of {@link TaskViewTransitions}.
 *
 * Build/Install/Run:
 *  atest WMShellUnitTests:TaskViewTransitionsTest
 */
@SmallTest
@RunWith(ParameterizedAndroidJunit4.class)
@TestableLooper.RunWithLooper(setAsMainLooper = true)
public class TaskViewTransitionsTest extends ShellTestCase {

    @Parameters(name = "{0}")
    public static List<FlagsParameterization> getParams() {
        return FlagsParameterization.allCombinationsOf(FLAG_ENABLE_BUBBLE_ANYTHING);
    }

    @Mock
    Transitions mTransitions;
    TaskViewTaskController mTaskViewTaskController;
    WindowContainerToken mToken;
    @Mock
    ShellTaskOrganizer mOrganizer;
    @Mock
    BubbleHelper mBubbleHelper;
    private final FakeTaskViewRootTask mTaskViewRootTask = new FakeTaskViewRootTask();

    Executor mExecutor = Runnable::run;

    ActivityManager.RunningTaskInfo mTaskInfo;
    TaskViewRepository mTaskViewRepository;
    TaskViewTransitions mTaskViewTransitions;

    public TaskViewTransitionsTest(FlagsParameterization flags) {}

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        mToken = new MockToken().token();

        mTaskInfo = createMockTaskInfo(314, mToken);

        mTaskViewRepository = new TaskViewRepository();
        when(mOrganizer.getExecutor()).thenReturn(mExecutor);
        mTaskViewTransitions = spy(new TaskViewTransitions(mTransitions, mTaskViewRepository,
                mOrganizer, Optional.of(mBubbleHelper), Optional.of(mTaskViewRootTask)));
        mTaskViewTaskController = createMockTaskController(mTaskInfo);
        mTaskViewTransitions.registerTaskView(mTaskViewTaskController);
    }

    @Test
    public void testMoveTaskViewToFullscreen_resetsInterceptBackPressed() {
        mTaskViewTransitions.moveTaskViewToFullscreen(mTaskViewTaskController);

        ArgumentCaptor<WindowContainerTransaction> wctCaptor =
                ArgumentCaptor.forClass(WindowContainerTransaction.class);
        verify(mTransitions).startTransition(anyInt(), wctCaptor.capture(), any());
        WindowContainerTransaction.Change chg =
                wctCaptor.getValue().getChanges().get(mToken.asBinder());
        if (!BubbleFlagHelper.enableRootTaskForBubble()) {
            assertThat(chg.getInterceptBackPressed()).isFalse();
        }
    }

    @EnableFlags({
            FLAG_ENABLE_CREATE_ANY_BUBBLE,
            FLAG_ENABLE_BUBBLE_ANYTHING,
    })
    @Test
    public void testMoveTaskViewToFullscreen_applyWctToExitBubble() {
        final Binder captionInsetsOwner = new Binder();
        when(mTaskViewTaskController.getCaptionInsetsOwner()).thenReturn(captionInsetsOwner);
        mTaskViewTransitions.moveTaskViewToFullscreen(mTaskViewTaskController);

        final TaskViewTransitions.PendingTransition pending = mTaskViewTransitions.findPending(
                mTaskViewTaskController, TRANSIT_CHANGE);
        assertThat(pending).isNotNull();
        final WindowContainerTransaction wct = pending.mWct;
        assertThat(wct).isNotNull();
        verifyExitBubbleTransaction(wct, mToken.asBinder(), captionInsetsOwner, true);
    }

    @Test
    public void testSetTaskBounds_taskNotVisible_noTransaction() {
        mTaskViewTransitions.setTaskViewVisible(mTaskViewTaskController, false);
        mTaskViewTransitions.setTaskBounds(mTaskViewTaskController,
                new Rect(0, 0, 100, 100));

        assertThat(mTaskViewTransitions.findPending(mTaskViewTaskController, TRANSIT_CHANGE))
                .isNull();
    }

    @Test
    public void testSetTaskBounds_taskVisible_boundsChangeTransaction() {
        mTaskViewTransitions.setTaskViewVisible(mTaskViewTaskController, true);

        // Consume the pending transaction from visibility change
        TaskViewTransitions.PendingTransition pending =
                mTaskViewTransitions.findPending(mTaskViewTaskController, TRANSIT_TO_FRONT);
        assertThat(pending).isNotNull();
        mTaskViewTransitions.startAnimation(pending.mClaimed,
                mock(TransitionInfo.class),
                new SurfaceControl.Transaction(),
                new SurfaceControl.Transaction(),
                mock(Transitions.TransitionFinishCallback.class));
        // Verify it was consumed
        TaskViewTransitions.PendingTransition pending2 =
                mTaskViewTransitions.findPending(mTaskViewTaskController, TRANSIT_TO_FRONT);
        assertThat(pending2).isNull();

        // Test that set bounds creates a new transaction
        mTaskViewTransitions.setTaskBounds(mTaskViewTaskController,
                new Rect(0, 0, 100, 100));
        assertThat(mTaskViewTransitions.findPending(mTaskViewTaskController, TRANSIT_CHANGE))
                .isNotNull();
    }

    @Test
    public void testSetTaskBounds_taskVisibleWithPending_noTransaction() {
        mTaskViewTransitions.setTaskViewVisible(mTaskViewTaskController, true);

        TaskViewTransitions.PendingTransition pending =
                mTaskViewTransitions.findPending(mTaskViewTaskController, TRANSIT_TO_FRONT);
        assertThat(pending).isNotNull();

        mTaskViewTransitions.setTaskBounds(mTaskViewTaskController,
                new Rect(0, 0, 100, 100));
        assertThat(mTaskViewTransitions.findPending(mTaskViewTaskController, TRANSIT_CHANGE))
                .isNull();
    }

    @Test
    public void testSetTaskBounds_sameBounds_noTransaction() {
        mTaskViewTransitions.setTaskViewVisible(mTaskViewTaskController, true);

        // Consume the pending transaction from visibility change
        TaskViewTransitions.PendingTransition pending =
                mTaskViewTransitions.findPending(mTaskViewTaskController, TRANSIT_TO_FRONT);
        assertThat(pending).isNotNull();
        mTaskViewTransitions.startAnimation(pending.mClaimed,
                mock(TransitionInfo.class),
                new SurfaceControl.Transaction(),
                new SurfaceControl.Transaction(),
                mock(Transitions.TransitionFinishCallback.class));
        // Verify it was consumed
        TaskViewTransitions.PendingTransition pending2 =
                mTaskViewTransitions.findPending(mTaskViewTaskController, TRANSIT_TO_FRONT);
        assertThat(pending2).isNull();

        // Test that set bounds creates a new transaction
        mTaskViewTransitions.setTaskBounds(mTaskViewTaskController,
                new Rect(0, 0, 100, 100));
        TaskViewTransitions.PendingTransition pendingBounds =
                mTaskViewTransitions.findPending(mTaskViewTaskController, TRANSIT_CHANGE);
        assertThat(pendingBounds).isNotNull();

        // Consume the pending bounds transaction
        mTaskViewTransitions.startAnimation(pendingBounds.mClaimed,
                mock(TransitionInfo.class),
                new SurfaceControl.Transaction(),
                new SurfaceControl.Transaction(),
                mock(Transitions.TransitionFinishCallback.class));
        // Verify it was consumed
        TaskViewTransitions.PendingTransition pendingBounds1 =
                mTaskViewTransitions.findPending(mTaskViewTaskController, TRANSIT_CHANGE);
        assertThat(pendingBounds1).isNull();

        // Test that setting the same bounds doesn't creates a new transaction
        mTaskViewTransitions.setTaskBounds(mTaskViewTaskController,
                new Rect(0, 0, 100, 100));
        TaskViewTransitions.PendingTransition pendingBounds2 =
                mTaskViewTransitions.findPending(mTaskViewTaskController, TRANSIT_CHANGE);
        assertThat(pendingBounds2).isNull();
    }

    @Test
    public void testSetTaskVisibility_taskRemoved_noNPE() {
        mTaskViewTransitions.unregisterTaskView(mTaskViewTaskController);

        mTaskViewTransitions.setTaskViewVisible(mTaskViewTaskController, false);
    }

    @Test
    public void testSetTaskVisibility_reorderNoHiddenVisibilitySync_resetsAlwaysOnTopAndReorder() {
        assumeTrue(BubbleFlagHelper.enableCreateAnyBubble());

        final Rect bounds = new Rect(0, 0, 100, 100);
        mTaskViewRepository.byTaskView(mTaskViewTaskController).mBounds = bounds;
        mTaskViewRepository.byTaskView(mTaskViewTaskController).mVisible = true;
        final IBinder mockBinder = mock(IBinder.class);
        when(mToken.asBinder()).thenReturn(mockBinder);

        mTaskViewTransitions.setTaskViewVisible(mTaskViewTaskController, false /* visible */,
                true /* reorder */, false /* syncHiddenWithVisibilityOnReorder */);

        final TaskViewTransitions.PendingTransition pending =
                mTaskViewTransitions.findPending(mTaskViewTaskController, TRANSIT_TO_BACK);
        assertThat(pending).isNotNull();
        final Map<IBinder, WindowContainerTransaction.Change> chgs = pending.mWct.getChanges();
        assertThat(chgs.keySet()).containsExactly(mockBinder);
        assertThat(chgs.get(mockBinder).getConfiguration().windowConfiguration.getBounds())
                .isEqualTo(bounds);
        assertThat(chgs.get(mockBinder).getHidden()).isFalse();
        final List<WindowContainerTransaction.HierarchyOp> ops = pending.mWct.getHierarchyOps();
        assertThat(ops).hasSize(2);
        assertThat(ops.get(0).isAlwaysOnTop()).isFalse();
        assertThat(ops.get(1).getToTop()).isFalse();
    }

    @Test
    public void testSetTaskBounds_taskRemoved_noNPE() {
        mTaskViewTransitions.unregisterTaskView(mTaskViewTaskController);

        mTaskViewTransitions.setTaskBounds(mTaskViewTaskController,
                new Rect(0, 0, 100, 100));
    }

    @Test
    public void testReorderTask_movedToFrontTransaction() {
        mTaskViewTransitions.reorderTaskViewTask(mTaskViewTaskController, true);
        // Consume the pending transaction from order change
        TaskViewTransitions.PendingTransition pending =
                mTaskViewTransitions.findPending(mTaskViewTaskController, TRANSIT_TO_FRONT);
        assertThat(pending).isNotNull();
        mTaskViewTransitions.startAnimation(pending.mClaimed,
                mock(TransitionInfo.class),
                new SurfaceControl.Transaction(),
                new SurfaceControl.Transaction(),
                mock(Transitions.TransitionFinishCallback.class));

        // Verify it was consumed
        TaskViewTransitions.PendingTransition pending2 =
                mTaskViewTransitions.findPending(mTaskViewTaskController, TRANSIT_TO_FRONT);
        assertThat(pending2).isNull();
    }

    @Test
    public void testReorderTask_movedToBackTransaction() {
        mTaskViewTransitions.reorderTaskViewTask(mTaskViewTaskController, false);
        // Consume the pending transaction from order change
        TaskViewTransitions.PendingTransition pending =
                mTaskViewTransitions.findPending(mTaskViewTaskController, TRANSIT_TO_BACK);
        assertThat(pending).isNotNull();
        mTaskViewTransitions.startAnimation(pending.mClaimed,
                mock(TransitionInfo.class),
                new SurfaceControl.Transaction(),
                new SurfaceControl.Transaction(),
                mock(Transitions.TransitionFinishCallback.class));

        // Verify it was consumed
        TaskViewTransitions.PendingTransition pending2 =
                mTaskViewTransitions.findPending(mTaskViewTaskController, TRANSIT_TO_BACK);
        assertThat(pending2).isNull();
    }

    @Test
    public void test_startAnimation_setsTaskNotFound() {
        TransitionInfo.Change change = mock(TransitionInfo.Change.class);
        when(change.getTaskInfo()).thenReturn(mTaskInfo);
        when(change.getMode()).thenReturn(TRANSIT_OPEN);

        List<TransitionInfo.Change> changes = new ArrayList<>();
        changes.add(change);

        TransitionInfo info = mock(TransitionInfo.class);
        when(info.getChanges()).thenReturn(changes);

        mTaskViewTransitions.startTaskView(new WindowContainerTransaction(),
                mTaskViewTaskController,
                mock(IBinder.class));

        TaskViewTransitions.PendingTransition pending =
                mTaskViewTransitions.findPendingOpeningTransition(mTaskViewTaskController);

        mTaskViewTransitions.startAnimation(pending.mClaimed,
                info,
                new SurfaceControl.Transaction(),
                new SurfaceControl.Transaction(),
                mock(Transitions.TransitionFinishCallback.class));

        verify(mTaskViewTaskController).setTaskNotFound();
    }

    @Test
    public void updateBoundsForUnfold_taskNotFound_doesNothing() {
        ActivityManager.RunningTaskInfo taskInfo = new ActivityManager.RunningTaskInfo();
        taskInfo.token = mock(WindowContainerToken.class);
        taskInfo.taskId = 666;
        Rect bounds = new Rect(100, 50, 200, 250);
        SurfaceControl.Transaction startTransaction = mock(SurfaceControl.Transaction.class);
        SurfaceControl.Transaction finishTransaction = mock(SurfaceControl.Transaction.class);
        assertThat(
                mTaskViewTransitions.updateBoundsForUnfold(bounds, startTransaction,
                        finishTransaction, taskInfo, mock(SurfaceControl.class)))
                .isFalse();

        verify(startTransaction, never()).reparent(any(), any());
    }

    @Test
    public void updateBoundsForUnfold_noPendingTransition_doesNothing() {
        Rect bounds = new Rect(100, 50, 200, 250);
        mTaskViewTransitions.setTaskBounds(mTaskViewTaskController, bounds);
        assertThat(mTaskViewTransitions.hasPending()).isFalse();

        SurfaceControl.Transaction startTransaction = mock(SurfaceControl.Transaction.class);
        SurfaceControl.Transaction finishTransaction = mock(SurfaceControl.Transaction.class);
        assertThat(
                mTaskViewTransitions.updateBoundsForUnfold(bounds, startTransaction,
                        finishTransaction, mTaskInfo, mock(SurfaceControl.class)))
                .isFalse();
        verify(startTransaction, never()).reparent(any(), any());
    }

    @Test
    public void updateBoundsForUnfold() {
        Rect bounds = new Rect(100, 50, 200, 250);
        mTaskViewTransitions.updateVisibilityState(mTaskViewTaskController, /* visible= */ true);
        mTaskViewTransitions.setTaskBounds(mTaskViewTaskController, bounds);
        assertThat(mTaskViewTransitions.hasPending()).isTrue();

        SurfaceControl.Transaction startTransaction = createMockTransaction();
        SurfaceControl.Transaction finishTransaction = createMockTransaction();
        assertThat(
                mTaskViewTransitions.updateBoundsForUnfold(bounds, startTransaction,
                        finishTransaction, mTaskInfo, mock(SurfaceControl.class)))
                .isTrue();
        assertThat(mTaskViewRepository.byTaskView(mTaskViewTaskController).mBounds)
                .isEqualTo(bounds);
    }

    @Test
    public void externalTransitionPending_alreadyFinished_removed() {
        IBinder transition = mock(IBinder.class);
        mTaskViewTransitions.enqueueExternal(mTaskViewTaskController, () -> transition);
        assertThat(mTaskViewTransitions.hasPending()).isTrue();

        // enqueue an external transition, that when started returns a null token as if it has
        // already finished
        mTaskViewTransitions.enqueueExternal(mTaskViewTaskController, () -> null);
        assertThat(mTaskViewTransitions.hasPending()).isTrue();

        mTaskViewTransitions.onExternalDone(transition);

        assertThat(mTaskViewTransitions.hasPending()).isFalse();
    }

    @Test
    public void removePendingTransitions_removePerTask() {
        WindowContainerToken otherToken = new MockToken().token();
        ActivityManager.RunningTaskInfo otherTaskInfo = createMockTaskInfo(999, otherToken);
        TaskViewTaskController otherController = createMockTaskController(otherTaskInfo);
        mTaskViewTransitions.registerTaskView(otherController);

        mTaskViewTransitions.setTaskViewVisible(mTaskViewTaskController, true);
        mTaskViewTransitions.setTaskViewVisible(otherController, true);

        // There should be two pending transitions, one for each task
        assertThat(mTaskViewTransitions.hasPending()).isTrue();
        assertThat(mTaskViewTransitions.findPending(mTaskViewTaskController,
                TRANSIT_TO_FRONT)).isNotNull();
        assertThat(mTaskViewTransitions.findPending(otherController, TRANSIT_TO_FRONT)).isNotNull();

        // Remove pending for one task, keep the other
        mTaskViewTransitions.removePendingTransitions(mTaskViewTaskController);
        assertThat(mTaskViewTransitions.hasPending()).isTrue();
        assertThat(mTaskViewTransitions.findPending(mTaskViewTaskController,
                TRANSIT_TO_FRONT)).isNull();
        assertThat(mTaskViewTransitions.findPending(otherController, TRANSIT_TO_FRONT)).isNotNull();

        // Remove the last one
        mTaskViewTransitions.removePendingTransitions(otherController);
        assertThat(mTaskViewTransitions.hasPending()).isFalse();
    }

    @Test
    public void enqueueRunningExternal_clearedFromPendingWithoutStarting() {
        // Add a normal transition to the queue first.
        mTaskViewTransitions.setTaskViewVisible(mTaskViewTaskController, true);
        assertThat(mTaskViewTransitions.findPending(mTaskViewTaskController,
                TRANSIT_TO_FRONT)).isNotNull();

        // Simulate an already running external transition by adding its binder ref.
        IBinder externalTransition = new Binder();
        mTaskViewTransitions.enqueueRunningExternal(mTaskViewTaskController, externalTransition);

        // Verify that both transitions are in the pending queue.
        assertThat(mTaskViewTransitions.findPending(mTaskViewTaskController,
                TRANSIT_TO_FRONT)).isNotNull();
        assertThat(mTaskViewTransitions.findPending(externalTransition)).isNotNull();

        // Now, clear the external gate transition.
        mTaskViewTransitions.onExternalDone(externalTransition);

        // Verify that the external gate is removed, but the original transition is still pending.
        assertThat(mTaskViewTransitions.findPending(externalTransition)).isNull();
        assertThat(mTaskViewTransitions.findPending(mTaskViewTaskController,
                TRANSIT_TO_FRONT)).isNotNull();
        assertThat(mTaskViewTransitions.hasPending()).isTrue();
    }

    @Test
    public void transitionWithNoChanges_shouldNotBeHandled() {
        IBinder transition = new Binder();
        TransitionInfo transitionInfo = new TransitionInfo(TRANSIT_CLOSE, /* flags= */ 0);
        boolean handled = mTaskViewTransitions.startAnimation(transition, transitionInfo,
                new SurfaceControl.Transaction(),
                new SurfaceControl.Transaction(),
                mock(Transitions.TransitionFinishCallback.class));

        assertThat(handled).isFalse();
    }

    @Test
    public void transitionWithNoChanges_tvtManaged_shouldStartNextTransition() {
        // enqueue an empty transition managed by TaskViewTransitions
        IBinder transition = new Binder();
        TransitionInfo transitionInfo = new TransitionInfo(TRANSIT_CLOSE, /* flags= */ 0);
        mTaskViewTransitions.enqueueRunningExternal(mTaskViewTaskController, transition);
        assertThat(mTaskViewTransitions.hasPending()).isTrue();

        // enqueue a normal transition
        mTaskViewTransitions.setTaskViewVisible(mTaskViewTaskController, true);
        TaskViewTransitions.PendingTransition pendingTransition =
                mTaskViewTransitions.findPending(mTaskViewTaskController, TRANSIT_TO_FRONT);
        assertThat(pendingTransition).isNotNull();
        assertThat(pendingTransition.mClaimed).isNull();

        IBinder pendingTransitionToken = new Binder();
        when(mTransitions.startTransition(pendingTransition.mType, pendingTransition.mWct,
                mTaskViewTransitions)).thenReturn(pendingTransitionToken);

        // dispatch the empty transition
        mTaskViewTransitions.startAnimation(transition, transitionInfo,
                new SurfaceControl.Transaction(),
                new SurfaceControl.Transaction(),
                mock(Transitions.TransitionFinishCallback.class));

        // check that the next transition was dispatched
        assertThat(pendingTransition.mClaimed).isNotNull();
    }

    @Test
    public void transitionWithNoChanges_nonTvtManaged_shouldNotStartNextTransition() {
        // enqueue a normal transition
        mTaskViewTransitions.setTaskViewVisible(mTaskViewTaskController, true);
        TaskViewTransitions.PendingTransition pendingTransition =
                mTaskViewTransitions.findPending(mTaskViewTaskController, TRANSIT_TO_FRONT);
        assertThat(pendingTransition).isNotNull();
        assertThat(pendingTransition.mClaimed).isNull();

        // dispatch an empty transition which is not managed by TaskViewTransitions
        IBinder transition = new Binder();
        TransitionInfo transitionInfo = new TransitionInfo(TRANSIT_CLOSE, /* flags= */ 0);
        mTaskViewTransitions.startAnimation(transition, transitionInfo,
                new SurfaceControl.Transaction(),
                new SurfaceControl.Transaction(),
                mock(Transitions.TransitionFinishCallback.class));

        // check that the next transition was not dispatched
        assertThat(pendingTransition.mClaimed).isNull();
    }

    @Test
    public void transitionWithNoTaskViewChanges_shouldNotBeHandled() {
        // Create an open transition for a task that is not in a taskView
        IBinder transition = new Binder();
        TransitionInfo info = new TransitionInfo(TRANSIT_OPEN, /* flags= */ 0);
        ActivityManager.RunningTaskInfo otherTask = createMockTaskInfo(8, new MockToken().token());
        SurfaceControl taskLeash = new SurfaceControl.Builder().setName("otherLeash").build();
        TransitionInfo.Change chg = new TransitionInfo.Change(otherTask.token, taskLeash);
        chg.setTaskInfo(otherTask);
        chg.setMode(TRANSIT_OPEN);
        info.addChange(chg);
        SurfaceControl rootLeash = new SurfaceControl.Builder().setName("rootLeash").build();
        info.addRoot(new TransitionInfo.Root(0, rootLeash, 0, 0));

        Boolean[] finishCalled = {false};
        Transitions.TransitionFinishCallback finishCallback = wct -> {
            finishCalled[0] = true;
        };

        // Check that TaskViewTransitions does not try to animate it
        boolean handled = mTaskViewTransitions.startAnimation(transition, info,
                new SurfaceControl.Transaction(),
                new SurfaceControl.Transaction(),
                finishCallback);

        assertThat(handled).isFalse();
        assertThat(finishCalled[0]).isFalse();
    }

    @EnableFlags({FLAG_ENABLE_CREATE_ANY_BUBBLE, FLAG_ENABLE_BUBBLE_ROOT_TASK})
    @Test
    public void testUpdateTaskViewTaskBounds_rootTask() {
        WindowContainerToken rootTaskToken = new MockToken().token();
        ActivityManager.RunningTaskInfo rootTaskInfo = createMockTaskInfo(100, rootTaskToken);
        WindowContainerToken taskToken = new MockToken().token();
        ActivityManager.RunningTaskInfo taskInfo = createMockTaskInfo(101, taskToken);
        Rect bounds = new Rect(0, 0, 10, 10);

        WindowContainerTransaction wct = mock(WindowContainerTransaction.class);
        mTaskViewTransitions.updateTaskViewTaskBounds(wct, taskInfo, bounds);
        verify(wct).setBounds(eq(taskToken), eq(bounds));

        clearInvocations(wct);
        mTaskViewRootTask.rootTaskInfo = rootTaskInfo;
        taskInfo.parentTaskId = rootTaskInfo.taskId;
        mTaskViewTransitions.updateTaskViewTaskBounds(wct, taskInfo, bounds);
        verify(wct).setBounds(eq(rootTaskToken), eq(bounds));

        clearInvocations(wct);
        taskInfo.parentTaskId = 10;
        mTaskViewTransitions.updateTaskViewTaskBounds(wct, taskInfo, bounds);
        verify(wct).setBounds(eq(taskToken), eq(bounds));
    }

    @Test
    public void bubbleTrampoline_shouldNotBeHandled() {
        IBinder trampolineToken = new Binder();
        TransitionInfo trampoline = createBubbleTrampolineTransition();
        boolean handled = mTaskViewTransitions.startAnimation(trampolineToken, trampoline,
                new SurfaceControl.Transaction(),
                new SurfaceControl.Transaction(),
                mock(Transitions.TransitionFinishCallback.class));

        assertThat(handled).isFalse();
    }

    @Test
    public void bubbleTrampoline_tvtManaged_shouldStartNextTransition() {
        // enqueue a trampoline transition managed by TaskViewTransitions
        IBinder trampolineToken = new Binder();
        mTaskViewTransitions.enqueueRunningExternal(mTaskViewTaskController, trampolineToken);
        assertThat(mTaskViewTransitions.hasPending()).isTrue();

        // enqueue a normal transition
        mTaskViewTransitions.setTaskViewVisible(mTaskViewTaskController, true);
        TaskViewTransitions.PendingTransition pendingTransition =
                mTaskViewTransitions.findPending(mTaskViewTaskController, TRANSIT_TO_FRONT);
        assertThat(pendingTransition).isNotNull();
        assertThat(pendingTransition.mClaimed).isNull();

        IBinder pendingTransitionToken = new Binder();
        when(mTransitions.startTransition(pendingTransition.mType, pendingTransition.mWct,
                mTaskViewTransitions)).thenReturn(pendingTransitionToken);

        // dispatch the bubble trampoline transition
        TransitionInfo trampoline = createBubbleTrampolineTransition();
        mTaskViewTransitions.startAnimation(trampolineToken, trampoline,
                new SurfaceControl.Transaction(),
                new SurfaceControl.Transaction(),
                mock(Transitions.TransitionFinishCallback.class));

        // check that the next transition was dispatched
        assertThat(pendingTransition.mClaimed).isNotNull();
    }

    @Test
    public void bubbleTrampoline_notTvtManaged_shouldNotStartNextTransition() {
        // enqueue a normal transition
        mTaskViewTransitions.setTaskViewVisible(mTaskViewTaskController, true);
        TaskViewTransitions.PendingTransition pendingTransition =
                mTaskViewTransitions.findPending(mTaskViewTaskController, TRANSIT_TO_FRONT);
        assertThat(pendingTransition).isNotNull();
        assertThat(pendingTransition.mClaimed).isNull();

        // dispatch the bubble trampoline transition
        IBinder trampolineToken = new Binder();
        TransitionInfo trampoline = createBubbleTrampolineTransition();
        mTaskViewTransitions.startAnimation(trampolineToken, trampoline,
                new SurfaceControl.Transaction(),
                new SurfaceControl.Transaction(),
                mock(Transitions.TransitionFinishCallback.class));

        // check that the next transition was not dispatched
        assertThat(pendingTransition.mClaimed).isNull();
    }

    private ActivityManager.RunningTaskInfo createMockTaskInfo(int taskId,
            WindowContainerToken token) {
        ActivityManager.RunningTaskInfo taskInfo = new ActivityManager.RunningTaskInfo();
        taskInfo.token = token;
        taskInfo.taskId = taskId;
        taskInfo.taskDescription = mock(ActivityManager.TaskDescription.class);
        return taskInfo;
    }

    private TaskViewTaskController createMockTaskController(
            ActivityManager.RunningTaskInfo taskInfo) {
        TaskViewTaskController controller = mock(TaskViewTaskController.class);
        when(controller.getTaskInfo()).thenReturn(taskInfo);
        when(controller.getTaskToken()).thenReturn(taskInfo.token);
        return controller;
    }

    private SurfaceControl.Transaction createMockTransaction() {
        SurfaceControl.Transaction transaction = mock(SurfaceControl.Transaction.class);
        when(transaction.reparent(any(), any())).thenReturn(transaction);
        when(transaction.setPosition(any(), anyFloat(), anyFloat())).thenReturn(transaction);
        when(transaction.setWindowCrop(any(), anyInt(), anyInt())).thenReturn(transaction);
        return transaction;
    }

    private TransitionInfo createBubbleTrampolineTransition() {
        final SurfaceControl leash = new SurfaceControl.Builder().setName("testLeash").build();
        final ActivityManager.RunningTaskInfo openingBubbleTask = createFakeAppBubbleTaskInfo();
        final TransitionInfo.Change openingBubble =
                new TransitionInfo.Change(openingBubbleTask.token, leash);
        openingBubble.setTaskInfo(openingBubbleTask);
        openingBubble.setMode(TRANSIT_OPEN);

        final ActivityManager.RunningTaskInfo closingBubbleTask = createFakeAppBubbleTaskInfo();
        final TransitionInfo.Change closingBubble =
                new TransitionInfo.Change(closingBubbleTask.token, leash);
        closingBubble.setTaskInfo(closingBubbleTask);
        closingBubble.setMode(TRANSIT_CLOSE);

        TransitionInfo info = new TransitionInfo(TRANSIT_CLOSE, 0);
        info.addChange(openingBubble);
        info.addChange(closingBubble);

        when(mBubbleHelper.containsBubbleSwitch(info)).thenReturn(true);

        return info;
    }

    private ActivityManager.RunningTaskInfo createFakeAppBubbleTaskInfo() {
        ActivityManager.RunningTaskInfo appBubble = new ActivityManager.RunningTaskInfo();
        appBubble.token = new MockToken().token();
        appBubble.isAppBubble = true;
        return appBubble;
    }

    private static class FakeTaskViewRootTask implements TaskViewRootTask {

        public ActivityManager.RunningTaskInfo rootTaskInfo;

        @Nullable
        @Override
        public ActivityManager.RunningTaskInfo getRootTaskInfo() {
            return rootTaskInfo;
        }
    }
}
