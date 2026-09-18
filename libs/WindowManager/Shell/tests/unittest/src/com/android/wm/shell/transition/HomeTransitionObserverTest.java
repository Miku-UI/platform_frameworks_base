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

package com.android.wm.shell.transition;

import static android.app.WindowConfiguration.ACTIVITY_TYPE_HOME;
import static android.app.WindowConfiguration.ACTIVITY_TYPE_STANDARD;
import static android.app.WindowConfiguration.ACTIVITY_TYPE_UNDEFINED;
import static android.view.WindowManager.TRANSIT_CHANGE;
import static android.view.WindowManager.TRANSIT_FLAG_KEYGUARD_GOING_AWAY;
import static android.view.WindowManager.TRANSIT_OPEN;
import static android.view.WindowManager.TRANSIT_PREPARE_BACK_NAVIGATION;
import static android.view.WindowManager.TRANSIT_TO_BACK;
import static android.view.WindowManager.TRANSIT_TO_FRONT;
import static android.view.WindowManager.TRANSIT_WAKE;
import static android.window.TransitionInfo.FLAG_BACK_GESTURE_ANIMATED;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;
import static com.android.wm.shell.desktopmode.DesktopModeTransitionTypes.TRANSIT_DESKTOP_MODE_START_DRAG_TO_DESKTOP;
import static com.android.wm.shell.desktopmode.DesktopModeTransitionTypes.TRANSIT_ENTER_DESKTOP_FROM_APP_HANDLE_MENU_BUTTON;
import static com.android.wm.shell.desktopmode.DesktopModeTransitionTypes.TRANSIT_EXIT_DESKTOP_MODE_HANDLE_MENU_BUTTON;
import static com.android.wm.shell.transition.Transitions.TRANSIT_CONVERT_TO_BUBBLE;
import static com.android.wm.shell.transition.Transitions.TRANSIT_MINIMIZE;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.platform.test.annotations.EnableFlags;
import android.view.SurfaceControl;
import android.window.TransitionInfo;
import android.window.WindowContainerToken;

import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.testing.wm.util.TransitionInfoBuilder;
import com.android.wm.shell.Flags;
import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.ShellTestCase;
import com.android.wm.shell.TestShellExecutor;
import com.android.wm.shell.common.DisplayController;
import com.android.wm.shell.common.DisplayInsetsController;
import com.android.wm.shell.common.ShellExecutor;
import com.android.wm.shell.desktopmode.multidesks.DesksOrganizer;
import com.android.wm.shell.shared.IHomeTransitionListener;
import com.android.wm.shell.shared.TransactionPool;
import com.android.wm.shell.shared.desktopmode.FakeDesktopState;
import com.android.wm.shell.sysui.ShellController;
import com.android.wm.shell.sysui.ShellInit;

import com.google.testing.junit.testparameterinjector.TestParameter;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Optional;

/**
 * Tests for the home transition observer.
 */
@SmallTest
@RunWith(TestParameterInjector.class)
public class HomeTransitionObserverTest extends ShellTestCase {

    @TestParameter boolean mShowHomeBehindDesktopWindows;

    private static final int TEST_USER = 0;
    private static final int TEST_USER_2 = 10;

    private final ShellTaskOrganizer mOrganizer = mock(ShellTaskOrganizer.class);
    private final TransactionPool mTransactionPool = mock(TransactionPool.class);
    private final Context mContext =
            InstrumentationRegistry.getInstrumentation().getTargetContext();
    private final ShellExecutor mAnimExecutor = new TestShellExecutor();
    private final TestShellExecutor mMainExecutor = new TestShellExecutor();
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    private final ShellController mShellController = mock(ShellController.class);
    private final DisplayController mDisplayController = mock(DisplayController.class);

    private final DesksOrganizer mDesksOrganizer = mock(DesksOrganizer.class);
    private final DisplayInsetsController mDisplayInsetsController =
            mock(DisplayInsetsController.class);

    private IHomeTransitionListener mListener;
    private IHomeTransitionListener mListener2;
    private Transitions mTransition;
    private HomeTransitionObserver mHomeTransitionObserver;

    @Before
    public void setUp() {
        mListener = mock(IHomeTransitionListener.class);
        when(mListener.asBinder()).thenReturn(mock(IBinder.class));
        mListener2 = mock(IHomeTransitionListener.class);
        when(mListener2.asBinder()).thenReturn(mock(IBinder.class));

        when(mShellController.getCurrentUserId()).thenReturn(TEST_USER);

        FakeDesktopState desktopState = new FakeDesktopState();
        desktopState.setShouldShowHomeBehindDesktop(mShowHomeBehindDesktopWindows);

        mHomeTransitionObserver = new HomeTransitionObserver(mContext, mMainExecutor,
                mDisplayInsetsController, mShellController, mock(ShellInit.class),
                desktopState, Optional.of(mDesksOrganizer));
        mTransition = new Transitions(mContext, mock(ShellInit.class), mShellController,
                mOrganizer, mTransactionPool, mDisplayController, mDisplayInsetsController,
                mMainExecutor, mMainHandler, mAnimExecutor, mock(TransitionLeashManager.class),
                mHomeTransitionObserver, mock(FocusTransitionObserver.class));
        mHomeTransitionObserver.setHomeTransitionListener(mTransition, mListener, TEST_USER);
    }

