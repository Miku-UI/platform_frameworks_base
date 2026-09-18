/*
 * Copyright (C) 2020 The Android Open Source Project
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
import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;
import static android.app.WindowConfiguration.WINDOWING_MODE_MULTI_WINDOW;
import static android.window.WindowContainerTransaction.HierarchyOp.HIERARCHY_OP_TYPE_ADD_INSETS_FRAME_PROVIDER;

import static com.android.window.flags.Flags.FLAG_ENABLE_BUBBLE_ROOT_TASK;
import static com.android.wm.shell.Flags.FLAG_ENABLE_CREATE_ANY_BUBBLE;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.annotation.NonNull;
import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.app.PendingIntent;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Rect;
import android.graphics.Region;
import android.os.Looper;
import android.platform.test.annotations.UsesFlags;
import android.platform.test.flag.junit.FlagsParameterization;
import android.testing.TestableLooper;
import android.view.SurfaceControl;
import android.view.SurfaceHolder;
import android.view.ViewTreeObserver;
import android.window.WindowContainerToken;
import android.window.WindowContainerTransaction;

import androidx.test.filters.SmallTest;

import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.ShellTestCase;
import com.android.wm.shell.TestHandler;
import com.android.wm.shell.bubbles.BubbleHelper;
import com.android.wm.shell.common.HandlerExecutor;
import com.android.wm.shell.common.SyncTransactionQueue;
import com.android.wm.shell.common.SyncTransactionQueue.TransactionRunnable;
import com.android.wm.shell.shared.bubbles.BubbleFlagHelper;
import com.android.wm.shell.transition.Transitions;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;

import platform.test.runner.parameterized.ParameterizedAndroidJunit4;
import platform.test.runner.parameterized.Parameters;

import java.util.List;
import java.util.Optional;

/**
 * Verifies {@link TaskView} behavior and its interactions with {@link TaskViewTaskController}.
 *
 * 1. Verifies that {@link TaskView.Listener} callbacks are invoked correctly for task lifecycle
 *    events.
 * 2. Verifies that {@link TaskView} surface lifecycle events are handled correctly, and how they
 *    interact with the task's visibility.
 * 3. Verifies that user interactions like back press and obscured touch regions are handled
 *    correctly.
 *
 * Build/Install/Run:
 *  atest WMShellUnitTests:TaskViewTest
 */
@SmallTest
@RunWith(ParameterizedAndroidJunit4.class)
@TestableLooper.RunWithLooper(setAsMainLooper = true)
@UsesFlags({com.android.window.flags.Flags.class, com.android.wm.shell.Flags.class})
public class TaskViewTest extends ShellTestCase {

    @Parameters(name = "{0}")
    public static List<FlagsParameterization> getParams() {
        return FlagsParameterization.progressionOf(
                FLAG_ENABLE_CREATE_ANY_BUBBLE,
                FLAG_ENABLE_BUBBLE_ROOT_TASK);
    }

    @Mock
    private TaskView.Listener mViewListener;
    @Mock
    private WindowContainerToken mToken;
    @Mock
    private ShellTaskOrganizer mOrganizer;
    @Captor
    private ArgumentCaptor<WindowContainerTransaction> mWctCaptor;
    @Mock
    private HandlerExecutor mExecutor;
    @Mock
    private SyncTransactionQueue mSyncQueue;
    @Mock
    private Transitions mTransitions;
    @Mock
    private Looper mViewLooper;
    @Mock
    private TaskViewBase mTaskViewBase;
    @Mock
    private SurfaceHolder mSurfaceHolder;
    @Mock
    private BubbleHelper mBubbleHelper;

    private final SurfaceControl mLeash = new SurfaceControl.Builder().setName("test").build();
    private final ActivityManager.RunningTaskInfo mTaskInfo = new ActivityManager.RunningTaskInfo();
    private final TaskViewRepository mTaskViewRepository = new TaskViewRepository();

    private TestHandler mViewHandler;
    private TaskView mTaskView;
    private TaskViewTransitions mTaskViewTransitions;
    private TaskViewTaskController mTaskViewTaskController;

