/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.wm.shell.pip2.phone;

import static com.android.wm.shell.common.pip.PipBoundsState.STASH_TYPE_NONE;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.RemoteException;
import android.testing.TestableLooper;
import android.view.SurfaceControl;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.IAccessibilityInteractionConnectionCallback;

import androidx.test.filters.SmallTest;

import com.android.wm.shell.R;
import com.android.wm.shell.ShellTestCase;
import com.android.wm.shell.common.ShellExecutor;
import com.android.wm.shell.common.pip.PipBoundsState;
import com.android.wm.shell.common.pip.PipSnapAlgorithm;
import com.android.wm.shell.pip2.PipSurfaceTransactionHelper;
import com.android.wm.shell.pip2.phone.PipAccessibilityInteractionConnection.AccessibilityCallbacks;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

/**
 * Tests for {@link PipAccessibilityInteractionConnection}
 */
@SmallTest
@TestableLooper.RunWithLooper(setAsMainLooper = true)
@RunWith(android.testing.AndroidTestingRunner.class)
public class PipAccessibilityInteractionConnectionTest extends ShellTestCase {
    private PipAccessibilityInteractionConnection mConnection;

    @Mock private Context mMockContext;
    @Mock private PipMotionHelper mMockMotionHelper;
    @Mock private PipBoundsState mMockPipBoundsState;
    @Mock private PipSnapAlgorithm mMockPipSnapAlgorithm;
    @Mock private PipTransitionState mMockPipTransitionState;
    @Mock private AccessibilityCallbacks mMockCallbacks;
    @Mock private Runnable mMockUnstashCallback;
    @Mock private ShellExecutor mMockMainExecutor;
    @Mock private IAccessibilityInteractionConnectionCallback mMockCallback;
    @Mock private AccessibilityManager mMockAccessibilityManager;
    @Mock private PipScheduler mMockPipScheduler;