    @Test
    public void testHomeActivityWithOpenModeNotifiesHomeIsVisible() throws RemoteException {
        ActivityManager.RunningTaskInfo taskInfo = createTaskInfo(1, ACTIVITY_TYPE_HOME);
        TransitionInfo info =
                new TransitionInfoBuilder(TRANSIT_OPEN)
                        .addChange(TRANSIT_OPEN, 0 /* flags */, taskInfo)
                        .build();

        mHomeTransitionObserver.onTransitionReady(mock(IBinder.class),
                info,
                mock(SurfaceControl.Transaction.class),
                mock(SurfaceControl.Transaction.class));

        verify(mListener, times(1))
                .onHomeVisibilityChanged(/* isVisible= */ true, /* keyguardGoingAway= */ false,
                        /* behindDesktop= */ false);
    }

    @Test
    public void testHomeActivityWithCloseModeNotifiesHomeIsNotVisible() throws RemoteException {
        ActivityManager.RunningTaskInfo taskInfo = createTaskInfo(1, ACTIVITY_TYPE_HOME);
        TransitionInfo info =
                new TransitionInfoBuilder(TRANSIT_TO_BACK)
                        .addChange(TRANSIT_TO_BACK, 0 /* flags */, taskInfo)
                        .build();

        mHomeTransitionObserver.onTransitionReady(mock(IBinder.class),
                info,
                mock(SurfaceControl.Transaction.class),
                mock(SurfaceControl.Transaction.class));

        verify(mListener, times(1))
                .onHomeVisibilityChanged(/* isVisible= */ false, /* keyguardGoingAway= */ false,
                        /* behindDesktop= */ false);
    }

    @Test
    public void testHomeActivity_differentUserTransition_doesNotTriggerCallbackForCurrentUser()
            throws RemoteException {
        ActivityManager.RunningTaskInfo taskInfo = createTaskInfo(1, ACTIVITY_TYPE_HOME);
        taskInfo.userId = TEST_USER_2;
        TransitionInfo info =
                new TransitionInfoBuilder(TRANSIT_OPEN)
                        .addChange(TRANSIT_OPEN, 0 /* flags */, taskInfo)
                        .build();

        mHomeTransitionObserver.onTransitionReady(mock(IBinder.class),
                info,
                mock(SurfaceControl.Transaction.class),
                mock(SurfaceControl.Transaction.class));

        verify(mListener, never())
                .onHomeVisibilityChanged(
                        /* isVisible= */ anyBoolean(), /* keyguardGoingAway= */ anyBoolean(),
                        /* behindDesktop= */ anyBoolean());

    }

    @Test
    public void testNonHomeActivityDoesNotTriggerCallback() throws RemoteException {
        ActivityManager.RunningTaskInfo taskInfo = createTaskInfo(1, ACTIVITY_TYPE_UNDEFINED);
        TransitionInfo info =
                new TransitionInfoBuilder(TRANSIT_TO_BACK)
                        .addChange(TRANSIT_TO_BACK, 0 /* flags */, taskInfo)
                        .build();

        mHomeTransitionObserver.onTransitionReady(mock(IBinder.class),
                info,
                mock(SurfaceControl.Transaction.class),
                mock(SurfaceControl.Transaction.class));

        verify(mListener, times(0))
                .onHomeVisibilityChanged(
                        /* isVisible= */ anyBoolean(), /* keyguardGoingAway= */ anyBoolean(),
                        /* behindDesktop= */ anyBoolean());
    }

    @Test
    public void testNonRunningHomeActivityDoesNotTriggerCallback() throws RemoteException {
        ActivityManager.RunningTaskInfo taskInfo = createTaskInfo(1, ACTIVITY_TYPE_UNDEFINED);
        TransitionInfo info =
                new TransitionInfoBuilder(TRANSIT_TO_BACK)
                        .addChange(TRANSIT_TO_BACK, 0 /* flags */, taskInfo)
                        .build();

        mHomeTransitionObserver.onTransitionReady(mock(IBinder.class),
                info,
                mock(SurfaceControl.Transaction.class),
                mock(SurfaceControl.Transaction.class));

        verify(mListener, times(0))
                .onHomeVisibilityChanged(
                        /* isVisible= */ anyBoolean(), /* keyguardGoingAway= */ anyBoolean(),
                        /* behindDesktop= */ anyBoolean());
    }

