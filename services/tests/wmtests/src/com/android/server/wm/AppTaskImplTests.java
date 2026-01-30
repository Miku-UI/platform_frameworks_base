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

package com.android.server.wm;

import static android.Manifest.permission.REPOSITION_SELF_WINDOWS;
import static android.app.TaskInfo.SELF_MOVABLE_ALLOWED;
import static android.app.TaskInfo.SELF_MOVABLE_DENIED;
import static android.app.TaskMoveRequestHandler.REMOTE_CALLBACK_RESULT_KEY;
import static android.app.TaskMoveRequestHandler.RESULT_APPROVED;
import static android.app.TaskMoveRequestHandler.RESULT_FAILED_BAD_BOUNDS;
import static android.app.TaskMoveRequestHandler.RESULT_FAILED_IMMOVABLE_TASK;
import static android.app.TaskMoveRequestHandler.RESULT_FAILED_NONEXISTENT_DISPLAY;
import static android.app.TaskMoveRequestHandler.RESULT_FAILED_NO_PERMISSIONS;
import static android.app.TaskMoveRequestHandler.RESULT_FAILED_UNABLE_TO_PLACE_TASK;
import static android.content.pm.PackageManager.PERMISSION_DENIED;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doAnswer;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.mock;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.mockitoSession;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.spy;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;
import static com.android.window.flags.Flags.FLAG_ENABLE_WINDOW_REPOSITIONING_API;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;

import android.graphics.Rect;
import android.os.Binder;
import android.os.Bundle;
import android.os.IRemoteCallback;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.annotations.Presubmit;

import androidx.test.filters.SmallTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

/**
 * Tests for the {@link AppTaskImpl} class.
 *
 * Build/Install/Run:
 * atest WmTests:AppTaskImplTests
 */
@SmallTest
@Presubmit
@RunWith(WindowTestRunner.class)
public class AppTaskImplTests extends WindowTestsBase {

    private final IRemoteCallback mMockCallback = mock(IRemoteCallback.class);
    private TransitionController mTransitionController;

    @Before
    public void setUp() {
        mTransitionController = spy(new TransitionController(mAtm));
        doReturn(true).when(mTransitionController).isShellTransitionsEnabled();
        doAnswer(invocation -> {
            final Transition capturedTransition = invocation.getArgument(0);
            if (capturedTransition.mTransactionPresentedListeners == null) {
                return true;
            }

            for (int i = 0; i < capturedTransition.mTransactionPresentedListeners.size(); i++) {
                capturedTransition.mTransactionPresentedListeners.get(i).run();
            }

            return true;
        }).when(mTransitionController).startCollectOrQueue(any(Transition.class), any());
        doReturn(mTransitionController).when(mAtm).getTransitionController();
    }

    /**
     * This actually also tests whether stubbings in this test setup are sufficient for a task move
     * request to be successfully validated which is a prerequisite for the tests below, that probe
     * what happens when a request-failing condition is introduced, to actually make sense.
     */
    @EnableFlags(FLAG_ENABLE_WINDOW_REPOSITIONING_API)
    @Test
    public void testMoveTaskTo_sendsApprovalToCallback() throws Exception {
        final Task task = getSelfMovableTask();
        final AppTaskImpl appTask = getAppTask(task.getRootTaskId());
        final DisplayContent dc = createNewDisplay();
        final Rect bounds = getValidBoundsForDisplay(dc);

        appTask.moveTaskTo(dc.mDisplayId, bounds, mMockCallback);

        verifyCallbackReceivedErrorCode(mMockCallback, RESULT_APPROVED);
    }

    @EnableFlags(FLAG_ENABLE_WINDOW_REPOSITIONING_API)
    @Test
    public void testMoveTaskTo_failsWhenNoPermission() throws Exception {
        final Task task = getSelfMovableTask();
        final AppTaskImpl appTask = getAppTask(task.getRootTaskId());
        final DisplayContent dc = createNewDisplay();
        final Rect bounds = getValidBoundsForDisplay(dc);

        final MockitoSession session =
                mockitoSession()
                        .strictness(Strictness.LENIENT)
                        .spyStatic(ActivityTaskManagerService.class)
                        .startMocking();
        try {
            doReturn(PERMISSION_DENIED)
                    .when(
                            () ->
                                    ActivityTaskManagerService.checkPermission(
                                            eq(REPOSITION_SELF_WINDOWS), anyInt(), anyInt()));

            appTask.moveTaskTo(dc.mDisplayId, bounds, mMockCallback);

            verifyCallbackReceivedErrorCode(mMockCallback, RESULT_FAILED_NO_PERMISSIONS);
        } finally {
            session.finishMocking();
        }
    }

    @EnableFlags(FLAG_ENABLE_WINDOW_REPOSITIONING_API)
    @Test
    public void testMoveTaskTo_failsWhenIncorrectDisplay() throws Exception {
        final Task task = getSelfMovableTask();
        final AppTaskImpl appTask = getAppTask(task.getRootTaskId());
        final int badDisplayId = -93;

        appTask.moveTaskTo(badDisplayId, new Rect(0, 0, 700, 700), mMockCallback);

        verifyCallbackReceivedErrorCode(mMockCallback, RESULT_FAILED_NONEXISTENT_DISPLAY);
    }