    public TaskViewTest(FlagsParameterization flags) {
        mSetFlagsRule.setFlagsParameterization(flags);
    }

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        doReturn(true).when(mViewLooper).isCurrentThread();
        mViewHandler = spy(new TestHandler(mViewLooper));

        mTaskInfo.parentTaskId = INVALID_TASK_ID;
        mTaskInfo.token = mToken;
        mTaskInfo.taskId = 314;
        mTaskInfo.taskDescription = mock(ActivityManager.TaskDescription.class);

        doAnswer((InvocationOnMock invocationOnMock) -> {
            final Runnable r = invocationOnMock.getArgument(0);
            r.run();
            return null;
        }).when(mExecutor).execute(any());

        when(mOrganizer.getExecutor()).thenReturn(mExecutor);

        doAnswer((InvocationOnMock invocationOnMock) -> {
            final TransactionRunnable r = invocationOnMock.getArgument(0);
            r.runWithTransaction(new SurfaceControl.Transaction());
            return null;
        }).when(mSyncQueue).runInSync(any());

        mTaskViewTransitions = spy(new TaskViewTransitions(mTransitions, mTaskViewRepository,
                mOrganizer, Optional.of(mBubbleHelper), /* taskViewRootTask= */ Optional.empty()));
        mTaskViewTaskController = new TaskViewTaskController(mContext, mOrganizer,
                mTaskViewTransitions, mSyncQueue);
        mTaskView = new TaskView(mContext, mTaskViewTransitions, mTaskViewTaskController,
                mViewHandler);
        mTaskView.setListener(mExecutor, mViewListener);
    }

    @After
    public void tearDown() {
        if (mTaskView != null) {
            mTaskView.release();
        }
    }

    @Test
    public void testSetPendingListener_throwsException() {
        final TaskView taskView = new TaskView(mContext, mTaskViewTransitions,
                new TaskViewTaskController(mContext, mOrganizer, mTaskViewTransitions, mSyncQueue));
        taskView.setListener(mExecutor, mViewListener);

        assertThrows("A TaskView listener can only be set once.",
                IllegalStateException.class,
                () -> taskView.setListener(mExecutor, mViewListener));
    }

    @Test
    public void testStartActivity() {
        final ActivityOptions options = ActivityOptions.makeBasic();
        final Rect bounds = new Rect(0, 0, 100, 100);

        mTaskView.startActivity(mock(PendingIntent.class), null, options, bounds);

        verify(mOrganizer).setPendingLaunchCookieListener(any(), eq(mTaskViewTaskController));
        assertThat(options.getLaunchWindowingMode()).isEqualTo(WINDOWING_MODE_MULTI_WINDOW);
    }

    @Test
    public void testOnNewTask_noSurface() {
        prepareOpenAnimation(true /* newTask */);

        verify(mViewListener).onTaskCreated(eq(mTaskInfo.taskId), any());
        verify(mViewListener, never()).onInitialized();
        assertThat(mTaskView.isSurfaceCreated()).isFalse();
        // If there's no surface the task should be made invisible
        verify(mViewListener).onTaskVisibilityChanged(eq(mTaskInfo.taskId), eq(false));
    }

    @Test
    public void testSurfaceCreated_noTask() {
        mTaskView.surfaceCreated(mSurfaceHolder);
        verify(mTaskViewTransitions, never()).setTaskViewVisible(any(), anyBoolean());

        verify(mViewListener).onInitialized();
        assertThat(mTaskView.isSurfaceCreated()).isTrue();
        // No task, no visibility change
        verify(mViewListener, never()).onTaskVisibilityChanged(anyInt(), anyBoolean());
    }

    @Test
    public void testOnNewTask_withSurface() {
        mTaskView.surfaceCreated(mSurfaceHolder);
        prepareOpenAnimation(true /* newTask */);

        verify(mViewListener).onTaskCreated(eq(mTaskInfo.taskId), any());
        verify(mViewListener, never()).onTaskVisibilityChanged(anyInt(), anyBoolean());
    }

    @Test
    public void testSurfaceCreated_withTask() {
        final WindowContainerTransaction wct = prepareOpenAnimation(true /* newTask */);
        mTaskView.surfaceCreated(mSurfaceHolder);

        verify(mViewListener).onInitialized();
        verify(mTaskViewTransitions).setTaskViewVisible(eq(mTaskViewTaskController), eq(true));
        assertThat(mTaskView.isSurfaceCreated()).isTrue();

        prepareOpenAnimation(wct, false /* newTask */);

        verify(mViewListener).onTaskVisibilityChanged(eq(mTaskInfo.taskId), eq(true));
    }

    @Test
    public void testSurfaceDestroyed_noTask() {
        mTaskView.surfaceCreated(mSurfaceHolder);
        mTaskView.surfaceDestroyed(mSurfaceHolder);

        verify(mViewListener, never()).onTaskVisibilityChanged(anyInt(), anyBoolean());
        assertThat(mTaskView.isSurfaceCreated()).isFalse();
    }

    @Test
    public void testSurfaceDestroyed_withTask() {
        prepareOpenAnimation(true /* newTask */);
        mTaskView.surfaceCreated(mSurfaceHolder);
        reset(mViewListener);
        mTaskView.surfaceDestroyed(mSurfaceHolder);

        verify(mTaskViewTransitions).setTaskViewVisible(eq(mTaskViewTaskController), eq(false));
        assertThat(mTaskView.isSurfaceCreated()).isFalse();

        mTaskViewTaskController.prepareHideAnimation(new SurfaceControl.Transaction());

        verify(mViewListener).onTaskVisibilityChanged(eq(mTaskInfo.taskId), eq(false));
    }

    @Test
    public void testOnReleased() {
        prepareOpenAnimation(true /* newTask */);
        mTaskView.surfaceCreated(mSurfaceHolder);
        mTaskView.release();

        verify(mOrganizer).removeListener(eq(mTaskViewTaskController));
        verify(mViewListener).onReleased();
        verify(mTaskViewTransitions).unregisterTaskView(eq(mTaskViewTaskController));
    }

    @Test
    public void testOnTaskVanished() {
        prepareOpenAnimation(true /* newTask */);
        final SurfaceControl taskLeash = mTaskViewTaskController.getTaskLeash();
        mTaskView.surfaceCreated(mSurfaceHolder);
        final SurfaceControl.Transaction tx = mock(SurfaceControl.Transaction.class);
        mTaskViewTaskController.prepareCloseAnimation(taskLeash, tx);

        verify(mViewListener).onTaskRemovalStarted(eq(mTaskInfo.taskId));
        assertThat(mTaskViewTaskController.getTaskLeash()).isNull();
        assertThat(taskLeash.isValid()).isFalse();
    }

    @Test
    public void testOnTaskVanished_withTaskInfoUpdate_notifiesTaskRemoval() {
        // Capture task info when onTaskRemovalStarted is triggered on the task view listener.
        final ActivityManager.RunningTaskInfo[] capturedTaskInfo =
                new ActivityManager.RunningTaskInfo[1];
        final int taskId = mTaskInfo.taskId;
        doAnswer(invocation -> {
            capturedTaskInfo[0] = mTaskView.getTaskInfo();
            return null;
        }).when(mViewListener).onTaskRemovalStarted(taskId);

        // Set up a mock TaskViewBase to verify notified task info.
        mTaskViewTaskController.setTaskViewBase(mTaskViewBase);

        // Prepare and trigger task opening animation with mTaskInfo.
        prepareOpenAnimation(true /* newTask */);
        mTaskView.surfaceCreated(mSurfaceHolder);

        // Simulate task info change with windowing mode update.
        final ActivityManager.RunningTaskInfo newTaskInfo = new ActivityManager.RunningTaskInfo();
        newTaskInfo.token = mTaskInfo.token;
        newTaskInfo.taskId = taskId;
        newTaskInfo.taskDescription = mTaskInfo.taskDescription;
        newTaskInfo.configuration.windowConfiguration.setWindowingMode(WINDOWING_MODE_FULLSCREEN);

        // Invoke onTaskVanished with updated task info.
        mTaskViewTaskController.onTaskVanished(newTaskInfo);

        verify(mViewListener).onTaskRemovalStarted(taskId);
        if (BubbleFlagHelper.enableCreateAnyBubble()) {
            // Verify TaskViewBase and listener updates with new task info.
            verify(mTaskViewBase).onTaskVanished(same(newTaskInfo));
            assertThat(capturedTaskInfo[0]).isSameInstanceAs(newTaskInfo);
        } else {
            // Verify TaskViewBase and listener updates with old task info.
            verify(mTaskViewBase).onTaskVanished(same(mTaskInfo));
            assertThat(capturedTaskInfo[0]).isSameInstanceAs(mTaskInfo);
        }
    }

    @Test
    public void testOnBackPressedOnTaskRoot() {
        prepareOpenAnimation(true /* newTask */);

        mTaskViewTaskController.onBackOnTaskRoot(mTaskInfo, true, false, false);

        verify(mViewListener).onBackPressedOnTaskRoot(eq(mTaskInfo.taskId));
    }

    @Test
    public void testSetOnBackPressedOnTaskRoot() {
        final WindowContainerTransaction wct = prepareOpenAnimation(true /* newTask */);

        assertThat(wct.getChanges().get(mToken.asBinder()).getInterceptBackPressed()).isTrue();
    }

    @Test
    public void testNoInterceptBackPressedOnChildTask() {
        mTaskInfo.parentTaskId = 3;  // Simulate bubble root task
        final WindowContainerTransaction wct = prepareOpenAnimation(true /* newTask */);

        assertThrows("Intercept back should not be set for child tasks",
                RuntimeException.class,
                () -> wct.getChanges().get(mToken.asBinder()).getInterceptBackPressed());
    }

    @Test
    public void testSetObscuredTouchRect() {
        mTaskView.setObscuredTouchRect(
                new Rect(/* left= */ 0, /* top= */ 10, /* right= */ 100, /* bottom= */ 120));
        final ViewTreeObserver.InternalInsetsInfo insetsInfo =
                new ViewTreeObserver.InternalInsetsInfo();
        mTaskView.onComputeInternalInsets(insetsInfo);

        assertThat(insetsInfo.touchableRegion.contains(0, 10)).isTrue();
        // Region doesn't contain the right/bottom edge.
        assertThat(insetsInfo.touchableRegion.contains(100 - 1, 120 - 1)).isTrue();

        mTaskView.setObscuredTouchRect(null);
        insetsInfo.touchableRegion.setEmpty();
        mTaskView.onComputeInternalInsets(insetsInfo);

        assertThat(insetsInfo.touchableRegion.contains(0, 10)).isFalse();
        assertThat(insetsInfo.touchableRegion.contains(100 - 1, 120 - 1)).isFalse();
    }

    @Test
    public void testSetObscuredTouchRegion() {
        final Region obscuredRegion = new Region(10, 10, 19, 19);
        obscuredRegion.union(new Rect(30, 30, 39, 39));

        mTaskView.setObscuredTouchRegion(obscuredRegion);
        final ViewTreeObserver.InternalInsetsInfo insetsInfo =
                new ViewTreeObserver.InternalInsetsInfo();
        mTaskView.onComputeInternalInsets(insetsInfo);

        assertThat(insetsInfo.touchableRegion.contains(10, 10)).isTrue();
        assertThat(insetsInfo.touchableRegion.contains(20, 20)).isFalse();
        assertThat(insetsInfo.touchableRegion.contains(30, 30)).isTrue();

        mTaskView.setObscuredTouchRegion(null);
        insetsInfo.touchableRegion.setEmpty();
        mTaskView.onComputeInternalInsets(insetsInfo);

        assertThat(insetsInfo.touchableRegion.contains(10, 10)).isFalse();
        assertThat(insetsInfo.touchableRegion.contains(20, 20)).isFalse();
        assertThat(insetsInfo.touchableRegion.contains(30, 30)).isFalse();
    }

    @Test
    public void testStartRootTask_setsBoundsAndVisibility() {
        final Rect bounds = new Rect(0, 0, 100, 100);
        when(mTaskViewBase.getCurrentBoundsOnScreen()).thenReturn(bounds);
        mTaskViewTaskController.setTaskViewBase(mTaskViewBase);

        // Surface created, but task not available so bounds / visibility isn't set
        mTaskView.surfaceCreated(mSurfaceHolder);
        final TaskViewRepository.TaskViewState taskViewState =
                mTaskViewTransitions.getRepository().byTaskView(mTaskViewTaskController);
        assertThat(taskViewState).isNotNull();
        assertThat(taskViewState.mVisible).isFalse();

        // Make the task available
        final WindowContainerTransaction wct = mock(WindowContainerTransaction.class);
        mTaskViewTransitions.startRootTask(mTaskViewTaskController, mTaskInfo, mLeash, wct);

        // Bounds got set
        verify(wct).setBounds(any(WindowContainerToken.class), eq(bounds));
        // Visibility & bounds state got set
        assertThat(taskViewState.mVisible).isTrue();
        assertThat(taskViewState.mBounds).isEqualTo(bounds);
    }

    @Test
    public void testPrepareOpenAnimation_copiesLeash() {
        prepareOpenAnimation(true /* newTask */);

        assertThat(mTaskViewTaskController.getTaskLeash()).isNotEqualTo(mLeash);
    }

    @Test
    public void testTaskViewPrepareOpenAnimationSetsBoundsAndVisibility() {
        final Rect bounds = new Rect(0, 0, 100, 100);
        when(mTaskViewBase.getCurrentBoundsOnScreen()).thenReturn(bounds);
        mTaskViewTaskController.setTaskViewBase(mTaskViewBase);

        // Surface created, but task not available so bounds / visibility isn't set
        mTaskView.surfaceCreated(mSurfaceHolder);
        final TaskViewRepository.TaskViewState taskViewState =
                mTaskViewTransitions.getRepository().byTaskView(mTaskViewTaskController);
        assertThat(taskViewState).isNotNull();
        assertThat(taskViewState.mVisible).isFalse();

        // Make the task available / start prepareOpen
        final WindowContainerTransaction wct = mock(WindowContainerTransaction.class);
        prepareOpenAnimation(wct, true /* newTask */);

        // Bounds got set
        verify(wct).setBounds(any(WindowContainerToken.class), eq(bounds));
        // Visibility & bounds state got set
        assertThat(taskViewState.mVisible).isTrue();
        assertThat(taskViewState.mBounds).isEqualTo(bounds);
    }

    @Test
    public void testTaskViewPrepareOpenAnimationSetsVisibilityFalse() {
        final Rect bounds = new Rect(0, 0, 100, 100);
        when(mTaskViewBase.getCurrentBoundsOnScreen()).thenReturn(bounds);
        mTaskViewTaskController.setTaskViewBase(mTaskViewBase);

        // Task is available, but the surface was never created
        final WindowContainerTransaction wct = mock(WindowContainerTransaction.class);
        prepareOpenAnimation(wct, true /* newTask */);

        // Bounds do not get set as there is no surface
        verify(wct, never()).setBounds(any(WindowContainerToken.class), any());
        // Visibility is set to false, bounds aren't set
        final TaskViewRepository.TaskViewState taskViewState =
                mTaskViewTransitions.getRepository().byTaskView(mTaskViewTaskController);
        assertThat(taskViewState.mVisible).isFalse();
        assertThat(taskViewState.mBounds.isEmpty()).isTrue();
    }

    @Test
    public void testRemoveTaskView_noTask() {
        mTaskView.removeTask();
        assertThat(mTaskViewTransitions.hasPending()).isFalse();
    }

    @Test
    public void testRemoveTaskView() {
        mTaskView.surfaceCreated(mSurfaceHolder);
        prepareOpenAnimation(true /* newTask */);

        verify(mViewListener).onTaskCreated(eq(mTaskInfo.taskId), any());

        mTaskView.removeTask();
        verify(mTaskViewTransitions).removeTaskView(eq(mTaskViewTaskController), any());
    }

    @Test
    public void testUnregisterTask() {
        mTaskView.unregisterTask();

        verify(mTaskViewTransitions).unregisterTaskView(mTaskViewTaskController);
    }

    @Test
    public void testOnTaskAppearedWithTaskNotFound() {
        mTaskViewTaskController.setTaskNotFound();
        mTaskViewTaskController.onTaskAppeared(mTaskInfo, mLeash);

        assertThat(mTaskViewTaskController.getTaskInfo()).isNull();
        verify(mTaskViewTransitions).removeTaskView(eq(mTaskViewTaskController), any());
    }

    @Test
    public void testOnTaskAppeared_withoutTaskNotFound() {
        mTaskViewTaskController.onTaskAppeared(mTaskInfo, mLeash);

        assertThat(mTaskViewTaskController.getPendingInfo()).isEqualTo(mTaskInfo);
        verify(mTaskViewTransitions, never()).removeTaskView(any(), any());
    }

    @Test
    public void testSetCaptionInsets_noTaskInitially() {
        final Rect insets = new Rect(0, 400, 0, 0);
        mTaskView.setCaptionInsets(Insets.of(insets));
        mTaskView.onComputeInternalInsets(new ViewTreeObserver.InternalInsetsInfo());

        verify(mOrganizer, never()).applyTransaction(any());

        mTaskView.surfaceCreated(mSurfaceHolder);
        reset(mOrganizer);
        prepareOpenAnimation(true /* newTask */);
        mTaskView.onComputeInternalInsets(new ViewTreeObserver.InternalInsetsInfo());

        verify(mOrganizer).applyTransaction(mWctCaptor.capture());
        assertThat(mWctCaptor.getValue().getHierarchyOps().stream().anyMatch(hop ->
                hop.getType() == HIERARCHY_OP_TYPE_ADD_INSETS_FRAME_PROVIDER)).isTrue();
    }

    @Test
    public void testSetCaptionInsets_withTask() {
        mTaskView.surfaceCreated(mSurfaceHolder);
        prepareOpenAnimation(true /* newTask */);
        reset(mOrganizer);

        final Rect insets = new Rect(0, 400, 0, 0);
        mTaskView.setCaptionInsets(Insets.of(insets));
        mTaskView.onComputeInternalInsets(new ViewTreeObserver.InternalInsetsInfo());

        verify(mOrganizer).applyTransaction(mWctCaptor.capture());
        assertThat(mWctCaptor.getValue().getHierarchyOps().stream().anyMatch(hop ->
                hop.getType() == HIERARCHY_OP_TYPE_ADD_INSETS_FRAME_PROVIDER)).isTrue();
    }

    @Test
    public void testReleaseInOnTaskRemoval_noNPE() {
        mTaskViewTaskController = spy(new TaskViewTaskController(mContext, mOrganizer,
                mTaskViewTransitions, mSyncQueue));
        mTaskView = new TaskView(mContext, mTaskViewTransitions, mTaskViewTaskController);
        mTaskView.setListener(mExecutor, new TaskView.Listener() {
            @Override
            public void onTaskRemovalStarted(int taskId) {
                mTaskView.release();
            }
        });

        prepareOpenAnimation(true /* newTask */);
        mTaskView.surfaceCreated(mSurfaceHolder);

        assertThat(mTaskViewTaskController.getTaskInfo()).isEqualTo(mTaskInfo);

        final SurfaceControl.Transaction tx = mock(SurfaceControl.Transaction.class);
        mTaskViewTaskController.prepareCloseAnimation(mTaskView.getSurfaceControl(), tx);

        assertThat(mTaskViewTaskController.getTaskInfo()).isNull();
    }

    @Test
    public void testOnTaskInfoChangedOnSameUiThread() {
        mTaskViewTaskController.onTaskInfoChanged(mTaskInfo);
        verify(mViewHandler, never()).post(any());
    }

    @Test
    public void testOnTaskInfoChangedOnDifferentUiThread() {
        doReturn(false).when(mViewLooper).isCurrentThread();
        mTaskViewTaskController.onTaskInfoChanged(mTaskInfo);
        verify(mViewHandler).post(any());
    }

    @Test
    public void testSetResizeBgOnSameUiThread_expectUsesTransaction() {
        final SurfaceControl.Transaction tx = mock(SurfaceControl.Transaction.class);
        mTaskView = spy(mTaskView);

        mTaskView.setResizeBgColor(tx, Color.BLUE);

        verify(mViewHandler, never()).post(any());
        verify(mTaskView, never()).setResizeBackgroundColor(eq(Color.BLUE));
        verify(mTaskView).setResizeBackgroundColor(eq(tx), eq(Color.BLUE));
    }

    @Test
    public void testSetResizeBgOnDifferentUiThread_expectDoesNotUseTransaction() {
        doReturn(false).when(mViewLooper).isCurrentThread();
        final SurfaceControl.Transaction tx = mock(SurfaceControl.Transaction.class);
        mTaskView = spy(mTaskView);

        mTaskView.setResizeBgColor(tx, Color.BLUE);

        verify(mViewHandler).post(any());
        verify(mTaskView).setResizeBackgroundColor(eq(Color.BLUE));
    }

    @Test
    public void testOnAppeared_setsTrimmableTask() {
        final WindowContainerTransaction wct = prepareOpenAnimation(true /* newTask */);
        assertThat(wct.getHierarchyOps().get(0).isTrimmableFromRecents()).isFalse();
    }

    @Test
    public void testMoveToFullscreen_callsTaskRemovalStarted() {
        prepareOpenAnimation(true /* newTask */);
        mTaskView.surfaceCreated(mSurfaceHolder);

        mTaskViewTransitions.moveTaskViewToFullscreen(mTaskViewTaskController);

        verify(mViewListener).onTaskRemovalStarted(eq(mTaskInfo.taskId));
    }

    @Test
    public void prepareCloseAnimation_taskViewWithZeroAlpha_hidesSurface() {
        prepareOpenAnimation(true /* newTask */);
        final SurfaceControl taskLeash = mTaskViewTaskController.getTaskLeash();
        mTaskView.surfaceCreated(mSurfaceHolder);
        mTaskView.setAlpha(0);
        final SurfaceControl.Transaction tx = mock(SurfaceControl.Transaction.class);
        mTaskViewTaskController.prepareCloseAnimation(taskLeash, tx);

        verify(tx).setAlpha(taskLeash, 0);
    }

    @Test
    public void onLocationChanged_withBounds_updatesTaskViewState() {
        // Simulate a task being present in the TaskView.
        mTaskView.surfaceCreated(mSurfaceHolder);
        prepareOpenAnimation(true /* newTask */);

        // Create a specific Rect for the new bounds.
        final Rect newBounds = new Rect(10, 20, 130, 240);

        // Call the method under test.
        mTaskView.onLocationChanged(newBounds);

        // Verify that the state in the repository has been updated with the new bounds.
        final TaskViewRepository.TaskViewState taskViewState =
                mTaskViewTransitions.getRepository().byTaskView(mTaskViewTaskController);
        assertThat(taskViewState.mBounds).isEqualTo(newBounds);
    }

    @NonNull
    private WindowContainerTransaction prepareOpenAnimation(boolean newTask) {
        final WindowContainerTransaction wct = new WindowContainerTransaction();
        prepareOpenAnimation(wct, newTask);
        return wct;
    }

    private void prepareOpenAnimation(@NonNull WindowContainerTransaction wct, boolean newTask) {
        mTaskViewTransitions.prepareOpenAnimation(
                mTaskViewTaskController,
                newTask,
                new SurfaceControl.Transaction(),
                new SurfaceControl.Transaction(),
                mTaskInfo,
                mLeash,
                wct);
    }
}