    @Mock private PipSurfaceTransactionHelper mMockPipSurfaceTransactionHelper;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mConnection = new PipAccessibilityInteractionConnection(mMockContext,
                mMockPipBoundsState, mMockMotionHelper, mMockPipSnapAlgorithm,
                mMockPipTransitionState, mMockPipScheduler, mMockPipSurfaceTransactionHelper,
                mMockCallbacks, mMockUnstashCallback, mMockMainExecutor);
    }

    @Test
    public void register_registersConnection() {
        mConnection.register(mMockAccessibilityManager);
        verify(mMockAccessibilityManager).setPictureInPictureActionReplacingConnection(
                mConnection.getConnectionImpl());
    }

    @Test
    public void onPipTransitionStateChanged_scheduledBoundsChange_schedulesAnimation() {
        Bundle extra = new Bundle();
        extra.putParcelable(PipAccessibilityInteractionConnection.A11Y_SCHEDULED_PIP_BOUNDS,
                new Rect(0, 0, 10, 10));
        mConnection.onPipTransitionStateChanged(
                PipTransitionState.ENTERING_PIP,
                PipTransitionState.SCHEDULED_BOUNDS_CHANGE,
                extra);
        verify(mMockPipScheduler).scheduleAnimateResizePip(any(Rect.class));
    }

    @Test
    public void onPipTransitionStateChanged_changingPipBounds_schedulesFinishPipBoundsChange() {
        // First, transition to SCHEDULED_BOUNDS_CHANGE to set the waiting flag.
        Bundle extra = new Bundle();
        extra.putParcelable(PipAccessibilityInteractionConnection.A11Y_SCHEDULED_PIP_BOUNDS,
                new Rect(0, 0, 10, 10));
        mConnection.onPipTransitionStateChanged(
                PipTransitionState.ENTERING_PIP,
                PipTransitionState.SCHEDULED_BOUNDS_CHANGE,
                extra);

        // Now, transition to CHANGING_PIP_BOUNDS to trigger the finish call.
        Bundle changingExtra = new Bundle();
        Rect destinationBounds = new Rect(0, 0, 20, 20);
        changingExtra.putParcelable(PipTransition.PIP_DESTINATION_BOUNDS, destinationBounds);
        changingExtra.putParcelable(PipTransition.PIP_START_TX,
                mock(SurfaceControl.Transaction.class));
        changingExtra.putParcelable(PipTransition.PIP_FINISH_TX,
                mock(SurfaceControl.Transaction.class));
        when(mMockPipTransitionState.getPinnedTaskLeash()).thenReturn(mock(SurfaceControl.class));
        when(mMockPipSurfaceTransactionHelper.round(any(), any(), anyBoolean()))
                .thenReturn(mMockPipSurfaceTransactionHelper);
        when(mMockPipSurfaceTransactionHelper.shadow(any(), any(), anyBoolean()))
                .thenReturn(mMockPipSurfaceTransactionHelper);
        mConnection.onPipTransitionStateChanged(
                PipTransitionState.SCHEDULED_BOUNDS_CHANGE,
                PipTransitionState.CHANGING_PIP_BOUNDS,
                changingExtra);

        verify(mMockPipScheduler).scheduleFinishPipBoundsChange(destinationBounds);
    }

    @Test
    public void obtainRootAccessibilityNodeInfo_returnsNodeWithActions() {
        when(mMockContext.getString(anyInt())).thenReturn("Test Action");
        AccessibilityNodeInfo info =
                PipAccessibilityInteractionConnection.obtainRootAccessibilityNodeInfo(mMockContext);

        assertNotNull(info);
        List<AccessibilityNodeInfo.AccessibilityAction> actions = info.getActionList();
        assertTrue(actions.contains(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK));
        assertTrue(actions.contains(AccessibilityNodeInfo.AccessibilityAction.ACTION_DISMISS));
        assertTrue(actions.contains(AccessibilityNodeInfo.AccessibilityAction.ACTION_EXPAND));
        assertTrue(actions.stream().anyMatch(a -> a.getId() == R.id.action_pip_resize));
        assertTrue(actions.stream().anyMatch(a -> a.getId() == R.id.action_pip_stash));
        assertTrue(actions.stream().anyMatch(a -> a.getId() == R.id.action_pip_unstash));
    }

    @Test
    public void findAccessibilityNodeInfoByAccessibilityId_rootNode_returnsNodeList()
            throws RemoteException {
        when(mMockContext.getString(anyInt())).thenReturn("Test Action");

        mConnection.getConnectionImpl().findAccessibilityNodeInfoByAccessibilityId(
                AccessibilityNodeInfo.ROOT_NODE_ID, null, 1, mMockCallback, 0, 0, 0, null, null,
                null);

        ArgumentCaptor<List<AccessibilityNodeInfo>> captor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(mMockMainExecutor).execute(runnableCaptor.capture());
        runnableCaptor.getValue().run();
        verify(mMockCallback).setFindAccessibilityNodeInfosResult(captor.capture(), eq(1));
        assertEquals(1, captor.getValue().size());
    }

    @Test
    public void findAccessibilityNodeInfoByAccessibilityId_nonRootNode_returnsNull()
            throws RemoteException {
        mConnection.getConnectionImpl().findAccessibilityNodeInfoByAccessibilityId(
                123L, null, 1, mMockCallback, 0, 0, 0, null, null, null);

        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(mMockMainExecutor).execute(runnableCaptor.capture());
        runnableCaptor.getValue().run();
        verify(mMockCallback).setFindAccessibilityNodeInfosResult(eq(null), eq(1));
    }

    @Test
    public void performAccessibilityAction_click_showsMenu() throws RemoteException {
        mConnection.getConnectionImpl().performAccessibilityAction(
                AccessibilityNodeInfo.ROOT_NODE_ID,
                AccessibilityNodeInfo.ACTION_CLICK, null, 1, mMockCallback, 0, 0, 0);

        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(mMockMainExecutor).execute(runnableCaptor.capture());
        runnableCaptor.getValue().run();
        verify(mMockCallbacks).onAccessibilityShowMenu();
        verify(mMockCallback).setPerformAccessibilityActionResult(true, 1);
    }

    @Test
    public void performAccessibilityAction_dismiss_dismissesPip() throws RemoteException {
        mConnection.getConnectionImpl().performAccessibilityAction(
                AccessibilityNodeInfo.ROOT_NODE_ID,
                AccessibilityNodeInfo.ACTION_DISMISS, null, 1, mMockCallback, 0, 0, 0);

        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(mMockMainExecutor).execute(runnableCaptor.capture());
        runnableCaptor.getValue().run();
        verify(mMockMotionHelper).dismissPip();
        verify(mMockCallback).setPerformAccessibilityActionResult(true, 1);
    }

    @Test
    public void performAccessibilityAction_expand_expandsPip() throws RemoteException {
        mConnection.getConnectionImpl().performAccessibilityAction(
                AccessibilityNodeInfo.ROOT_NODE_ID,
                AccessibilityNodeInfo.ACTION_EXPAND, null, 1, mMockCallback, 0, 0, 0);

        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(mMockMainExecutor).execute(runnableCaptor.capture());
        runnableCaptor.getValue().run();
        verify(mMockMotionHelper).expandLeavePip(false);
        verify(mMockCallback).setPerformAccessibilityActionResult(true, 1);
    }

    @Test
    public void performAccessibilityAction_stash_stashesPip() throws RemoteException {
        mConnection.getConnectionImpl().performAccessibilityAction(
                AccessibilityNodeInfo.ROOT_NODE_ID,
                R.id.action_pip_stash, null, 1, mMockCallback, 0, 0, 0);

        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(mMockMainExecutor).execute(runnableCaptor.capture());
        runnableCaptor.getValue().run();
        verify(mMockMotionHelper).animateToStashedClosestEdge();
        verify(mMockCallback).setPerformAccessibilityActionResult(true, 1);
    }

    @Test
    public void performAccessibilityAction_unstash_unstashesPip() throws RemoteException {
        mConnection.getConnectionImpl().performAccessibilityAction(
                AccessibilityNodeInfo.ROOT_NODE_ID,
                R.id.action_pip_unstash, null, 1, mMockCallback, 0, 0, 0);

        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(mMockMainExecutor).execute(runnableCaptor.capture());
        runnableCaptor.getValue().run();
        verify(mMockUnstashCallback).run();
        verify(mMockPipBoundsState).setStashed(STASH_TYPE_NONE);
        verify(mMockCallback).setPerformAccessibilityActionResult(true, 1);
    }

    @Test
    public void performAccessibilityAction_resizeFromNormal_triggersExpandedBounds()
            throws RemoteException {
        Rect normalBounds = new Rect(0, 0, 100, 100);
        Rect expandedBounds = new Rect(0, 0, 200, 200);
        Rect normalMovementBounds = new Rect(0, 0, 800, 800);
        Rect expandedMovementBounds = new Rect(0, 0, 600, 600);
        mConnection.onMovementBoundsChanged(normalBounds, expandedBounds, normalMovementBounds,
                expandedMovementBounds);
        when(mMockPipBoundsState.getBounds()).thenReturn(new Rect(normalBounds));

        mConnection.getConnectionImpl().performAccessibilityAction(
                AccessibilityNodeInfo.ROOT_NODE_ID,
                R.id.action_pip_resize, null, 1, mMockCallback, 0, 0, 0);

        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(mMockMainExecutor).execute(runnableCaptor.capture());
        runnableCaptor.getValue().run();
        verify(mMockPipTransitionState).setOnIdlePipTransitionStateRunnable(any());
        verify(mMockPipSnapAlgorithm).getSnapFraction(any(), eq(normalMovementBounds));
        verify(mMockPipSnapAlgorithm).applySnapFraction(eq(expandedBounds),
                eq(expandedMovementBounds), anyFloat());
        verify(mMockCallback).setPerformAccessibilityActionResult(true, 1);
    }

    @Test
    public void performAccessibilityAction_resizeFromExpanded_triggersNormalBounds()
            throws RemoteException {
        Rect normalBounds = new Rect(0, 0, 100, 100);
        Rect expandedBounds = new Rect(0, 0, 200, 200);
        Rect normalMovementBounds = new Rect(0, 0, 800, 800);
        Rect expandedMovementBounds = new Rect(0, 0, 600, 600);
        mConnection.onMovementBoundsChanged(normalBounds, expandedBounds, normalMovementBounds,
                expandedMovementBounds);
        when(mMockPipBoundsState.getBounds()).thenReturn(new Rect(expandedBounds));

        mConnection.getConnectionImpl().performAccessibilityAction(
                AccessibilityNodeInfo.ROOT_NODE_ID,
                R.id.action_pip_resize, null, 1, mMockCallback, 0, 0, 0);

        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(mMockMainExecutor).execute(runnableCaptor.capture());
        runnableCaptor.getValue().run();
        verify(mMockPipTransitionState).setOnIdlePipTransitionStateRunnable(any());
        verify(mMockPipSnapAlgorithm).getSnapFraction(any(), eq(expandedMovementBounds));
        verify(mMockPipSnapAlgorithm).applySnapFraction(eq(normalBounds),
                eq(normalMovementBounds), anyFloat());
        verify(mMockCallback).setPerformAccessibilityActionResult(true, 1);
    }
}
