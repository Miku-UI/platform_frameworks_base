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

package com.android.wm.shell.taskview;

import static android.app.ActivityTaskManager.INVALID_TASK_ID;
import static android.view.WindowManager.TRANSIT_CHANGE;
import static android.view.WindowManager.TRANSIT_CLOSE;
import static android.view.WindowManager.TRANSIT_OPEN;
import static android.view.WindowManager.TRANSIT_TO_BACK;
import static android.view.WindowManager.TRANSIT_TO_FRONT;

import static com.android.window.flags.Flags.FLAG_ENABLE_BUBBLE_ROOT_TASK;
import static com.android.window.flags.Flags.enableHandlersDebuggingMode;
import static com.android.wm.shell.Flags.FLAG_ENABLE_CREATE_ANY_BUBBLE;
import static com.android.wm.shell.Flags.FLAG_TASK_VIEW_TRANSITIONS_REFACTOR;
import static com.android.wm.shell.Flags.taskViewTransitionsRefactor;
import static com.android.wm.shell.transition.TransitionDispatchState.CAPTURED_UNRELATED_CHANGE;
import static com.android.wm.shell.transition.TransitionDispatchState.LOST_RELEVANT_CHANGE;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.annotation.NonNull;
import android.app.ActivityManager;
import android.graphics.Rect;
import android.os.IBinder;
import android.platform.test.annotations.UsesFlags;
import android.platform.test.flag.junit.FlagsParameterization;
import android.testing.TestableLooper;
import android.util.Slog;
import android.view.SurfaceControl;
import android.window.TransitionInfo;
import android.window.WindowContainerToken;
import android.window.WindowContainerTransaction;

import androidx.test.filters.SmallTest;

import com.android.testing.wm.util.StubTransaction;
import com.android.testing.wm.util.TransitionInfoBuilder;
import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.ShellTestCase;
import com.android.wm.shell.bubbles.BubbleHelper;
import com.android.wm.shell.common.ShellExecutor;
import com.android.wm.shell.shared.bubbles.BubbleFlagHelper;
import com.android.wm.shell.transition.TransitionDispatchState;
import com.android.wm.shell.transition.Transitions;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import platform.test.runner.parameterized.ParameterizedAndroidJunit4;
import platform.test.runner.parameterized.Parameters;

import java.util.List;
import java.util.Optional;


/**
 * Verifies the behavior of {@link TaskViewTransitions#startAnimation}.
 *
 * 1. Verifies that startAnimation populates {@link TransitionDispatchState} correctly.
 * 2. Verifies that changes behind {@link FLAG_ENABLE_HANDLERS_DEBUGGING_MODE} don't change the
 *    behavior of startAnimation.
 *
 * Build/Install/Run:
 *  atest WMShellUnitTests:TaskViewTransitionStartAnimationTest
 */
@SmallTest
@RunWith(ParameterizedAndroidJunit4.class)
@TestableLooper.RunWithLooper(setAsMainLooper = true)
@UsesFlags({com.android.window.flags.Flags.class, com.android.wm.shell.Flags.class})
public class TaskViewTransitionStartAnimationTest extends ShellTestCase {
    private static final String TAG = "TVstartAnimTest";
    private static final Rect BOUNDS = new Rect(0, 0, 100, 100);

    @Parameters(name = "{0}")
    public static List<FlagsParameterization> getParams() {
        return FlagsParameterization.progressionOf(
                FLAG_TASK_VIEW_TRANSITIONS_REFACTOR,
                FLAG_ENABLE_CREATE_ANY_BUBBLE,
                FLAG_ENABLE_BUBBLE_ROOT_TASK);
    }

