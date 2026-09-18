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

package com.android.wm.shell.transition;

import static android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM;
import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;
import static android.view.WindowManager.TRANSIT_CHANGE;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.ActivityManager;
import android.graphics.PointF;
import android.graphics.Rect;
import android.view.Display;
import android.view.SurfaceControl;
import android.window.TransitionInfo;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.wm.shell.ShellTestCase;
import com.android.wm.shell.TestShellExecutor;
import com.android.wm.shell.common.DisplayController;
import com.android.wm.shell.shared.TransactionPool;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Optional;

/**
 * Tests for {@link DisplayMoveAnimation}.
 *
 * atest WMShellUnitTests:DisplayMoveAnimationTest
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class DisplayMoveAnimationTest extends ShellTestCase {
    private final TransactionPool mTransactionPool = mock(TransactionPool.class);
    private final DisplayController mDisplayController = mock(DisplayController.class);
    private final TestShellExecutor mMainExecutor = new TestShellExecutor();

    private DisplayMoveAnimation mDisplayMoveAnimation;

    @Before
    public void setUp() {
        mContext = spy(mContext);
        Display display = mock(Display.class);
        when(display.getType()).thenReturn(Display.TYPE_INTERNAL);
        when(mContext.getDisplayNoVerify()).thenReturn(display);
        mContext.getOrCreateTestableResources().addOverride(
                com.android.internal.R.bool.config_supportsRoundedCornersOnWindows, true);
        mContext.getOrCreateTestableResources().addOverride(
                com.android.internal.R.dimen.rounded_corner_radius, 20);
        when(mTransactionPool.acquire()).thenReturn(mock(SurfaceControl.Transaction.class));
        mDisplayMoveAnimation = new DisplayMoveAnimation(mTransactionPool, mDisplayController);
        when(mDisplayController.getRelativeTranslationDp(anyInt(), anyInt(), any(), any(),
                anyFloat())).thenReturn(new PointF(1, 0));
        when(mDisplayController.convertDpVectorToPxVector(anyInt(), any()))
                .thenReturn(new PointF(1, 0));
        when(mDisplayController.getDisplayContext(anyInt())).thenReturn(mContext);
    }

    @Test
    public void testAnimations() {
        // No snapshot
        final TransitionInfo.Change change = createTestChange(WINDOWING_MODE_FULLSCREEN);
        final SurfaceControl.Transaction startT = mock(SurfaceControl.Transaction.class);
        TransitionInfo info = createTestInfo(change);

        Optional<WindowAnimation> anim = mDisplayMoveAnimation.startAnimation(
                startT, change, info, (winAnim) -> {}, mMainExecutor);

        assertTrue(anim.isPresent());

        // With Snapshot
        final SurfaceControl snapshot = mock(SurfaceControl.class);
        change.setSnapshot(snapshot, 0);
        info = createTestInfo(change);

        anim = mDisplayMoveAnimation.startAnimation(
                startT, change, info, (winAnim) -> {}, mMainExecutor);

        assertTrue(anim.isPresent());
    }

    @Test
    public void testCornerRadius_fullscreen() {
        final TransitionInfo.Change change = createTestChange(WINDOWING_MODE_FULLSCREEN);
        final TransitionInfo info = createTestInfo(change);
        final SurfaceControl.Transaction startT = mock(SurfaceControl.Transaction.class);

        mDisplayMoveAnimation.startAnimation(startT, change, info, (winAnim) -> {}, mMainExecutor);

        // Verify corners are set for fullscreen
        verify(startT).setCornerRadius(eq(change.getLeash()), anyFloat());
    }

    @Test
    public void testCornerRadius_freeform() {
        final TransitionInfo.Change change = createTestChange(WINDOWING_MODE_FREEFORM);
        final TransitionInfo info = createTestInfo(change);
        final SurfaceControl.Transaction startT = mock(SurfaceControl.Transaction.class);

        mDisplayMoveAnimation.startAnimation(startT, change, info, (winAnim) -> {}, mMainExecutor);

        // Verify corners are NOT set for freeform
        verify(startT, never()).setCornerRadius(eq(change.getLeash()), anyFloat());
    }

    @Test
    public void testCoordinateMath() {
        final TransitionInfo.Change change = createTestChange(WINDOWING_MODE_FULLSCREEN);
        final TransitionInfo info = createTestInfo(change);
        final SurfaceControl.Transaction startT = mock(SurfaceControl.Transaction.class);

        PointF vector = new PointF(100f, 200f);
        when(mDisplayController.getRelativeTranslationDp(anyInt(), anyInt(), any(), any(),
                anyFloat())).thenReturn(new PointF(1f, 1f));
        when(mDisplayController.convertDpVectorToPxVector(anyInt(), any()))
                .thenReturn(vector);

        mDisplayMoveAnimation.startAnimation(startT, change, info, (winAnim) -> {}, mMainExecutor);

        // endPos is (0,0) based on createTestChange bounds and root offset
        // Verify initial position is (0 - 100, 0 - 200)
        verify(startT).setPosition(eq(change.getLeash()), eq(-100f), eq(-200f));
    }

    private TransitionInfo.Change createTestChange(int windowingMode) {
        ActivityManager.RunningTaskInfo taskInfo = new ActivityManager.RunningTaskInfo();
        taskInfo.configuration.windowConfiguration.setWindowingMode(windowingMode);
        TransitionInfo.Change change = new TransitionInfo.Change(null, mock(SurfaceControl.class));
        change.setTaskInfo(taskInfo);
        change.setDisplayId(0, 1);
        change.setEndAbsBounds(new Rect(0, 0, 1000, 1000));
        change.setStartAbsBounds(new Rect(0, 0, 1000, 1000));
        return change;
    }

    private TransitionInfo createTestInfo(TransitionInfo.Change change) {
        TransitionInfo info = new TransitionInfo(TRANSIT_CHANGE, 0);
        info.addChange(change);
        info.addRootLeash(0, mock(SurfaceControl.class), 0, 0);
        info.addRootLeash(1, mock(SurfaceControl.class), 0, 0);
        return info;
    }
}