    @Test
    public void testHomeShownBehindDeskTriggersCallback() throws RemoteException {
        ActivityManager.RunningTaskInfo homeTaskInfo = createTaskInfo(1, ACTIVITY_TYPE_HOME);
        ActivityManager.RunningTaskInfo taskInfo = createTaskInfo(2, ACTIVITY_TYPE_STANDARD);
        ActivityManager.RunningTaskInfo deskTaskInfo = createTaskInfo(3, ACTIVITY_TYPE_STANDARD);

        setTaskIdForMockedDeskChanges(3);

        TransitionInfo showHomeInfo =
                new TransitionInfoBuilder(TRANSIT_OPEN)
                        .addChange(TRANSIT_TO_FRONT, 0 /* flags */, homeTaskInfo)
                        .build();

        mHomeTransitionObserver.onTransitionReady(mock(IBinder.class),
                showHomeInfo,
                mock(SurfaceControl.Transaction.class),
                mock(SurfaceControl.Transaction.class));

        verify(mListener, times(1))
                .onHomeVisibilityChanged(/* isVisible= */ true, /* keyguardGoingAway= */ false,
                        /* behindDesktop= */ false);

        TransitionInfo openDesktop =
                new TransitionInfoBuilder(TRANSIT_OPEN)
                        .addChange(TRANSIT_TO_FRONT, 0 /* flags */, taskInfo)
                        .addChange(TRANSIT_TO_FRONT, 0 /* flags */, deskTaskInfo)
                        .build();

        mHomeTransitionObserver.onTransitionReady(mock(IBinder.class),
                openDesktop,
                mock(SurfaceControl.Transaction.class),
                mock(SurfaceControl.Transaction.class));

        verify(mListener, times(mShowHomeBehindDesktopWindows ? 1 : 0))
                .onHomeVisibilityChanged(/* isVisible= */ true, /* keyguardGoingAway= */ false,
                        /* behindDesktop= */ true);
    }

    @Test
    public void testDeskOpenWithNoHomeTransitionTriggerCallback() throws RemoteException {
        ActivityManager.RunningTaskInfo taskInfo = createTaskInfo(2, ACTIVITY_TYPE_STANDARD);
        ActivityManager.RunningTaskInfo deskTaskInfo = createTaskInfo(3, ACTIVITY_TYPE_STANDARD);
        setTaskIdForMockedDeskChanges(3);

        TransitionInfo openDesktop =
                new TransitionInfoBuilder(TRANSIT_OPEN)
                        .addChange(TRANSIT_TO_FRONT, 0 /* flags */, taskInfo)
                        .addChange(TRANSIT_TO_FRONT, 0 /* flags */, deskTaskInfo)
                        .build();

        mHomeTransitionObserver.onTransitionReady(mock(IBinder.class),
                openDesktop,
                mock(SurfaceControl.Transaction.class),
                mock(SurfaceControl.Transaction.class));

        verify(mListener, times(mShowHomeBehindDesktopWindows ? 1 : 0))
                .onHomeVisibilityChanged(/* isVisible= */ true, /* keyguardGoingAway= */ false,
                        /* behindDesktop= */ true);
    }