    @Mock
    private Transitions mTransitions;
    @Mock
    private TaskViewTaskController mTaskViewTaskController;
    @Mock
    private ActivityManager.RunningTaskInfo mTaskInfo;
    @Mock
    private ActivityManager.RunningTaskInfo mUnregisteredTaskInfo;
    @Mock
    private WindowContainerToken mToken;
    @Mock
    private IBinder mTokenBinder;
    @Mock
    private WindowContainerToken mUnregisteredToken;
    @Mock
    private IBinder mUnregisteredTokenBinder;
    @Mock
    private IBinder mLaunchCookie;
    @Mock
    private SurfaceControl mTaskLeash;
    @Mock
    private SurfaceControl mSurfaceControl;
    @Mock
    private Transitions.TransitionFinishCallback mFinishCallback;
    @Mock
    private BubbleHelper mBubbleHelper;

    private final TaskViewRepository mTaskViewRepository = new TaskViewRepository();
    private TaskViewTransitions mTaskViewTransitions;
    private StubTransaction mStartTransaction;
    private StubTransaction mFinishTransaction;

    public TaskViewTransitionStartAnimationTest(FlagsParameterization flags) {
        mSetFlagsRule.setFlagsParameterization(flags);
    }

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        when(mToken.asBinder()).thenReturn(mTokenBinder);
        when(mUnregisteredToken.asBinder()).thenReturn(mUnregisteredTokenBinder);
        mTaskInfo = new ActivityManager.RunningTaskInfo();
        mTaskInfo.token = mToken;
        mTaskInfo.taskId = 314;
        mTaskInfo.parentTaskId = INVALID_TASK_ID;
        mTaskInfo.taskDescription = mock(ActivityManager.TaskDescription.class);
        mTaskInfo.launchCookies.add(mLaunchCookie);

        // Task not registered in mTaskViewTaskController
        mUnregisteredTaskInfo =  new ActivityManager.RunningTaskInfo();
        mUnregisteredTaskInfo.token = mUnregisteredToken;
        // Same id as the other to match pending info id
        mUnregisteredTaskInfo.taskId = 314;
        mUnregisteredTaskInfo.parentTaskId = INVALID_TASK_ID;
        mUnregisteredTaskInfo.launchCookies.add(mock(IBinder.class));
        mTaskInfo.taskDescription = mock(ActivityManager.TaskDescription.class);

        mTaskViewTransitions = new TaskViewTransitions(mTransitions, mTaskViewRepository,
                mock(ShellTaskOrganizer.class), Optional.of(mBubbleHelper),
                /* taskViewRootTask= */ Optional.empty());
        mTaskViewTransitions.registerTaskView(mTaskViewTaskController);
        when(mTaskViewTaskController.getTaskInfo()).thenReturn(mTaskInfo);
        when(mTaskViewTaskController.getPendingInfo()).thenReturn(mTaskInfo);
        when(mTaskViewTaskController.getTaskToken()).thenReturn(mToken);
        when(mTaskViewTaskController.getSurfaceControl()).thenReturn(mSurfaceControl);
        when(mTaskViewTaskController.prepareOpen(any(), any())).thenReturn(BOUNDS);

        mFinishCallback = mock(Transitions.TransitionFinishCallback.class);

