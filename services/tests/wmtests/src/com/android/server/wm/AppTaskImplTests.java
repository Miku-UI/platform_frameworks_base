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
import static android.app.ActivityManager.AppTask.WINDOWING_LAYER_PINNED;
import static android.app.AppOpsManager.MODE_ALLOWED;
import static android.app.AppOpsManager.MODE_ERRORED;
import static android.app.AppOpsManager.OP_PICTURE_IN_PICTURE;
import static android.app.TaskInfo.SELF_MOVABLE_ALLOWED;
import static android.app.TaskInfo.SELF_MOVABLE_DENIED;
import static android.app.TaskMoveRequestHandler.REMOTE_CALLBACK_RESULT_KEY;
import static android.app.TaskMoveRequestHandler.RESULT_APPROVED;
import static android.app.TaskMoveRequestHandler.RESULT_FAILED_BAD_BOUNDS;
import static android.app.TaskMoveRequestHandler.RESULT_FAILED_BAL_POLICY_VIOLATION;
import static android.app.TaskMoveRequestHandler.RESULT_FAILED_IMMOVABLE_TASK;
import static android.app.TaskMoveRequestHandler.RESULT_FAILED_NONEXISTENT_DISPLAY;
import static android.app.TaskMoveRequestHandler.RESULT_FAILED_NO_PERMISSIONS;
import static android.app.TaskMoveRequestHandler.RESULT_FAILED_UNABLE_TO_PLACE_TASK;
import static android.content.pm.PackageManager.PERMISSION_DENIED;
import static android.view.WindowManager.TRANSIT_OPEN;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doNothing;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doThrow;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.mock;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.mockitoSession;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.spy;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;
import static com.android.server.wm.AppTaskImpl.WINDOWING_LAYER_CALLBACK_INVOKE_TIMEOUT_MS;
import static com.android.window.flags.Flags.FLAG_ENABLE_INTERACTIVE_PICTURE_IN_PICTURE;
import static com.android.server.wm.BackgroundActivityStartController.BalVerdict;
import static com.android.window.flags.Flags.FLAG_ENABLE_WINDOW_REPOSITIONING_API;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.testng.AssertJUnit.assertNotNull;

import android.app.ActivityManager;
import android.app.AppOpsManager;
import android.app.TaskWindowingLayerRequestHandler;
import android.graphics.Rect;
import android.os.Binder;
import android.os.Bundle;
import android.os.IRemoteCallback;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.annotations.Presubmit;
import android.window.TransitionRequestInfo.WindowingLayerChange;

import androidx.test.filters.SmallTest;