    @Test
    public void testDeskHiddenWithHomeHiddenDoesNotTriggerCallback() throws RemoteException {
        ActivityManager.RunningTaskInfo homeTaskInfo = createTaskInfo(1, ACTIVITY_TYPE_HOME);
        ActivityManager.RunningTaskInfo taskInfo = createTaskInfo(2, ACTIVITY_TYPE_STANDARD);
        ActivityManager.RunningTaskInfo deskTaskInfo = createTaskInfo(3, ACTIVITY_TYPE_STANDARD);
        setTaskIdForMockedDeskChanges(3);

        TransitionInfo showHomeAndDesktopInfo =
                new TransitionInfoBuilder(TRANSIT_OPEN)
                        .addChange(TRANSIT_TO_FRONT, 0 /* flags */, homeTaskInfo)
                        .addChange(TRANSIT_TO_FRONT, 0 /* flags */, taskInfo)
                        .addChange(TRANSIT_TO_FRONT, 0 /* flags */, deskTaskInfo)
                        .build();

        mHomeTransitionObserver.onTransitionReady(mock(IBinder.class),
                showHomeAndDesktopInfo,
                mock(SurfaceControl.Transaction.class),
                mock(SurfaceControl.Transaction.class));

        verify(mListener, times(1))
                .onHomeVisibilityChanged(/* isVisible= */ true, /* keyguardGoingAway= */ false,
                        /* behindDesktop= */ mShowHomeBehindDesktopWindows);

        TransitionInfo hideHome =
                new TransitionInfoBuilder(TRANSIT_TO_BACK)
                        .addChange(TRANSIT_TO_BACK, 0 /* flags */, homeTaskInfo)
                        .build();

        mHomeTransitionObserver.onTransitionReady(mock(IBinder.class),
                hideHome,
                mock(SurfaceControl.Transaction.class),
                mock(SurfaceControl.Transaction.class));

        verify(mListener, times(1))
                .onHomeVisibilityChanged(/* isVisible= */ false, /* keyguardGoingAway= */ false,
                        /* behindDesktop= */ false);

        TransitionInfo hideDesktop =
                new TransitionInfoBuilder(TRANSIT_EXIT_DESKTOP_MODE_HANDLE_MENU_BUTTON)
                        .addChange(TRANSIT_TO_BACK, 0 /* flags */, taskInfo)
                        .addChange(TRANSIT_TO_BACK, 0 /* flags */, deskTaskInfo)
                        .build();

        mHomeTransitionObserver.onTransitionReady(mock(IBinder.class),
                hideDesktop,
                mock(SurfaceControl.Transaction.class),
                mock(SurfaceControl.Transaction.class));

        verify(mListener, never())
                .onHomeVisibilityChanged(/* isVisible= */ false, /* keyguardGoingAway= */ false,
                        /* behindDesktop= */ true);
    }

    @Test
    public void testDeskHiddenWithVisibleHomeTriggerCallback() throws RemoteException {
        ActivityManager.RunningTaskInfo homeTaskInfo = createTaskInfo(1, ACTIVITY_TYPE_HOME);
        ActivityManager.RunningTaskInfo taskInfo = createTaskInfo(2, ACTIVITY_TYPE_STANDARD);
        ActivityManager.RunningTaskInfo deskTaskInfo = createTaskInfo(3, ACTIVITY_TYPE_STANDARD);
        setTaskIdForMockedDeskChanges(3);

        TransitionInfo showHomeAndDesktopInfo =
                new TransitionInfoBuilder(TRANSIT_OPEN)
                        .addChange(TRANSIT_TO_FRONT, 0 /* flags */, homeTaskInfo)
                        .addChange(TRANSIT_TO_FRONT, 0 /* flags */, taskInfo)
                        .addChange(TRANSIT_TO_FRONT, 0 /* flags */, deskTaskInfo)
                        .build();

        mHomeTransitionObserver.onTransitionReady(mock(IBinder.class),
                showHomeAndDesktopInfo,
                mock(SurfaceControl.Transaction.class),
                mock(SurfaceControl.Transaction.class));

        verify(mListener, times(1))
                .onHomeVisibilityChanged(/* isVisible= */ true, /* keyguardGoingAway= */ false,
                        /* behindDesktop= */ mShowHomeBehindDesktopWindows);

        TransitionInfo hideDesktop =
                new TransitionInfoBuilder(TRANSIT_EXIT_DESKTOP_MODE_HANDLE_MENU_BUTTON)
                        .addChange(TRANSIT_TO_BACK, 0 /* flags */, taskInfo)
                        .addChange(TRANSIT_TO_BACK, 0 /* flags */, deskTaskInfo)
                        .build();

        mHomeTransitionObserver.onTransitionReady(mock(IBinder.class),
                hideDesktop,
                mock(SurfaceControl.Transaction.class),
                mock(SurfaceControl.Transaction.class));

        if (mShowHomeBehindDesktopWindows) {
            verify(mListener, times(1))
                    .onHomeVisibilityChanged(/* isVisible= */ true, /* keyguardGoingAway= */ false,
                            /* behindDesktop= */ false);
        }
    }