        mStartTransaction = spy(new StubTransaction());
        mFinishTransaction = spy(new StubTransaction());
    }

    /**
     * Tests on {@link TransitionDispatchState}
     */
    @Test
    public void taskView_dispatchStateFindsIncompatible_animationMode() {
        assumeTrue(enableHandlersDebuggingMode());
        assumeTrue(taskViewTransitionsRefactor()); // To avoid running twice

        final TransitionInfo.Change showingTV = getTaskView(TRANSIT_TO_FRONT);
        final TransitionInfo.Change nonTV = getTask(TRANSIT_TO_BACK, false /* registered */);
        final TaskViewTransitions.PendingTransition pending =
                setPendingTransaction(true /* visible*/, false /* opening */);
        // Showing taskView + normal task.
        // TaskView is accepted, but normal task is detected as error
        final TransitionInfo info = new TransitionInfoBuilder(TRANSIT_TO_FRONT)
                .addChange(showingTV)
                .addChange(nonTV)
                .build();

        final TransitionDispatchState dispatchState =
                spy(new TransitionDispatchState(pending.mClaimed, info));

        final boolean handled = mTaskViewTransitions.startAnimation(pending.mClaimed, info,
                dispatchState, mStartTransaction, mFinishTransaction, mFinishCallback);

        Slog.v(TAG, "DispatchState:\n" + dispatchState.getDebugInfo());
        // Has animated the taskView
        assertWithMessage("Handler should play the transition")
                .that(handled).isTrue();
        final ArgumentCaptor<WindowContainerTransaction> wctCaptor =
                ArgumentCaptor.forClass(WindowContainerTransaction.class);
        verify(mFinishCallback).onTransitionFinished(wctCaptor.capture());
        assertWithMessage("Expected wct to be created and sent to callback")
                .that(wctCaptor.getValue()).isNotNull();
        verify(pending.mTaskView).notifyAppeared(eq(false));

        // Non task-view spotted as intruder
        verify(dispatchState).addError(eq(mTaskViewTransitions), eq(nonTV),
                eq(CAPTURED_UNRELATED_CHANGE));
        assertThat(dispatchState.hasErrors(mTaskViewTransitions)).isTrue();
    }

    @Test
    public void taskView_dispatchStateFindsCompatible_dataCollectionMode() {
        assumeTrue(enableHandlersDebuggingMode());
        assumeTrue(taskViewTransitionsRefactor()); // To avoid running twice

        final TransitionInfo.Change showingTV = getTaskView(TRANSIT_TO_FRONT);
        final TransitionInfo.Change nonTV = getTask(TRANSIT_TO_BACK, false /* registered */);
        final TaskViewTransitions.PendingTransition pending =
                setPendingTransaction(true /* visible*/, false /* opening */);
        // Showing taskView + normal task.
        // TaskView is detected as change that could have played
        final TransitionInfo info = new TransitionInfoBuilder(TRANSIT_TO_FRONT)
                .addChange(showingTV)
                .addChange(nonTV)
                .build();

        final TransitionDispatchState dispatchState =
                spy(new TransitionDispatchState(pending.mClaimed, info));

        final boolean handled = mTaskViewTransitions.startAnimation(pending.mClaimed, null,
                dispatchState, mStartTransaction, mFinishTransaction, mFinishCallback);

        Slog.v(TAG, "DispatchState:\n" + dispatchState.getDebugInfo());
        // Has not animated the taskView
        assertWithMessage("Handler should not play the transition")
                .that(handled).isFalse();
        verify(mFinishCallback, never()).onTransitionFinished(any());
        verify(pending.mTaskView, never()).notifyAppeared(anyBoolean());

        // Non task-view spotted as intruder
        verify(dispatchState)
                .addError(eq(mTaskViewTransitions), eq(showingTV), eq(LOST_RELEVANT_CHANGE));
        assertThat(dispatchState.hasErrors(mTaskViewTransitions)).isTrue();
    }

    /**
     * Refactor tests on taskViews
     */
    @Test
    public void hideTaskViewHandled() {
        final TaskViewTransitions.PendingTransition pending =
                setPendingTransaction(false /* visible*/, false /* opening */);
        final TransitionInfo info = new TransitionInfoBuilder(TRANSIT_TO_BACK)
                .addChange(getTaskView(TRANSIT_TO_BACK)).build();

        final boolean handled = mTaskViewTransitions.startAnimation(pending.mClaimed, info,
                mStartTransaction, mFinishTransaction, mFinishCallback);

        assertWithMessage("Handler should have consumed transition")
                .that(handled).isTrue();
        verify(mStartTransaction).hide(mTaskLeash);
        verify(mTaskViewTaskController).prepareHideAnimation(mFinishTransaction);
    }

    @Test
    public void removeTaskViewHandled() {
        final TaskViewTransitions.PendingTransition pending =
                setPendingTransaction(false /* visible*/, false /* opening */);
        final TransitionInfo info = new TransitionInfoBuilder(TRANSIT_TO_BACK)
                .addChange(getTaskView(TRANSIT_CLOSE)).build();

        final boolean handled = mTaskViewTransitions.startAnimation(pending.mClaimed, info,
                mStartTransaction, mFinishTransaction, mFinishCallback);

        assertWithMessage("Handler should have consumed transition")
                .that(handled).isTrue();
        verify(mTaskViewTaskController).prepareCloseAnimation(mTaskLeash, mStartTransaction);
    }

    @Test
    public void openTaskViewHandled() {
        final TaskViewTransitions.PendingTransition pending =
                setPendingTransaction(true /* visible*/, true /* opening */);
        final TransitionInfo info = new TransitionInfoBuilder(TRANSIT_TO_FRONT)
                .addChange(getTaskView(TRANSIT_OPEN)).build();

        final boolean handled = mTaskViewTransitions.startAnimation(pending.mClaimed, info,
                mStartTransaction, mFinishTransaction, mFinishCallback);

        assertWithMessage("Handler should have consumed transition")
                .that(handled).isTrue();
        final ArgumentCaptor<WindowContainerTransaction> wctCaptor =
                ArgumentCaptor.forClass(WindowContainerTransaction.class);
        verify(mFinishCallback).onTransitionFinished(wctCaptor.capture());
        final WindowContainerTransaction wct = wctCaptor.getValue();
        assertOpenAnimationPreparation(mTokenBinder, pending, wct, true /* newTask */,
                true /* shouldInterceptBack */, true /* shouldCorpWindow */);
    }

    @Test
    public void openTaskView_withParentTask_doesNotInterceptBack() {
        mTaskInfo.parentTaskId = 3;  // Simulate bubble root task
        final TaskViewTransitions.PendingTransition pending =
                setPendingTransaction(true /* visible*/, true /* opening */);
        final TransitionInfo info = new TransitionInfoBuilder(TRANSIT_TO_FRONT)
                .addChange(getTaskView(TRANSIT_OPEN)).build();

        mTaskViewTransitions.startAnimation(pending.mClaimed, info, mStartTransaction,
                mFinishTransaction, mFinishCallback);

        final ArgumentCaptor<WindowContainerTransaction> wctCaptor =
                ArgumentCaptor.forClass(WindowContainerTransaction.class);
        verify(mFinishCallback).onTransitionFinished(wctCaptor.capture());
        final WindowContainerTransaction wct = wctCaptor.getValue();
        assertOpenAnimationPreparation(mTokenBinder, pending, wct, true /* newTask */,
                false /* shouldInterceptBack */,
                !BubbleFlagHelper.enableRootTaskForBubble() /* shouldCorpWindow */);
    }

    @Test
    public void openTask_onlyAlienChanges_notHandled() {
        // Builds a transition with ONLY a non-TaskView change.
        final TransitionInfo info = new TransitionInfoBuilder(TRANSIT_OPEN)
                .addChange(getTask(TRANSIT_OPEN, false /* registered */))
                .build();

        final boolean handled = mTaskViewTransitions.startAnimation(
                mock(IBinder.class), info, mStartTransaction, mFinishTransaction, mFinishCallback);

        assertWithMessage("Handler should not consume alien-only transition")
                .that(handled).isFalse();
        verify(mFinishCallback, never()).onTransitionFinished(any());
    }

    @Test
    public void showTaskViewHandled() {
        final TaskViewTransitions.PendingTransition pending =
                setPendingTransaction(true /* visible*/, false /* opening */);
        final TransitionInfo info = new TransitionInfoBuilder(TRANSIT_TO_FRONT)
                .addChange(getTaskView(TRANSIT_TO_FRONT)).build();

        final boolean handled = mTaskViewTransitions.startAnimation(pending.mClaimed, info,
                mStartTransaction, mFinishTransaction, mFinishCallback);

        assertWithMessage("Handler should have consumed transition")
                .that(handled).isTrue();
        final ArgumentCaptor<WindowContainerTransaction> wctCaptor =
                ArgumentCaptor.forClass(WindowContainerTransaction.class);
        verify(mFinishCallback).onTransitionFinished(wctCaptor.capture());
        final WindowContainerTransaction wct = wctCaptor.getValue();
        assertOpenAnimationPreparation(mTokenBinder, pending, wct, false /* newTask */,
                false /* shouldInterceptBack */, true /* shouldCorpWindow */);
    }

    @Test
    public void showTaskView_whenSurfaceDestroyed_notHandled() {
        assumeTrue(taskViewTransitionsRefactor());

        // Simulate the surface being destroyed (e.g. a collapsed bubble being relaunched).
        when(mTaskViewTaskController.prepareOpen(any(), any())).thenReturn(null);
        final TransitionInfo info = new TransitionInfoBuilder(TRANSIT_TO_FRONT)
                .addChange(getTaskView(TRANSIT_TO_FRONT)).build();

        final boolean handled = mTaskViewTransitions.startAnimation(mock(IBinder.class), info,
                mStartTransaction, mFinishTransaction, mFinishCallback);

        assertWithMessage("Handler should defer the transition when the surface is not ready")
                .that(handled).isFalse();
        verify(mFinishCallback, never()).onTransitionFinished(any());
    }

    @Test
    public void taskToTaskViewHandled() {
        assumeTrue(BubbleFlagHelper.enableCreateAnyBubble());

        final TaskViewTransitions.PendingTransition pending =
                setPendingTransaction(true /* visible*/, false /* opening */);
        final TransitionInfo info = new TransitionInfoBuilder(TRANSIT_TO_FRONT)
                .addChange(getTask(TRANSIT_TO_FRONT, false /* register */)).build();

        final boolean handled = mTaskViewTransitions.startAnimation(pending.mClaimed, info,
                mStartTransaction, mFinishTransaction, mFinishCallback);

        assertWithMessage("Handler should have consumed transition")
                .that(handled).isTrue();
        final ArgumentCaptor<WindowContainerTransaction> wctCaptor =
                ArgumentCaptor.forClass(WindowContainerTransaction.class);
        verify(mFinishCallback).onTransitionFinished(wctCaptor.capture());
        final WindowContainerTransaction wct = wctCaptor.getValue();
        assertOpenAnimationPreparation(mUnregisteredTokenBinder, pending, wct, true /* newTask */,
                true /* shouldInterceptBack */, true /* shouldCorpWindow */);
    }

    @Test
    public void taskToTaskView_withParentTask_doesNotInterceptBack() {
        assumeTrue(BubbleFlagHelper.enableCreateAnyBubble());

        mTaskInfo.parentTaskId = 3;  // Simulate bubble root task
        final TaskViewTransitions.PendingTransition pending =
                setPendingTransaction(true /* visible*/, false /* opening */);
        final TransitionInfo info = new TransitionInfoBuilder(TRANSIT_TO_FRONT)
                .addChange(getTask(TRANSIT_TO_FRONT, false /* register */)).build();

        mTaskViewTransitions.startAnimation(pending.mClaimed, info, mStartTransaction,
                mFinishTransaction, mFinishCallback);

        final ArgumentCaptor<WindowContainerTransaction> wctCaptor =
                ArgumentCaptor.forClass(WindowContainerTransaction.class);
        verify(mFinishCallback).onTransitionFinished(wctCaptor.capture());
        final WindowContainerTransaction wct = wctCaptor.getValue();
        assertOpenAnimationPreparation(mUnregisteredTokenBinder, pending, wct, true /* newTask */,
                false /* shouldInterceptBack */,
                !BubbleFlagHelper.enableRootTaskForBubble() /* shouldCorpWindow */);
    }

    @Test
    public void testMergeAnimation_dispatchDisplayTransition() {
        final TransitionInfo.Change displayChange = new TransitionInfo.Change(
                /* container= */ null, new SurfaceControl());
        displayChange.setStartAbsBounds(new Rect(0, 0, 500, 1000));
        displayChange.setEndAbsBounds(new Rect(0, 0, 1000, 500));
        displayChange.setMode(TRANSIT_CHANGE);
        displayChange.setFlags(TransitionInfo.FLAG_IS_DISPLAY);
        final TransitionInfo.Change taskViewChange = getTaskView(TRANSIT_CHANGE);
        final TransitionInfo displayChangeInfo = new TransitionInfoBuilder(TRANSIT_CHANGE)
                .addChange(taskViewChange).addChange(displayChange).build();
        when(mTransitions.getMainExecutor()).thenReturn(mock(ShellExecutor.class));
        final IBinder displayTransition = mock(IBinder.class);
        final boolean handled = mTaskViewTransitions.startAnimation(displayTransition,
                displayChangeInfo, mStartTransaction, mFinishTransaction, mFinishCallback);

        assertWithMessage("Handler should have consumed display change transition")
                .that(handled).isTrue();
        assertWithMessage("TaskView change should be consumed")
                .that(displayChangeInfo.getChanges()).doesNotContain(taskViewChange);
        verify(mFinishCallback, never()).onTransitionFinished(any());

        // Assume that TaskViewTransitions#setTaskBounds starts another transition.
        final IBinder setTaskBoundsTransition = mock(IBinder.class);
        final TransitionInfo.Change boundsChange = getTaskView(TRANSIT_CHANGE);
        final Rect newBounds = new Rect(100, 100, 500, 500);
        when(boundsChange.getEndAbsBounds()).thenReturn(newBounds);
        final TransitionInfo mergeTransitionInfo = new TransitionInfoBuilder(TRANSIT_CHANGE)
                .addChange(boundsChange).build();
        mTaskViewTransitions.mergeAnimation(displayTransition, mergeTransitionInfo,
                mStartTransaction, mFinishTransaction, setTaskBoundsTransition, mFinishCallback);

        verify(mStartTransaction).setWindowCrop(eq(boundsChange.getLeash()),
                eq(BOUNDS.width()), eq(BOUNDS.height()));
        verify(mTransitions).dispatchTransition(eq(displayTransition), eq(displayChangeInfo),
                eq(mStartTransaction), eq(mFinishTransaction),
                eq(mFinishCallback), eq(mTaskViewTransitions));
    }

    @Test
    public void changingTaskViewHandled() {
        final TaskViewTransitions.PendingTransition pending =
                setPendingTransaction(true /* visible*/, false /* opening */);
        final TransitionInfo info = new TransitionInfoBuilder(TRANSIT_TO_FRONT)
                .addChange(getTaskView(TRANSIT_CHANGE)).build();

        final boolean handled = mTaskViewTransitions.startAnimation(pending.mClaimed, info,
                mStartTransaction, mFinishTransaction, mFinishCallback);

        assertWithMessage("Handler should have consumed transition")
                .that(handled).isTrue();
        final ArgumentCaptor<WindowContainerTransaction> wctCaptor =
                ArgumentCaptor.forClass(WindowContainerTransaction.class);
        verify(mFinishCallback).onTransitionFinished(wctCaptor.capture());
        final WindowContainerTransaction wct = wctCaptor.getValue();
        assertBoundsAndSurfaceTransactions(wct.getChanges().get(mTokenBinder),
                true /* shouldCorpWindow */);
    }

    @Test
    public void closingTaskHandled() {
        final TaskViewTransitions.PendingTransition pending =
                setPendingTransaction(false /* visible*/, false /* opening */);
        final TransitionInfo info = new TransitionInfoBuilder(TRANSIT_TO_BACK)
                .addChange(getTask(TRANSIT_CLOSE, false /* registered */)).build();

        final boolean handled = mTaskViewTransitions.startAnimation(pending.mClaimed, info,
                mStartTransaction, mFinishTransaction, mFinishCallback);

        assertWithMessage("Handler should have consumed transition")
                .that(handled).isTrue();
        verify(mTaskViewTaskController, never()).prepareCloseAnimation(any(), any());
    }

    @Test
    public void closingTask_withMixedChanges_notHandled() {
        // Builds a transition with both a TaskView change and a non-TaskView change.
        final TransitionInfo info = new TransitionInfoBuilder(TRANSIT_CLOSE)
                .addChange(getTaskView(TRANSIT_CLOSE))
                .addChange(getTask(TRANSIT_TO_FRONT, false /* registered */))
                .build();

        final boolean handled = mTaskViewTransitions.startAnimation(
                mock(IBinder.class), info, mStartTransaction, mFinishTransaction, mFinishCallback);

        assertWithMessage("Handler should not consume mixed changes transition")
                .that(handled).isFalse();
        verify(mFinishCallback, never()).onTransitionFinished(any());
    }

    /**
     * Refactor tests on non-taskViews
     */
    @Test
    public void hidingTaskNotHandled() {
        final TaskViewTransitions.PendingTransition pending =
                setPendingTransaction(false /* visible*/, false /* opening */);
        final TransitionInfo info = new TransitionInfoBuilder(TRANSIT_TO_BACK)
                .addChange(getTask(TRANSIT_TO_BACK, false /* registered */)).build();

        mTaskViewTransitions.startAnimation(pending.mClaimed, info,
                mStartTransaction, mFinishTransaction, mFinishCallback);

        verify(mStartTransaction, never()).hide(any());
        verify(pending.mTaskView, never()).prepareHideAnimation(any());

        final ArgumentCaptor<WindowContainerTransaction> wctCaptor =
                ArgumentCaptor.forClass(WindowContainerTransaction.class);
        verify(mFinishCallback).onTransitionFinished(wctCaptor.capture());
        assertWithMessage("No wct should have been created")
                .that(wctCaptor.getValue()).isNull();
    }

    @Test
    public void showingTaskNotHandled() {
        final TaskViewTransitions.PendingTransition pending =
                setPendingTransaction(true /* visible*/, false /* opening */);
        final TransitionInfo info =
                new TransitionInfoBuilder(TRANSIT_TO_FRONT)
                        .addChange(getTask(TRANSIT_TO_FRONT, false /* registered */))
                        .build();
        // Change id to avoid matching pending info, or this would be task->taskView
        mUnregisteredTaskInfo.taskId = 222;

        mTaskViewTransitions.startAnimation(
                pending.mClaimed, info, mStartTransaction, mFinishTransaction, mFinishCallback);
        verify(mTaskViewTaskController, never()).notifyAppeared(anyBoolean());
        final ArgumentCaptor<WindowContainerTransaction> wctCaptor =
                ArgumentCaptor.forClass(WindowContainerTransaction.class);
        verify(mFinishCallback).onTransitionFinished(wctCaptor.capture());
        assertWithMessage("No wct should have been created")
                .that(wctCaptor.getValue()).isNull();
    }

    @Test
    public void changingTaskNotHandled() {
        final TaskViewTransitions.PendingTransition pending =
                setPendingTransaction(true /* visible*/, false /* opening */);
        final TransitionInfo info = new TransitionInfoBuilder(TRANSIT_TO_FRONT)
                .addChange(getTask(TRANSIT_CHANGE, false /* registered */)).build();
        mUnregisteredTaskInfo.taskId = 222;

        mTaskViewTransitions.startAnimation(pending.mClaimed, info,
                mStartTransaction, mFinishTransaction, mFinishCallback);

        verify(mTaskViewTaskController, never()).prepareOpen(any(), any());
        final ArgumentCaptor<WindowContainerTransaction> wctCaptor =
                ArgumentCaptor.forClass(WindowContainerTransaction.class);
        verify(mFinishCallback).onTransitionFinished(wctCaptor.capture());
        assertWithMessage("No wct should have been created")
                .that(wctCaptor.getValue()).isNull();
    }

    private TransitionInfo.Change getTaskView(@TransitionInfo.TransitionMode int type) {
        return getTask(type, true);
    }

    private TransitionInfo.Change getTask(@TransitionInfo.TransitionMode int type,
            boolean registered) {
        final TransitionInfo.Change change = mock(TransitionInfo.Change.class);
        when(change.getLeash()).thenReturn(mTaskLeash);
        when(change.getTaskInfo()).thenReturn(registered ? mTaskInfo : mUnregisteredTaskInfo);
        when(change.getMode()).thenReturn(type);
        return change;
    }

    private TaskViewTransitions.PendingTransition setPendingTransaction(boolean visible,
            boolean opening) {
        final TaskViewRepository.TaskViewState state =
                mTaskViewRepository.byTaskView(mTaskViewTaskController);
        assertWithMessage("state can't be null here").that(state).isNotNull();
        state.mVisible = !visible;
        state.mBounds = BOUNDS;
        final TaskViewTransitions.PendingTransition pending;
        if (opening) {
            mTaskViewTransitions.startTaskView(mock(WindowContainerTransaction.class),
                    mTaskViewTaskController, mLaunchCookie);
            pending = mTaskViewTransitions.findPending(mTaskViewTaskController, TRANSIT_OPEN);
        } else {
            mTaskViewTransitions.setTaskViewVisible(mTaskViewTaskController, visible);
            pending = mTaskViewTransitions.findPending(
                    mTaskViewTaskController, visible ? TRANSIT_TO_FRONT : TRANSIT_TO_BACK);
        }
        assertWithMessage("pending can't be null").that(pending).isNotNull();
        return pending;
    }

    // assertions to verify TaskViewTransitions.prepareOpenAnimation is called
    private void assertOpenAnimationPreparation(@NonNull IBinder binder,
            @NonNull TaskViewTransitions.PendingTransition pending,
            @NonNull WindowContainerTransaction wct, boolean newTask, boolean shouldInterceptBack,
            boolean shouldCorpWindow) {
        final WindowContainerTransaction.Change change = wct.getChanges().get(binder);
        assertBoundsAndSurfaceTransactions(change, shouldCorpWindow);
        if (shouldInterceptBack) {
            assertWithMessage("wct.setInterceptBackPressedOnTaskRoot(token, true) should be called")
                    .that(change.getInterceptBackPressed()).isTrue();
        }

        assertWithMessage("no hierarchyOp set")
                .that(wct.getHierarchyOps()).isNotEmpty();
        assertWithMessage("isTrimmableFromRecents should be set to false")
                .that(wct.getHierarchyOps().getLast().isTrimmableFromRecents()).isFalse();
        verify(pending.mTaskView).notifyAppeared(eq(newTask));
    }

    private void assertBoundsAndSurfaceTransactions(WindowContainerTransaction.Change change,
            boolean shouldCorpWindow) {
        assertThat(change).isNotNull();
        assertWithMessage("wct.setBounds(token, bounds) should be called")
                .that(change.getConfiguration().windowConfiguration.getBounds()).isEqualTo(BOUNDS);

        verify(mStartTransaction).reparent(eq(mTaskLeash), eq(mSurfaceControl));
        verify(mStartTransaction).show(eq(mTaskLeash));
        verify(mFinishTransaction).reparent(eq(mTaskLeash), eq(mSurfaceControl));
        verify(mFinishTransaction).setPosition(eq(mTaskLeash), eq(0.0f), eq(0.0f));
        if (shouldCorpWindow) {
            verify(mFinishTransaction).setWindowCrop(eq(mTaskLeash), eq(100), eq(100));
        }
        verify(mTaskViewTaskController).applyCaptionInsetsIfNeeded();
    }
}