import com.android.server.testutils.OffsettableClock;
import com.android.server.testutils.TestHandler;

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


    private static final String PACKAGE_NAME = "my.package.name";
    private final IRemoteCallback mMockCallback = mock(IRemoteCallback.class);
    private TransitionController mTransitionController;
    private TestTransitionPlayer mTestTransitionPlayer;
    private AppOpsManager mAppOpsManager;
    private OffsettableClock mClock;
    private TestHandler mHandler;
    private final WindowProcessController mMockWPC = mock(WindowProcessController.class);
    private final BackgroundActivityStartController mMockBalController =
                mock(BackgroundActivityStartController.class);

    @Before
    public void setUp() {
        mTransitionController = spy(mAtm.getTransitionController());
        mTransitionController.setSyncEngine(createTestBLASTSyncEngine());
        mTestTransitionPlayer = registerTestTransitionPlayer();
        doReturn(mTransitionController).when(mAtm).getTransitionController();

        mAppOpsManager = mAtm.getAppOpsManager();
        mockPipAppOppToReturn(MODE_ALLOWED);
        mClock = new OffsettableClock.Stopped();
        mHandler = new TestHandler(null, mClock);

        doReturn(mMockWPC).when(mAtm).getProcessController(anyInt(), anyInt());
        doReturn(mMockBalController).when(mSupervisor).getBackgroundActivityLaunchController();
        doReturn(BalVerdict.ALLOW_BY_DEFAULT).when(mMockBalController).checkBackgroundActivityStart(
                anyInt(), anyInt(), anyString(), anyInt(), anyInt(), any(), any(), anyBoolean(),
                any(), any(), any());
        doReturn(false).when(mAtm).isBackgroundActivityStartsEnabled();
        doNothing().when(mAtm).assertPackageMatchesCallingUid(PACKAGE_NAME);
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

        appTask.moveTaskTo(dc.mDisplayId, bounds, mMockCallback, PACKAGE_NAME);

        mTestTransitionPlayer.mLastTransit.invokePresentedListenersForTest();
        verifyCallbackReceivedTaskMoveErrorCode(mMockCallback, RESULT_APPROVED);
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

            appTask.moveTaskTo(dc.mDisplayId, bounds, mMockCallback, PACKAGE_NAME);

            verifyCallbackReceivedTaskMoveErrorCode(mMockCallback, RESULT_FAILED_NO_PERMISSIONS);
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

        appTask.moveTaskTo(badDisplayId, new Rect(0, 0, 700, 700), mMockCallback, PACKAGE_NAME);

        verifyCallbackReceivedTaskMoveErrorCode(mMockCallback, RESULT_FAILED_NONEXISTENT_DISPLAY);
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

        appTask.moveTaskTo(dc.mDisplayId, bounds, mMockCallback, PACKAGE_NAME);

        verifyCallbackReceivedTaskMoveErrorCode(mMockCallback, RESULT_FAILED_UNABLE_TO_PLACE_TASK);
    }

    @EnableFlags(FLAG_ENABLE_WINDOW_REPOSITIONING_API)
    @Test
    public void testMoveTaskTo_failsIffBoundsTooSmall() throws Exception {
        final Task task = getSelfMovableTask();
        final AppTaskImpl appTask = getAppTask(task.getRootTaskId());
        final DisplayContent dc = createNewDisplay();
        final Rect tooSmallBounds = new Rect(0, 0, 1, 1);

        appTask.moveTaskTo(dc.mDisplayId, tooSmallBounds, mMockCallback, PACKAGE_NAME);

        verifyCallbackReceivedTaskMoveErrorCode(mMockCallback, RESULT_FAILED_BAD_BOUNDS);
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

        appTask.moveTaskTo(dc.mDisplayId, tooSmallBounds, mMockCallback, PACKAGE_NAME);

        verifyCallbackReceivedTaskMoveErrorCode(mMockCallback, RESULT_FAILED_BAD_BOUNDS);
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

        appTask.moveTaskTo(dc.mDisplayId, bigEnoughBounds, mMockCallback, PACKAGE_NAME);

        mTestTransitionPlayer.mLastTransit.invokePresentedListenersForTest();
        verifyCallbackReceivedTaskMoveErrorCode(mMockCallback, RESULT_APPROVED);
    }

    @EnableFlags(FLAG_ENABLE_WINDOW_REPOSITIONING_API)
    @Test
    public void testMoveTaskTo_failsWhenTaskNotMovable() throws Exception {
        final Task task = getSelfMovableTask();
        final AppTaskImpl appTask = getAppTask(task.getRootTaskId());
        final DisplayContent dc = createNewDisplay();
        final Rect bounds = getValidBoundsForDisplay(dc);
        task.setSelfMovable(SELF_MOVABLE_DENIED);

        appTask.moveTaskTo(dc.mDisplayId, bounds, mMockCallback, PACKAGE_NAME);

        verifyCallbackReceivedTaskMoveErrorCode(mMockCallback, RESULT_FAILED_IMMOVABLE_TASK);
    }

    @DisableFlags(FLAG_ENABLE_INTERACTIVE_PICTURE_IN_PICTURE)
    @Test
    public void testRequestWindowingLayer_failsWhenFeatureDisabled() throws Exception {
        final Task task = getSampleTask();
        final AppTaskImpl appTask = getAppTask(task.getRootTaskId());

        appTask.requestWindowingLayer(WINDOWING_LAYER_PINNED, mMockCallback);

        verifyCallbackReceivedWindowingLayerErrorCode(mMockCallback,
                TaskWindowingLayerRequestHandler.RESULT_FAILED_BAD_STATE);
        verify(mTransitionController, never()).startCollectOrQueue(any(), any());
    }

    @EnableFlags(FLAG_ENABLE_INTERACTIVE_PICTURE_IN_PICTURE)
    @Test
    public void testRequestWindowingLayer_requestsTransitionWithCorrectParameters() {
        final Task task = getSampleTask();
        final AppTaskImpl appTask = getAppTask(task.getRootTaskId());
        final int requestedLayer = WINDOWING_LAYER_PINNED;

        appTask.requestWindowingLayer(requestedLayer, mMockCallback);
        completeRequestedTransition();

        final WindowingLayerChange change =
                mTestTransitionPlayer.mLastRequest.getWindowingLayerChange();
        assertNotNull(change);
        assertEquals(requestedLayer, change.getWindowingLayer());
        assertNotNull(change.getRemoteCallback());
    }

    @EnableFlags(FLAG_ENABLE_INTERACTIVE_PICTURE_IN_PICTURE)
    @Test
    public void testRequestWindowingLayer_deniesPinnedRequestWithoutPipAppOpAllowed()
            throws Exception {
        final Task task = getSampleTask();
        final AppTaskImpl appTask = getAppTask(task.getRootTaskId());
        mockPipAppOppToReturn(MODE_ERRORED);

        appTask.requestWindowingLayer(WINDOWING_LAYER_PINNED, mMockCallback);

        verifyCallbackReceivedWindowingLayerErrorCode(mMockCallback,
                TaskWindowingLayerRequestHandler.RESULT_FAILED_INSUFFICIENT_PERMISSIONS);
        verify(mTransitionController, never()).startCollectOrQueue(any(), any());
    }

    @EnableFlags(FLAG_ENABLE_INTERACTIVE_PICTURE_IN_PICTURE)
    @Test
    public void testRequestWindowingLayer_revalidatesPermissionIfDeferred()
            throws Exception {
        final Task task = getSampleTask();
        final AppTaskImpl appTask = getAppTask(task.getRootTaskId());
        // queue windowing layer transition after sample one
        Transition sampleTransition = createAndCollectSampleTransition();
        appTask.requestWindowingLayer(WINDOWING_LAYER_PINNED, mMockCallback);
        waitUntilHandlersIdle();

        verify(mMockCallback, never()).sendResult(any()); // windowing request was not rejected yet
        mockPipAppOppToReturn(MODE_ERRORED); // change permission to deny the request
        startAndFinish(sampleTransition); // proceed with sample transition to collect next one

        verifyCallbackReceivedWindowingLayerErrorCode(mMockCallback,
                TaskWindowingLayerRequestHandler.RESULT_FAILED_INSUFFICIENT_PERMISSIONS);
        verify(mTransitionController, never()).requestStartWindowingLayerTransition(any(), any(),
                any());
    }

    @EnableFlags(FLAG_ENABLE_INTERACTIVE_PICTURE_IN_PICTURE)
    @Test
    public void testRequestWindowingLayer_fallbacksWhenTransitionEndsWithoutResultInTime()
            throws Exception {
        final Task task = getSampleTask();
        final AppTaskImpl appTask = getAppTask(task.getRootTaskId());

        appTask.requestWindowingLayer(WINDOWING_LAYER_PINNED, mMockCallback);

        verify(mMockCallback, never()).sendResult(any());
        completeRequestedTransition();
        advanceTimeBy(WINDOWING_LAYER_CALLBACK_INVOKE_TIMEOUT_MS + 1);
        verifyCallbackReceivedWindowingLayerErrorCode(mMockCallback,
                TaskWindowingLayerRequestHandler.RESULT_FAILED_BAD_STATE);
    }

    @EnableFlags(FLAG_ENABLE_INTERACTIVE_PICTURE_IN_PICTURE)
    @Test
    public void testRequestWindowingLayer_skipsFallbackIfCallbackAlreadyInvoked()
            throws Exception {
        final Task task = getSampleTask();
        final AppTaskImpl appTask = getAppTask(task.getRootTaskId());
        final Bundle sampleBundle = new Bundle();

        appTask.requestWindowingLayer(WINDOWING_LAYER_PINNED, mMockCallback);
        final WindowingLayerChange change =
                mTestTransitionPlayer.mLastRequest.getWindowingLayerChange();
        assertNotNull(change);
        change.getRemoteCallback().sendResult(sampleBundle); // simulate callback

        completeRequestedTransition();
        advanceTimeBy(WINDOWING_LAYER_CALLBACK_INVOKE_TIMEOUT_MS + 1);
        verify(mMockCallback).sendResult(eq(sampleBundle)); // only once, with sampleBundle
    }

    @EnableFlags(FLAG_ENABLE_INTERACTIVE_PICTURE_IN_PICTURE)
    @Test
    public void testRequestWindowingLayer_rejectsPinnedRequestForNonFocusedTask()
            throws Exception {
        final Task task = getSampleTask();
        final Task focusedTask = getSampleTaskBuilder().setOnTop(true).build();
        final AppTaskImpl appTask = getAppTask(task.getRootTaskId());
        final Bundle sampleBundle = new Bundle();

        appTask.requestWindowingLayer(WINDOWING_LAYER_PINNED, mMockCallback);

        verifyCallbackReceivedWindowingLayerErrorCode(mMockCallback,
                TaskWindowingLayerRequestHandler.RESULT_FAILED_BAD_STATE);
        verify(mTransitionController, never()).startCollectOrQueue(any(), any());
    }

    @EnableFlags(FLAG_ENABLE_WINDOW_REPOSITIONING_API)
    @Test
    public void testMoveTaskTo_failsWhenBalBlocks() throws Exception {
        final Task task = getSelfMovableTask();
        final AppTaskImpl appTask = getAppTask(task.getRootTaskId());
        final DisplayContent dc = createNewDisplay();
        final Rect bounds = getValidBoundsForDisplay(dc);
        doReturn(BalVerdict.BLOCK).when(mMockBalController).checkBackgroundActivityStart(
                anyInt(), anyInt(), anyString(), anyInt(), anyInt(), any(), any(), anyBoolean(),
                any(), any(), any());

        appTask.moveTaskTo(dc.mDisplayId, bounds, mMockCallback, PACKAGE_NAME);

        verifyCallbackReceivedTaskMoveErrorCode(mMockCallback, RESULT_FAILED_BAL_POLICY_VIOLATION);
    }

    @EnableFlags(FLAG_ENABLE_WINDOW_REPOSITIONING_API)
    @Test
    public void testMoveTaskTo_throwsWhenCallingPackageDoesNotMatchCallingUid() throws Exception {
        final Task task = getSelfMovableTask();
        final AppTaskImpl appTask = getAppTask(task.getRootTaskId());
        final DisplayContent dc = createNewDisplay();
        final Rect bounds = getValidBoundsForDisplay(dc);
        doThrow(SecurityException.class).when(mAtm).assertPackageMatchesCallingUid(PACKAGE_NAME);

        assertThrows(
                SecurityException.class,
                () -> appTask.moveTaskTo(dc.mDisplayId, bounds, mMockCallback, PACKAGE_NAME));
    }

    @Test
    public void testGetTaskInfo_returnsNullForNonExistentTask() {
        // Use a task ID that is guaranteed not to exist.
        final int nonExistentTaskId = 9999;
        final AppTaskImpl appTask = getAppTask(nonExistentTaskId);

        // Calling getTaskInfo for a non-existent task should return null.
        final ActivityManager.RecentTaskInfo taskInfo = appTask.getTaskInfo();

        // Verify that the result is null, as per the updated implementation,
        // instead of throwing an exception.
        assertNull(taskInfo);
    }

    private AppTaskImpl getAppTask(int taskId) {
        return new AppTaskImpl(mAtm, taskId, Binder.getCallingUid(), mHandler);
    }

    private Task getSelfMovableTask() {
        final Task task = getSampleTask();
        task.setSelfMovable(SELF_MOVABLE_ALLOWED);
        return task;
    }

    private Task getSampleTask() {
        return getSampleTaskBuilder().build();
    }

    private TaskBuilder getSampleTaskBuilder() {
        return new TaskBuilder(mSupervisor).setCreateActivity(true);
    }

    private Rect getValidBoundsForDisplay(DisplayContent dc) {
        final float density = dc.getDisplayMetrics().density;
        final int minimalSizeDp = dc.mMinSizeOfResizeableTaskDp;
        final int sizePx = (int) Math.ceil(density * minimalSizeDp) + 1;
        return new Rect(0, 0, sizePx, sizePx);
    }

    private void verifyCallbackReceivedTaskMoveErrorCode(IRemoteCallback callback,
            int expectedErrorCode)
            throws Exception {
        // use TaskMoveRequestHandler#REMOTE_CALLBACK_RESULT_KEY key to get bundle value
        verifyCallbackReceived(callback, REMOTE_CALLBACK_RESULT_KEY, expectedErrorCode);
    }

    private void verifyCallbackReceivedWindowingLayerErrorCode(IRemoteCallback callback,
            int expectedErrorCode)
            throws Exception {
        // use TaskWindowingLayerRequestHandler#REMOTE_CALLBACK_RESULT_KEY key to get bundle value
        verifyCallbackReceived(callback,
                TaskWindowingLayerRequestHandler.REMOTE_CALLBACK_RESULT_KEY, expectedErrorCode);
    }

    private void verifyCallbackReceived(IRemoteCallback callback, String dataKey, int expectedValue)
            throws Exception {
        final Bundle bundle = getBundlePassedToCallback(callback);
        final int receivedValue = bundle.getInt(dataKey);
        assertEquals(expectedValue, receivedValue);
    }

    private Bundle getBundlePassedToCallback(IRemoteCallback callback) throws Exception {
        ArgumentCaptor<Bundle> captor = ArgumentCaptor.forClass(Bundle.class);
        verify(callback).sendResult(captor.capture());
        return captor.getValue();
    }

    private void completeRequestedTransition() {
        mTestTransitionPlayer.startTransition();
        waitUntilHandlersIdle();
        mTestTransitionPlayer.mLastTransit.invokePresentedListenersForTest();
        mTestTransitionPlayer.finish();
    }

    private void mockPipAppOppToReturn(@AppOpsManager.Mode int mode) {
        doReturn(mode).when(mAppOpsManager).checkOpNoThrow(eq(OP_PICTURE_IN_PICTURE),
                anyInt(), any());
    }

    private Transition createAndCollectSampleTransition() {
        Transition transit = new Transition(TRANSIT_OPEN, 0 /* flags */, mTransitionController,
                mTransitionController.mSyncEngine);
        mTransitionController.startCollectOrQueue(transit, (deferred) -> {});
        return transit;
    }

    private void startAndFinish(Transition transit) {
        transit.start();
        transit.setAllReady();
        mTransitionController.mSyncEngine.tryFinishForTest(transit.getSyncId());
        waitUntilHandlersIdle();
    }

    private void advanceTimeBy(int timeMs) {
        mClock.fastForward(timeMs);
        mHandler.timeAdvance();
    }
}