    @Test
    public void testShowingHomeAndDesktopTriggersCallback() throws RemoteException {
        ActivityManager.RunningTaskInfo homeTaskInfo = createTaskInfo(1, ACTIVITY_TYPE_HOME);
        ActivityManager.RunningTaskInfo taskInfo = createTaskInfo(2, ACTIVITY_TYPE_STANDARD);
        ActivityManager.RunningTaskInfo deskTaskInfo = createTaskInfo(3, ACTIVITY_TYPE_STANDARD);
        setTaskIdForMockedDeskChanges(3);

        TransitionInfo showHomeAndDesktopInfo =
                new TransitionInfoBuilder(TRANSIT_TO_BACK)
                        .addChange(TRANSIT_TO_BACK, 0 /* flags */, homeTaskInfo)
                        .build();

        mHomeTransitionObserver.onTransitionReady(mock(IBinder.class),
                showHomeAndDesktopInfo,
                mock(SurfaceControl.Transaction.class),
                mock(SurfaceControl.Transaction.class));

        verify(mListener, times(1))
                .onHomeVisibilityChanged(/* isVisible= */ false, /* keyguardGoingAway= */ false,
                        /* behindDesktop= */ false);

        TransitionInfo showHomeAndDesktop =
                new TransitionInfoBuilder(TRANSIT_ENTER_DESKTOP_FROM_APP_HANDLE_MENU_BUTTON)
                        .addChange(TRANSIT_TO_FRONT, 0 /* flags */, taskInfo)
                        .addChange(TRANSIT_TO_FRONT, 0 /* flags */, deskTaskInfo)
                        .addChange(TRANSIT_TO_FRONT, 0 /* flags */, homeTaskInfo)
                        .build();

        mHomeTransitionObserver.onTransitionReady(mock(IBinder.class),
                showHomeAndDesktop,
                mock(SurfaceControl.Transaction.class),
                mock(SurfaceControl.Transaction.class));

        verify(mListener, times(1))
                .onHomeVisibilityChanged(/* isVisible= */ true, /* keyguardGoingAway= */ false,
                        /* behindDesktop= */ mShowHomeBehindDesktopWindows);
    }

    @Test
    public void testTriggerCallbackIfDesktopExitHasSystemUserIdTask() throws RemoteException {
        mHomeTransitionObserver.setHomeTransitionListener(mTransition, mListener2, TEST_USER_2);
        when(mShellController.getCurrentUserId()).thenReturn(TEST_USER_2);

        ActivityManager.RunningTaskInfo homeTaskInfo = createTaskInfo(1, ACTIVITY_TYPE_HOME);
        homeTaskInfo.userId = TEST_USER_2;
        ActivityManager.RunningTaskInfo taskInfo = createTaskInfo(2, ACTIVITY_TYPE_STANDARD);
        taskInfo.userId = TEST_USER_2;
        ActivityManager.RunningTaskInfo deskTaskInfo = createTaskInfo(3, ACTIVITY_TYPE_STANDARD);
        setTaskIdForMockedDeskChanges(3);

        TransitionInfo showHomeAndDesktop =
                new TransitionInfoBuilder(TRANSIT_ENTER_DESKTOP_FROM_APP_HANDLE_MENU_BUTTON)
                        .addChange(TRANSIT_TO_FRONT, 0 /* flags */, taskInfo)
                        .addChange(TRANSIT_TO_FRONT, 0 /* flags */, deskTaskInfo)
                        .addChange(TRANSIT_TO_FRONT, 0 /* flags */, homeTaskInfo)
                        .build();

        mHomeTransitionObserver.onTransitionReady(mock(IBinder.class),
                showHomeAndDesktop,
                mock(SurfaceControl.Transaction.class),
                mock(SurfaceControl.Transaction.class));

        verify(mListener2, times(1))
                .onHomeVisibilityChanged(/* isVisible= */ true, /* keyguardGoingAway= */ false,
                        /* behindDesktop= */ mShowHomeBehindDesktopWindows);

        deskTaskInfo.userId = TEST_USER;
        TransitionInfo minimizeDesktopWindows =
                new TransitionInfoBuilder(TRANSIT_MINIMIZE)
                        .addChange(TRANSIT_TO_BACK, 0 /* flags */, deskTaskInfo)
                        .addChange(TRANSIT_TO_BACK, 0 /* flags */, taskInfo)
                        .build();

        mHomeTransitionObserver.onTransitionReady(mock(IBinder.class),
                minimizeDesktopWindows,
                mock(SurfaceControl.Transaction.class),
                mock(SurfaceControl.Transaction.class));

        if (mShowHomeBehindDesktopWindows) {
            verify(mListener2, times(1))
                    .onHomeVisibilityChanged(/* isVisible= */ true, /* keyguardGoingAway= */ false,
                            /* behindDesktop= */ false);
        }
    }


    @Test
    public void testStartDragToDesktopDoesNotTriggerCallback() throws RemoteException {
        ActivityManager.RunningTaskInfo taskInfo = createTaskInfo(1, ACTIVITY_TYPE_HOME);
        TransitionInfo info =
                new TransitionInfoBuilder(TRANSIT_DESKTOP_MODE_START_DRAG_TO_DESKTOP)
                        .addChange(TRANSIT_OPEN, 0 /* flags */, taskInfo)
                        .build();

        mHomeTransitionObserver.onTransitionReady(mock(IBinder.class),
                info,
                mock(SurfaceControl.Transaction.class),
                mock(SurfaceControl.Transaction.class));

        verify(mListener, times(0))
                .onHomeVisibilityChanged(
                        /* isVisible= */ anyBoolean(), /* keyguardGoingAway= */ anyBoolean(),
                        /* behindDesktop= */ anyBoolean());
    }