    @EnableFlags(FLAG_ENABLE_WINDOW_REPOSITIONING_API)
    @Test
    public void testMoveTaskTo_failsWhenCannotPlaceEntityOnDisplay() throws Exception {
        final Task task = getSelfMovableTask();
        final AppTaskImpl appTask = getAppTask(task.getRootTaskId());
        final DisplayContent dc = createNewDisplay();
        final Rect bounds = getValidBoundsForDisplay(dc);
        doReturn(false)
                .when(mSupervisor)
                .canPlaceEntityOnDisplay(eq(dc.mDisplayId), anyInt(), anyInt(), any(Task.class));

        appTask.moveTaskTo(dc.mDisplayId, bounds, mMockCallback);

        verifyCallbackReceivedErrorCode(mMockCallback, RESULT_FAILED_UNABLE_TO_PLACE_TASK);
    }

    @EnableFlags(FLAG_ENABLE_WINDOW_REPOSITIONING_API)
    @Test
    public void testMoveTaskTo_failsIffBoundsTooSmall() throws Exception {
        final Task task = getSelfMovableTask();
        final AppTaskImpl appTask = getAppTask(task.getRootTaskId());
        final DisplayContent dc = createNewDisplay();
        final Rect tooSmallBounds = new Rect(0, 0, 1, 1);

        appTask.moveTaskTo(dc.mDisplayId, tooSmallBounds, mMockCallback);

        verifyCallbackReceivedErrorCode(mMockCallback, RESULT_FAILED_BAD_BOUNDS);
    }

    @EnableFlags(FLAG_ENABLE_WINDOW_REPOSITIONING_API)
    @Test
    public void testMoveTaskTo_failsIffBoundsTooSmall2() throws Exception {
        final Task task = getSelfMovableTask();
        final AppTaskImpl appTask = getAppTask(task.getRootTaskId());
        final DisplayContent dc = new TestDisplayContent.Builder(mAtm, 1000, 1000).build();
        final float density = dc.getDisplayMetrics().density;
        final int minimalSizeDp = dc.mMinSizeOfResizeableTaskDp;
        final int tooSmallSizePx = (int) Math.ceil(density * minimalSizeDp) - 1;
        final Rect tooSmallBounds = new Rect(0, 0, tooSmallSizePx, tooSmallSizePx);

        appTask.moveTaskTo(dc.mDisplayId, tooSmallBounds, mMockCallback);

        verifyCallbackReceivedErrorCode(mMockCallback, RESULT_FAILED_BAD_BOUNDS);
    }

    @EnableFlags(FLAG_ENABLE_WINDOW_REPOSITIONING_API)
    @Test
    public void testMoveTaskTo_failsIffBoundsTooSmall3() throws Exception {
        final Task task = getSelfMovableTask();
        final AppTaskImpl appTask = getAppTask(task.getRootTaskId());
        final DisplayContent dc = new TestDisplayContent.Builder(mAtm, 1000, 1000).build();
        final float density = dc.getDisplayMetrics().density;
        final int minimalSizeDp = dc.mMinSizeOfResizeableTaskDp;
        final int bigEnoughSizePx = (int) Math.floor(density * minimalSizeDp) + 1;
        final Rect bigEnoughBounds = new Rect(0, 0, bigEnoughSizePx, bigEnoughSizePx);

        appTask.moveTaskTo(dc.mDisplayId, bigEnoughBounds, mMockCallback);

        verifyCallbackReceivedErrorCode(mMockCallback, RESULT_APPROVED);
    }

    @EnableFlags(FLAG_ENABLE_WINDOW_REPOSITIONING_API)
    @Test
    public void testMoveTaskTo_failsWhenTaskNotMovable() throws Exception {
        final Task task = getSelfMovableTask();
        final AppTaskImpl appTask = getAppTask(task.getRootTaskId());
        final DisplayContent dc = createNewDisplay();
        final Rect bounds = getValidBoundsForDisplay(dc);
        task.setSelfMovable(SELF_MOVABLE_DENIED);

        appTask.moveTaskTo(dc.mDisplayId, bounds, mMockCallback);

        verifyCallbackReceivedErrorCode(mMockCallback, RESULT_FAILED_IMMOVABLE_TASK);
    }

    private AppTaskImpl getAppTask(int taskId) {
        return new AppTaskImpl(mAtm, taskId, Binder.getCallingUid());
    }

    private Task getSelfMovableTask() {
        final Task task = new TaskBuilder(mSupervisor).setCreateActivity(true).build();
        task.setSelfMovable(SELF_MOVABLE_ALLOWED);
        return task;
    }

    private Rect getValidBoundsForDisplay(DisplayContent dc) {
        final float density = dc.getDisplayMetrics().density;
        final int minimalSizeDp = dc.mMinSizeOfResizeableTaskDp;
        final int sizePx = (int) Math.ceil(density * minimalSizeDp) + 1;
        return new Rect(0, 0, sizePx, sizePx);
    }

    private void verifyCallbackReceivedErrorCode(IRemoteCallback callback, int expectedErrorCode)
            throws Exception {
        final Bundle bundle = getBundlePassedToCallback(callback);
        final int receivedErrorCode = bundle.getInt(REMOTE_CALLBACK_RESULT_KEY);
        assertEquals(expectedErrorCode, receivedErrorCode);
    }

    private Bundle getBundlePassedToCallback(IRemoteCallback callback) throws Exception {
        ArgumentCaptor<Bundle> captor = ArgumentCaptor.forClass(Bundle.class);
        verify(callback).sendResult(captor.capture());
        return captor.getValue();
    }
}
