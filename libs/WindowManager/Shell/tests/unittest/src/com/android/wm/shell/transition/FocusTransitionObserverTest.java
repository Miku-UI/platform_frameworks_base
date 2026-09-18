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

import static android.view.Display.DEFAULT_DISPLAY;
import static android.view.WindowManager.TRANSIT_CHANGE;
import static android.view.WindowManager.TRANSIT_OPEN;
import static android.view.WindowManager.TRANSIT_TO_FRONT;
import static android.window.TransitionInfo.FLAG_IS_DISPLAY;
import static android.window.TransitionInfo.FLAG_MOVED_TO_TOP;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import android.app.ActivityManager.RunningTaskInfo;
import android.os.RemoteException;
import android.platform.test.annotations.EnableFlags;
import android.view.SurfaceControl;
import android.window.TransitionInfo;
import android.window.TransitionInfo.TransitionMode;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.window.flags.Flags;
import com.android.wm.shell.ShellTestCase;
import com.android.wm.shell.TestShellExecutor;
import com.android.wm.shell.shared.FocusTransitionListener;
import com.android.wm.shell.sysui.ShellCommandHandler;
import com.android.wm.shell.sysui.ShellInit;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatcher;

import java.util.ArrayList;
import java.util.List;

/**
 * Tests for the focus transition observer.
 *
 * <p>Build/Install/Run:
 * atest WMShellUnitTests:com.android.wm.shell.transition.FocusTransitionObserverTest
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class FocusTransitionObserverTest extends ShellTestCase {

    static final int SECONDARY_DISPLAY_ID = 1;

    private FocusTransitionListener mListener;
    private final TestShellExecutor mShellExecutor = new TestShellExecutor();
    private ShellInit mShellInit;
    private ShellCommandHandler mShellCommandHandler;
    private FocusTransitionObserver mFocusTransitionObserver;

    @Before
    public void setUp() {
        mListener = mock(FocusTransitionListener.class);
        mShellInit = spy(new ShellInit(mShellExecutor));
        mShellCommandHandler = mock(ShellCommandHandler.class);
        mFocusTransitionObserver = new FocusTransitionObserver(mShellInit, mShellCommandHandler);
        mFocusTransitionObserver.setLocalFocusTransitionListener(mListener, mShellExecutor);
        mShellInit.init();
        mShellExecutor.flushAll();
        clearInvocations(mListener);
    }

    @Test
    public void testBasicTaskAndDisplayFocusSwitch() throws RemoteException {
        final SurfaceControl.Transaction tx = mock(SurfaceControl.Transaction.class);

        // First, open a task on the default display.
        TransitionInfo info = mock(TransitionInfo.class);
        final List<TransitionInfo.Change> changes = new ArrayList<>();
        TransitionInfo.Change change1 = setupTaskChange(changes, 1 /* taskId */, TRANSIT_OPEN,
                DEFAULT_DISPLAY, true /* focused */);
        when(info.getChanges()).thenReturn(changes);
        mFocusTransitionObserver.updateFocusState(info);
        mShellExecutor.flushAll();
        verify(mListener, never()).onFocusedDisplayChanged(anyInt());
        verify(mListener, times(1)).onFocusedTaskChanged(
                argThat(new RunningTaskInfoMatcher(change1.getTaskInfo())),
                eq(true) /* isFocusedOnDisplay */, eq(true) /* isFocusedGlobally */);
        clearInvocations(mListener);

        // Open a task on the secondary display.
        TransitionInfo.Change change2 = setupTaskChange(changes, 2 /* taskId */, TRANSIT_OPEN,
                SECONDARY_DISPLAY_ID, true /* focused */);
        setupDisplayToTopChange(changes, SECONDARY_DISPLAY_ID);
        when(info.getChanges()).thenReturn(changes);
        mFocusTransitionObserver.updateFocusState(info);
        mShellExecutor.flushAll();
        verify(mListener, times(1))
                .onFocusedDisplayChanged(SECONDARY_DISPLAY_ID);
        verify(mListener, times(1)).onFocusedTaskChanged(
                argThat(new RunningTaskInfoMatcher(change1.getTaskInfo())),
                eq(true) /* isFocusedOnDisplay */, eq(false) /* isFocusedGlobally */);
        verify(mListener, times(1)).onFocusedTaskChanged(
                argThat(new RunningTaskInfoMatcher(change2.getTaskInfo())),
                eq(true) /* isFocusedOnDisplay */, eq(true) /* isFocusedGlobally */);
        clearInvocations(mListener);

        // Moving only the default display back to front, and verify that affected tasks are also
        // notified.
        changes.clear();
        setupDisplayToTopChange(changes, DEFAULT_DISPLAY);
        when(info.getChanges()).thenReturn(changes);
        mFocusTransitionObserver.updateFocusState(info);
        mShellExecutor.flushAll();
        verify(mListener, times(1))
                .onFocusedDisplayChanged(DEFAULT_DISPLAY);
        verify(mListener, times(1)).onFocusedTaskChanged(
                argThat(new RunningTaskInfoMatcher(change1.getTaskInfo())),
                eq(true) /* isFocusedOnDisplay */, eq(true) /* isFocusedGlobally */);
        verify(mListener, times(1)).onFocusedTaskChanged(
                argThat(new RunningTaskInfoMatcher(change2.getTaskInfo())),
                eq(true) /* isFocusedOnDisplay */, eq(false) /* isFocusedGlobally */);
    }

    @Test
    public void testTaskFocusSwitch() throws RemoteException {
        final SurfaceControl.Transaction tx = mock(SurfaceControl.Transaction.class);

        // Open 2 tasks on the default display.
        TransitionInfo info = mock(TransitionInfo.class);
        final List<TransitionInfo.Change> changes = new ArrayList<>();
        TransitionInfo.Change change1 = setupTaskChange(changes, 1 /* taskId */, TRANSIT_OPEN,
                DEFAULT_DISPLAY, true /* focused */);
        when(info.getChanges()).thenReturn(changes);
        mFocusTransitionObserver.updateFocusState(info);
        mShellExecutor.flushAll();
        verify(mListener, never()).onFocusedDisplayChanged(anyInt());
        verify(mListener, times(1)).onFocusedTaskChanged(
                argThat(new RunningTaskInfoMatcher(change1.getTaskInfo())),
                eq(true) /* isFocusedOnDisplay */, eq(true) /* isFocusedGlobally */);
        clearInvocations(mListener);
        changes.clear();

        TransitionInfo.Change change2 = setupTaskChange(changes, 2 /* taskId */, TRANSIT_OPEN,
                DEFAULT_DISPLAY, true /* focused */);
        when(info.getChanges()).thenReturn(changes);
        mFocusTransitionObserver.updateFocusState(info);
        mShellExecutor.flushAll();
        verify(mListener, times(1)).onFocusedTaskChanged(
                argThat(new RunningTaskInfoMatcher(change1.getTaskInfo())),
                eq(false) /* isFocusedOnDisplay */, eq(false) /* isFocusedGlobally */);
        verify(mListener, times(1)).onFocusedTaskChanged(
                argThat(new RunningTaskInfoMatcher(change2.getTaskInfo())),
                eq(true) /* isFocusedOnDisplay */, eq(true) /* isFocusedGlobally */);
        clearInvocations(mListener);
        changes.clear();

        // Moving a task to front.
        changes.clear();
        setupTaskChange(changes, 1 /* taskId */, TRANSIT_TO_FRONT,
                DEFAULT_DISPLAY, true /* focused */);
        when(info.getChanges()).thenReturn(changes);
        mFocusTransitionObserver.updateFocusState(info);
        mShellExecutor.flushAll();
        verify(mListener, times(1)).onFocusedTaskChanged(
                argThat(new RunningTaskInfoMatcher(change1.getTaskInfo())),
                eq(true) /* isFocusedOnDisplay */, eq(true) /* isFocusedGlobally */);
        verify(mListener, times(1)).onFocusedTaskChanged(
                argThat(new RunningTaskInfoMatcher(change2.getTaskInfo())),
                eq(false) /* isFocusedOnDisplay */, eq(false) /* isFocusedGlobally */);
    }

    @Test
    public void testUpdateFocus_movedToTop_notTransitOpen() {
        // This test verifies that a task focus is updated for a transition with FLAG_MOVED_TO_TOP,
        // even if the transition mode is not TRANSIT_OPEN. This specifically tests one part of the
        // OR condition in the focus update logic.

        // Setup a transition for a task moving to the top using TRANSIT_CHANGE.
        TransitionInfo info = mock(TransitionInfo.class);
        final List<TransitionInfo.Change> changes = new ArrayList<>();
        TransitionInfo.Change change = setupTaskChange(changes, 1 /* taskId */, TRANSIT_CHANGE,
                DEFAULT_DISPLAY, false /* focused */, true /* movedToTop */);
        when(info.getChanges()).thenReturn(changes);

        // Trigger the focus state update.
        mFocusTransitionObserver.updateFocusState(info);
        mShellExecutor.flushAll();

        // Verify that the listener was notified about the new focused task.
        // This confirms that the `change.hasFlags(FLAG_MOVED_TO_TOP)` condition was sufficient
        // to trigger the focus update.
        verify(mListener, times(1)).onFocusedTaskChanged(
                argThat(new RunningTaskInfoMatcher(change.getTaskInfo())),
                eq(true) /* isFocusedOnDisplay */, eq(true) /* isFocusedGlobally */);
    }

    @Test
    public void testUpdateFocus_transitOpen_noMovedToTopFlag() {
        // This test verifies that a task focus is updated for a TRANSIT_OPEN transition,
        // even if the FLAG_MOVED_TO_TOP is not set. This specifically tests the other part of the
        // OR condition in the focus update logic.

        // Setup a transition for a new task opening, but without the MOVED_TO_TOP flag.
        TransitionInfo info = mock(TransitionInfo.class);
        final List<TransitionInfo.Change> changes = new ArrayList<>();
        TransitionInfo.Change change = setupTaskChange(changes, 1 /* taskId */, TRANSIT_OPEN,
                DEFAULT_DISPLAY, false /* focused */, false /* movedToTop */);
        when(info.getChanges()).thenReturn(changes);

        // Trigger the focus state update.
        mFocusTransitionObserver.updateFocusState(info);
        mShellExecutor.flushAll();

        // Verify that the listener was notified about the new focused task.
        verify(mListener, times(1)).onFocusedTaskChanged(
                argThat(new RunningTaskInfoMatcher(change.getTaskInfo())),
                eq(true) /* isFocusedOnDisplay */, eq(true) /* isFocusedGlobally */);
    }


    @Test
    public void testTaskMoveToAnotherDisplay() throws RemoteException {
        final SurfaceControl.Transaction tx = mock(SurfaceControl.Transaction.class);

        // First, open a task on the default display.
        TransitionInfo info = mock(TransitionInfo.class);
        final List<TransitionInfo.Change> changes = new ArrayList<>();
        TransitionInfo.Change change1 = setupTaskChange(changes, 1 /* taskId */, TRANSIT_OPEN,
                DEFAULT_DISPLAY, true /* focused */);
        when(info.getChanges()).thenReturn(changes);
        mFocusTransitionObserver.updateFocusState(info);
        mShellExecutor.flushAll();
        verify(mListener, never()).onFocusedDisplayChanged(anyInt());
        verify(mListener, times(1)).onFocusedTaskChanged(
                argThat(new RunningTaskInfoMatcher(change1.getTaskInfo())),
                eq(true) /* isFocusedOnDisplay */, eq(true) /* isFocusedGlobally */);
        clearInvocations(mListener);
        changes.clear();

        // Open 2 tasks on the secondary display.
        TransitionInfo.Change change2 = setupTaskChange(changes, 2 /* taskId */, TRANSIT_OPEN,
                SECONDARY_DISPLAY_ID, true /* focused */);
        setupDisplayToTopChange(changes, SECONDARY_DISPLAY_ID);
        when(info.getChanges()).thenReturn(changes);
        mFocusTransitionObserver.updateFocusState(info);
        mShellExecutor.flushAll();
        verify(mListener, times(1))
                .onFocusedDisplayChanged(SECONDARY_DISPLAY_ID);
        verify(mListener, times(1)).onFocusedTaskChanged(
                argThat(new RunningTaskInfoMatcher(change1.getTaskInfo())),
                eq(true) /* isFocusedOnDisplay */, eq(false) /* isFocusedGlobally */);
        verify(mListener, times(1)).onFocusedTaskChanged(
                argThat(new RunningTaskInfoMatcher(change2.getTaskInfo())),
                eq(true) /* isFocusedOnDisplay */, eq(true) /* isFocusedGlobally */);
        clearInvocations(mListener);
        changes.clear();

        TransitionInfo.Change change3 = setupTaskChange(changes, 3 /* taskId */, TRANSIT_OPEN,
                SECONDARY_DISPLAY_ID, true /* focused */);
        setupDisplayToTopChange(changes, SECONDARY_DISPLAY_ID);
        when(info.getChanges()).thenReturn(changes);
        mFocusTransitionObserver.updateFocusState(info);
        mShellExecutor.flushAll();
        verify(mListener, times(1)).onFocusedTaskChanged(
                argThat(new RunningTaskInfoMatcher(change2.getTaskInfo())),
                eq(false) /* isFocusedOnDisplay */, eq(false) /* isFocusedGlobally */);
        verify(mListener, times(1)).onFocusedTaskChanged(
                argThat(new RunningTaskInfoMatcher(change3.getTaskInfo())),
                eq(true) /* isFocusedOnDisplay */, eq(true) /* isFocusedGlobally */);
        clearInvocations(mListener);
        changes.clear();

        // Move focused task in the secondary display to the default display
        setupTaskChange(changes, 3 /* taskId */, TRANSIT_CHANGE,
                SECONDARY_DISPLAY_ID, DEFAULT_DISPLAY, true /* focused */, true /* movedToTop */);
        setupTaskChange(changes, 2 /* taskId */, TRANSIT_TO_FRONT,
                SECONDARY_DISPLAY_ID, true /* focused */);
        setupDisplayToTopChange(changes, DEFAULT_DISPLAY);
        when(info.getChanges()).thenReturn(changes);
        mFocusTransitionObserver.updateFocusState(info);
        mShellExecutor.flushAll();
        verify(mListener, times(1)).onFocusedTaskChanged(
                argThat(new RunningTaskInfoMatcher(change1.getTaskInfo())),
                eq(false) /* isFocusedOnDisplay */, eq(false) /* isFocusedGlobally */);
        verify(mListener, times(1)).onFocusedTaskChanged(
                argThat(new RunningTaskInfoMatcher(change2.getTaskInfo())),
                eq(true) /* isFocusedOnDisplay */, eq(false) /* isFocusedGlobally */);
        verify(mListener, times(1)).onFocusedTaskChanged(
                argThat(new RunningTaskInfoMatcher(change3.getTaskInfo())),
                eq(true) /* isFocusedOnDisplay */, eq(true) /* isFocusedGlobally */);
        clearInvocations(mListener);
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_INTERACTIVE_PICTURE_IN_PICTURE)
    public void testTaskFocusChange_whenTransitionHasOnlyFocusChange() throws RemoteException {
        final TransitionInfo info = mock(TransitionInfo.class);
        final List<TransitionInfo.Change> changes = new ArrayList<>();
        final TransitionInfo.Change change = setupTaskChange(changes, 1 /* taskId */, TRANSIT_OPEN,
                DEFAULT_DISPLAY, true /* focused */, false /* movedToTop */);
        when(info.getChanges()).thenReturn(changes);

        mFocusTransitionObserver.updateFocusState(info);

        mShellExecutor.flushAll();
        verify(mListener, never()).onFocusedDisplayChanged(anyInt());
        verify(mListener, times(1)).onFocusedTaskChanged(
                argThat(new RunningTaskInfoMatcher(change.getTaskInfo())),
                eq(true) /* isFocusedOnDisplay */, eq(true) /* isFocusedGlobally */);
        assertTrue(mFocusTransitionObserver.hasGlobalFocus(change.getTaskInfo()));
        clearInvocations(mListener);
        changes.clear();
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_INTERACTIVE_PICTURE_IN_PICTURE)
    public void testTaskFocusChange_whenTaskIsAlreadyFocused_doesNotNotifyListeners()
            throws RemoteException {
        final TransitionInfo info = mock(TransitionInfo.class);
        final List<TransitionInfo.Change> changes = new ArrayList<>();
        final TransitionInfo.Change change = setupTaskChange(changes, 1 /* taskId */, TRANSIT_OPEN,
                DEFAULT_DISPLAY, true /* focused */, false /* movedToTop */);
        when(info.getChanges()).thenReturn(changes);
        // Set the task as focused in the observer and then add a new listener.
        mFocusTransitionObserver.updateFocusState(info);
        final FocusTransitionListener newListener = mock(FocusTransitionListener.class);
        mFocusTransitionObserver.setLocalFocusTransitionListener(newListener, mShellExecutor);

        // when updateFocusState is called again, the task should already be focused and the
        // listener should not be notified.
        mFocusTransitionObserver.updateFocusState(info);

        mShellExecutor.flushAll();
        // original listener notified only once (first focus update)
        verify(mListener, never()).onFocusedDisplayChanged(anyInt());
        verify(mListener, times(1)).onFocusedTaskChanged(
                argThat(new RunningTaskInfoMatcher(change.getTaskInfo())),
                eq(true) /* isFocusedOnDisplay */, eq(true) /* isFocusedGlobally */);
        assertTrue(mFocusTransitionObserver.hasGlobalFocus(change.getTaskInfo()));
        // new listener was not notified
        verify(newListener, never()).onFocusedTaskChanged(any(), anyBoolean(), anyBoolean());
        clearInvocations(mListener);
        changes.clear();
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_FOCUS_RESET_ON_DISPLAY_REMOVAL)
    public void testOnDisplayDisconnected_focused_resetsFocus() throws RemoteException {
        // First, move focus to a secondary display.
        TransitionInfo info = mock(TransitionInfo.class);
        final List<TransitionInfo.Change> changes = new ArrayList<>();
        setupDisplayToTopChange(changes, SECONDARY_DISPLAY_ID);
        when(info.getChanges()).thenReturn(changes);
        mFocusTransitionObserver.updateFocusState(info);
        mShellExecutor.flushAll();
        verify(mListener, times(1)).onFocusedDisplayChanged(SECONDARY_DISPLAY_ID);
        clearInvocations(mListener);

        // Manually trigger display disconnect.
        mFocusTransitionObserver.onDisplayDisconnected(SECONDARY_DISPLAY_ID, DEFAULT_DISPLAY);
        mShellExecutor.flushAll();

        // Verify that focus is reset to DEFAULT_DISPLAY.
        verify(mListener, times(1)).onFocusedDisplayChanged(DEFAULT_DISPLAY);
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_FOCUS_RESET_ON_DISPLAY_REMOVAL)
    public void testOnDisplayDisconnected_notFocused_doesNotResetFocus() throws RemoteException {
        // Focus is initially on DEFAULT_DISPLAY, disconnect on different display does not reset
        // focus.
        mFocusTransitionObserver.onDisplayDisconnected(SECONDARY_DISPLAY_ID, DEFAULT_DISPLAY);
        mShellExecutor.flushAll();

        verify(mListener, never()).onFocusedDisplayChanged(anyInt());
    }

    @Test
    public void testAddDump() {
        verify(mShellCommandHandler, times(1)).addDumpCallback(
                any(), isA(FocusTransitionObserver.class));
    }

    private TransitionInfo.Change setupTaskChange(List<TransitionInfo.Change> changes, int taskId,
            @TransitionMode int mode, int displayId, boolean focused) {
        return setupTaskChange(changes, taskId, mode, displayId, displayId, focused,
                true /* movedToTop */);
    }

    private TransitionInfo.Change setupTaskChange(List<TransitionInfo.Change> changes, int taskId,
            @TransitionMode int mode, int displayId, boolean focused,
            boolean movedToTop) {
        return setupTaskChange(changes, taskId, mode, displayId, displayId, focused, movedToTop);
    }

    private TransitionInfo.Change setupTaskChange(List<TransitionInfo.Change> changes, int taskId,
            @TransitionMode int mode, int startDisplayId, int endDisplayId, boolean taskFocused,
            boolean movedToTop) {
        TransitionInfo.Change change = mock(TransitionInfo.Change.class);
        RunningTaskInfo taskInfo = mock(RunningTaskInfo.class);
        taskInfo.taskId = taskId;
        taskInfo.isFocused = taskFocused;
        when(change.hasFlags(FLAG_MOVED_TO_TOP)).thenReturn(movedToTop);
        taskInfo.displayId = endDisplayId;
        when(change.getStartDisplayId()).thenReturn(startDisplayId);
        when(change.getEndDisplayId()).thenReturn(endDisplayId);
        when(change.getTaskInfo()).thenReturn(taskInfo);
        when(change.getMode()).thenReturn(mode);
        changes.add(change);
        return change;
    }


    private void setupDisplayToTopChange(List<TransitionInfo.Change> changes, int displayId) {
        TransitionInfo.Change change = mock(TransitionInfo.Change.class);
        when(change.hasFlags(FLAG_MOVED_TO_TOP)).thenReturn(true);
        when(change.hasFlags(FLAG_IS_DISPLAY)).thenReturn(true);
        when(change.getEndDisplayId()).thenReturn(displayId);
        changes.add(change);
    }

    private static class RunningTaskInfoMatcher implements ArgumentMatcher<RunningTaskInfo> {

        private final RunningTaskInfo mExpectedTaskInfo;

        public RunningTaskInfoMatcher(RunningTaskInfo runningTaskInfo) {
            mExpectedTaskInfo = runningTaskInfo;
        }

        @Override
        public boolean matches(RunningTaskInfo actualTaskInfo) {
            return mExpectedTaskInfo.taskId == actualTaskInfo.taskId;
        }
    }
}