    @Test
    public void startDragToDesktopAborted_triggersCallback() throws RemoteException {
        ActivityManager.RunningTaskInfo taskInfo = createTaskInfo(1, ACTIVITY_TYPE_HOME);
        TransitionInfo info =
                new TransitionInfoBuilder(TRANSIT_DESKTOP_MODE_START_DRAG_TO_DESKTOP)
                        .addChange(TRANSIT_OPEN, 0 /* flags */, taskInfo)
                        .build();

        IBinder transition = mock(IBinder.class);
        mHomeTransitionObserver.onTransitionReady(
                transition,
                info,
                mock(SurfaceControl.Transaction.class),
                mock(SurfaceControl.Transaction.class));

        mHomeTransitionObserver.onTransitionFinished(transition, /* aborted= */ true);

        verify(mListener)
                .onHomeVisibilityChanged(/* isVisible= */ true, /* keyguardGoingAway= */ false,
                /* behindDesktop= */ false);
    }

    @Test
    public void startDragToDesktopFinished_triggersCallback() throws RemoteException {
        ActivityManager.RunningTaskInfo taskInfo = createTaskInfo(1, ACTIVITY_TYPE_HOME);
        TransitionInfo info =
                new TransitionInfoBuilder(TRANSIT_DESKTOP_MODE_START_DRAG_TO_DESKTOP)
                        .addChange(TRANSIT_OPEN, 0 /* flags */, taskInfo)
                        .build();

        IBinder transition = mock(IBinder.class);
        mHomeTransitionObserver.onTransitionReady(
                transition,
                info,
                mock(SurfaceControl.Transaction.class),
                mock(SurfaceControl.Transaction.class));

        mHomeTransitionObserver.onTransitionFinished(transition, /* aborted= */ false);

        verify(mListener)
                .onHomeVisibilityChanged(/* isVisible= */ true, /* keyguardGoingAway= */ false,
                        /* behindDesktop= */ false);
    }

    @Test
    @EnableFlags({Flags.FLAG_ENABLE_BUBBLE_TO_FULLSCREEN, Flags.FLAG_ENABLE_CREATE_ANY_BUBBLE})
    public void testDragTaskToBubbleOverHome_notifiesHomeIsVisible() throws RemoteException {
        ActivityManager.RunningTaskInfo homeTask = createTaskInfo(1, ACTIVITY_TYPE_HOME);
        ActivityManager.RunningTaskInfo bubbleTask = createTaskInfo(2, ACTIVITY_TYPE_STANDARD);

        TransitionInfo startDragTransition =
                new TransitionInfoBuilder(TRANSIT_DESKTOP_MODE_START_DRAG_TO_DESKTOP)
                        .addChange(TRANSIT_TO_FRONT, homeTask)
                        .addChange(TRANSIT_TO_BACK, bubbleTask)
                        .build();

        // Start drag to desktop which brings home to front
        mHomeTransitionObserver.onTransitionReady(new Binder(), startDragTransition,
                MockTransactionPool.create(), MockTransactionPool.create());
        // Does not notify home visibility yet
        verify(mListener, never())
                .onHomeVisibilityChanged(
                        /* isVisible= */ anyBoolean(), /* keyguardGoingAway= */ anyBoolean(),
                        /* behindDesktop= */ anyBoolean());

        TransitionInfo convertToBubbleTransition =
                new TransitionInfoBuilder(TRANSIT_CONVERT_TO_BUBBLE)
                        .addChange(TRANSIT_TO_FRONT, bubbleTask)
                        .build();

        // Convert to bubble. Transition does not include changes for home task
        mHomeTransitionObserver.onTransitionReady(new Binder(), convertToBubbleTransition,
                MockTransactionPool.create(), MockTransactionPool.create());

        // Notifies home visibility change that was pending from the start of drag
        verify(mListener)
                .onHomeVisibilityChanged(/* isVisible= */ true, /* keyguardGoingAway= */ false,
                        /* behindDesktop= */ false);
    }

    @Test
    @EnableFlags({Flags.FLAG_ENABLE_BUBBLE_TO_FULLSCREEN, Flags.FLAG_ENABLE_CREATE_ANY_BUBBLE})
    public void testDragTaskToBubbleOverOtherTask_notifiesHomeIsNotVisible()
            throws RemoteException {
        ActivityManager.RunningTaskInfo homeTask = createTaskInfo(1, ACTIVITY_TYPE_HOME);
        ActivityManager.RunningTaskInfo bubbleTask = createTaskInfo(2, ACTIVITY_TYPE_STANDARD);
        ActivityManager.RunningTaskInfo otherTask = createTaskInfo(3, ACTIVITY_TYPE_STANDARD);

        TransitionInfo startDragTransition =
                new TransitionInfoBuilder(TRANSIT_DESKTOP_MODE_START_DRAG_TO_DESKTOP)
                        .addChange(TRANSIT_TO_FRONT, homeTask)
                        .addChange(TRANSIT_TO_BACK, bubbleTask)
                        .build();

        // Start drag to desktop which brings home to front
        mHomeTransitionObserver.onTransitionReady(new Binder(), startDragTransition,
                MockTransactionPool.create(), MockTransactionPool.create());
        // Does not notify home visibility yet
        verify(mListener, never())
                .onHomeVisibilityChanged(
                        /* isVisible= */ anyBoolean(), /* keyguardGoingAway= */ anyBoolean(),
                        /* behindDesktop= */ anyBoolean());

        TransitionInfo convertToBubbleTransition =
                new TransitionInfoBuilder(TRANSIT_CONVERT_TO_BUBBLE)
                        .addChange(TRANSIT_TO_FRONT, bubbleTask)
                        .addChange(TRANSIT_TO_FRONT, otherTask)
                        .addChange(TRANSIT_TO_BACK, homeTask)
                        .build();

        // Convert to bubble. Transition includes home task to back which updates home visibility
        mHomeTransitionObserver.onTransitionReady(new Binder(), convertToBubbleTransition,
                MockTransactionPool.create(), MockTransactionPool.create());

        // Notifies home visibility change due to home moving to back in the second transition
        verify(mListener)
                .onHomeVisibilityChanged(/* isVisible= */ false, /* keyguardGoingAway= */ false,
                        /* behindDesktop= */ false);
    }

    @Test
    public void testHomeActivityWithBackGestureNotifiesHomeIsVisibleAfterClose()
            throws RemoteException {
        ActivityManager.RunningTaskInfo taskInfo = createTaskInfo(1, ACTIVITY_TYPE_HOME);
        TransitionInfo info =
                new TransitionInfoBuilder(TRANSIT_PREPARE_BACK_NAVIGATION)
                        .addChange(TRANSIT_OPEN, FLAG_BACK_GESTURE_ANIMATED, taskInfo)
                        .build();
        mHomeTransitionObserver.onTransitionReady(mock(IBinder.class),
                info,
                mock(SurfaceControl.Transaction.class),
                mock(SurfaceControl.Transaction.class));
        verify(mListener, times(0))
                .onHomeVisibilityChanged(
                        /* isVisible= */ anyBoolean(), /* keyguardGoingAway= */ anyBoolean(),
                        /* behindDesktop= */ anyBoolean());

        info = new TransitionInfoBuilder(TRANSIT_TO_BACK)
                .addChange(TRANSIT_CHANGE, FLAG_BACK_GESTURE_ANIMATED, taskInfo)
                .build();
        mHomeTransitionObserver.onTransitionReady(mock(IBinder.class),
                info,
                mock(SurfaceControl.Transaction.class),
                mock(SurfaceControl.Transaction.class));
        verify(mListener, times(1))
                .onHomeVisibilityChanged(/* isVisible= */ true, /* keyguardGoingAway= */ false,
                        /* behindDesktop= */ false);
    }

    @Test
    public void testSetListener_userSwitched_triggersWhenUserRegistersListener()
            throws RemoteException {
        ActivityManager.RunningTaskInfo taskInfo = createTaskInfo(1, ACTIVITY_TYPE_HOME);
        TransitionInfo info =
                new TransitionInfoBuilder(TRANSIT_OPEN)
                        .addChange(TRANSIT_OPEN, 0 /* flags */, taskInfo)
                        .build();

        // Switch to user with visible home.
        taskInfo.userId = TEST_USER_2;
        mHomeTransitionObserver.onTransitionReady(mock(IBinder.class),
                info,
                mock(SurfaceControl.Transaction.class),
                mock(SurfaceControl.Transaction.class));

        mHomeTransitionObserver.setHomeTransitionListener(mTransition, mListener2, TEST_USER_2);
        verify(mListener2, times(1))
                .onHomeVisibilityChanged(/* isVisible= */ true, /* keyguardGoingAway= */ false,
                        /* behindDesktop= */ false);
    }

    @Test
    public void testSetListener_userSwitchedBack_triggersWithPreviousVisibility()
            throws RemoteException {
        ActivityManager.RunningTaskInfo taskInfo = createTaskInfo(1, ACTIVITY_TYPE_HOME);
        // Switch to user with visible home, and register its listener.
        taskInfo.userId = TEST_USER_2;
        TransitionInfo info =
                new TransitionInfoBuilder(TRANSIT_OPEN)
                        .addChange(TRANSIT_OPEN, 0 /* flags */, taskInfo)
                        .build();

        mHomeTransitionObserver.onTransitionReady(mock(IBinder.class),
                info,
                mock(SurfaceControl.Transaction.class),
                mock(SurfaceControl.Transaction.class));
        mHomeTransitionObserver.setHomeTransitionListener(mTransition, mListener2, TEST_USER_2);

        // Switch back to first user with invisible home, and register its listener.
        taskInfo.userId = TEST_USER;
        info = new TransitionInfoBuilder(TRANSIT_TO_BACK)
                .addChange(TRANSIT_TO_BACK, 0 /* flags */, taskInfo)
                .build();
        mHomeTransitionObserver.onTransitionReady(mock(IBinder.class),
                info,
                mock(SurfaceControl.Transaction.class),
                mock(SurfaceControl.Transaction.class));

        verify(mListener, never()) // Not invoked yet (not set).
                .onHomeVisibilityChanged(
                        /* isVisible= */ eq(false), /* keyguardGoingAway= */ anyBoolean(),
                        /* behindDesktop= */ anyBoolean());
        mHomeTransitionObserver.setHomeTransitionListener(mTransition, mListener, TEST_USER);
        verify(mListener, times(1))
                .onHomeVisibilityChanged(/* isVisible= */ false, /* keyguardGoingAway= */ false,
                        /* behindDesktop= */ false);
    }

    @Test
    public void testHomeBecomesVisibleWithKeyguardGoingAway() throws RemoteException {
        ActivityManager.RunningTaskInfo taskInfo = createTaskInfo(1, ACTIVITY_TYPE_HOME);
        TransitionInfo info =
                new TransitionInfoBuilder(TRANSIT_OPEN, TRANSIT_FLAG_KEYGUARD_GOING_AWAY)
                        .addChange(TRANSIT_OPEN, 0 /* flags */, taskInfo)
                        .build();

        mHomeTransitionObserver.onTransitionReady(mock(IBinder.class),
                info,
                mock(SurfaceControl.Transaction.class),
                mock(SurfaceControl.Transaction.class));

        verify(mListener, times(1))
                .onHomeVisibilityChanged(/* isVisible= */ true, /* keyguardGoingAway= */ true,
                        /* behindDesktop= */ false);
    }

    @Test
    public void testHomeBecomesVisibleWhileWaking() throws RemoteException {
        ActivityManager.RunningTaskInfo taskInfo = createTaskInfo(1, ACTIVITY_TYPE_HOME);
        TransitionInfo info =
                new TransitionInfoBuilder(TRANSIT_WAKE)
                        .addChange(TRANSIT_OPEN, 0 /* flags */, taskInfo)
                        .build();

        mHomeTransitionObserver.onTransitionReady(mock(IBinder.class),
                info,
                mock(SurfaceControl.Transaction.class),
                mock(SurfaceControl.Transaction.class));

        verify(mListener, times(1)).onHomeVisibilityChanged(
                /* isVisible= */ true, /* keyguardGoingAwayOrWaking= */ true,
                /* behindDesktop= */ false);
    }

    @Test
    public void testHomeBecomesInvisibleWhileWaking() throws RemoteException {
        ActivityManager.RunningTaskInfo taskInfo = createTaskInfo(1, ACTIVITY_TYPE_HOME);
        TransitionInfo info =
                new TransitionInfoBuilder(TRANSIT_WAKE)
                        .addChange(TRANSIT_TO_BACK, 0 /* flags */, taskInfo)
                        .build();

        mHomeTransitionObserver.onTransitionReady(mock(IBinder.class),
                info,
                mock(SurfaceControl.Transaction.class),
                mock(SurfaceControl.Transaction.class));

        verify(mListener, times(1)).onHomeVisibilityChanged(
                /* isVisible= */ false, /* keyguardGoingAwayOrWaking= */ true,
                /* behindDesktop= */ false);
    }


    private void setTaskIdForMockedDeskChanges(int taskId) {
        when(mDesksOrganizer.isDeskChange(any())).thenAnswer(invocation -> {
            final TransitionInfo.Change change = invocation.getArgument(0);
            if (change == null) {
                return false;
            }
            ActivityManager.RunningTaskInfo taskInfo = change.getTaskInfo();
            if (taskInfo == null) {
                return false;
            }
            return taskInfo.taskId == taskId;
        });
    }

    private static ActivityManager.RunningTaskInfo createTaskInfo(int taskId, int activityType) {
        ActivityManager.RunningTaskInfo taskInfo = new ActivityManager.RunningTaskInfo();
        taskInfo.taskId = taskId;
        taskInfo.topActivityType = activityType;
        taskInfo.configuration.windowConfiguration.setActivityType(activityType);
        taskInfo.token = mock(WindowContainerToken.class);
        taskInfo.isRunning = true;
        return taskInfo;
    }
}
