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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.os.SystemClock;
import android.platform.test.annotations.EnableFlags;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

import androidx.test.filters.SmallTest;

import com.android.wm.shell.Flags;
import com.android.wm.shell.ShellTestCase;
import com.android.wm.shell.common.pip.PipBoundsState;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for {@link PipPinchToResizeHandler}.
 */
@SmallTest
@TestableLooper.RunWithLooper
@EnableFlags(Flags.FLAG_ENABLE_PIP2)
@RunWith(AndroidTestingRunner.class)
public class PipPinchToResizeHandlerTest extends ShellTestCase {

    private PipPinchToResizeHandler mPipPinchToResizeHandler;

    @Mock
    private PipResizeGestureHandler mPipResizeGestureHandler;
    @Mock
    private PipBoundsState mPipBoundsState;
    @Mock
    private PhonePipMenuController mPhonePipMenuController;
    @Mock
    private PipScheduler mPipScheduler;
    @Mock
    private PipInteractionHandler mPipInteractionHandler;

    private PointF mDownPoint;
    private PointF mDownSecondPoint;
    private Rect mDownBounds;
    private PointF mLastPoint;
    private PointF mLastSecondPoint;
    private Rect mLastResizeBounds;
    private float mTouchSlop;
    private Point mMinSize;
    private Point mMaxSize;

    private static final Rect INITIAL_BOUNDS = new Rect(100, 100, 200, 200);

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mPipPinchToResizeHandler = new PipPinchToResizeHandler(mPipResizeGestureHandler,
                mPipBoundsState, mPhonePipMenuController, mPipScheduler, mPipInteractionHandler);

        mDownPoint = new PointF();
        mDownSecondPoint = new PointF();
        mDownBounds = new Rect();
        mLastPoint = new PointF();
        mLastSecondPoint = new PointF();
        mLastResizeBounds = new Rect();
        mTouchSlop = ViewConfiguration.get(mContext).getScaledTouchSlop();
        mMinSize = new Point(50, 50);
        mMaxSize = new Point(500, 500);

        when(mPipBoundsState.getBounds()).thenReturn(INITIAL_BOUNDS);
    }

    private MotionEvent obtainMotionEvent(int action, PointF p1, PointF p2) {
        final MotionEvent.PointerProperties[] properties = new MotionEvent.PointerProperties[2];
        properties[0] = new MotionEvent.PointerProperties();
        properties[0].id = 0;
        properties[1] = new MotionEvent.PointerProperties();
        properties[1].id = 1;

        final MotionEvent.PointerCoords[] coords = new MotionEvent.PointerCoords[2];
        coords[0] = new MotionEvent.PointerCoords();
        coords[0].x = p1.x;
        coords[0].y = p1.y;
        coords[1] = new MotionEvent.PointerCoords();
        coords[1].x = p2.x;
        coords[1].y = p2.y;

        return MotionEvent.obtain(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(),
                action, 2, properties, coords, 0, 0, 1f, 1f, 0, 0, 0, 0);
    }

    @Test
    public void onPinchResize_pointerDownInside_startsGesture() {
        PointF p1 = new PointF(110, 110);
        PointF p2 = new PointF(190, 190);
        MotionEvent ev = obtainMotionEvent(MotionEvent.ACTION_POINTER_DOWN, p1, p2);

        mPipPinchToResizeHandler.onPinchResize(ev, mDownPoint, mDownSecondPoint, mDownBounds,
                mLastPoint, mLastSecondPoint, mLastResizeBounds, mTouchSlop, mMinSize, mMaxSize);

        verify(mPipResizeGestureHandler).setAllowGesture(true);
        verify(mPipInteractionHandler).begin(any(),
                eq(PipInteractionHandler.INTERACTION_PINCHING_PIP));
        verify(mPipResizeGestureHandler).startHighPerfSession();
    }

    @Test
    public void onPinchResize_pointerDownOutside_doesNotStartGesture() {
        PointF p1 = new PointF(10, 10);
        PointF p2 = new PointF(90, 90);
        MotionEvent ev = obtainMotionEvent(MotionEvent.ACTION_POINTER_DOWN, p1, p2);

        mPipPinchToResizeHandler.onPinchResize(ev, mDownPoint, mDownSecondPoint, mDownBounds,
                mLastPoint, mLastSecondPoint, mLastResizeBounds, mTouchSlop, mMinSize, mMaxSize);

        verify(mPipResizeGestureHandler, never()).setAllowGesture(anyBoolean());
        verify(mPipInteractionHandler, never()).begin(any(), anyInt());
    }

    @Test
    public void onPinchResize_moveThresholdNotCrossed_doesNotResize() {
        // Start gesture
        PointF p1Down = new PointF(110, 110);
        PointF p2Down = new PointF(190, 190);
        MotionEvent evDown = obtainMotionEvent(MotionEvent.ACTION_POINTER_DOWN, p1Down, p2Down);
        mPipPinchToResizeHandler.onPinchResize(evDown, mDownPoint, mDownSecondPoint, mDownBounds,
                mLastPoint, mLastSecondPoint, mLastResizeBounds, mTouchSlop, mMinSize, mMaxSize);

        // Move slightly
        PointF p1Move = new PointF(111, 111);
        PointF p2Move = new PointF(189, 189);
        MotionEvent evMove = obtainMotionEvent(MotionEvent.ACTION_MOVE, p1Move, p2Move);
        when(mPipResizeGestureHandler.getThresholdCrossed()).thenReturn(false);

        mPipPinchToResizeHandler.onPinchResize(evMove, mDownPoint, mDownSecondPoint, mDownBounds,
                mLastPoint, mLastSecondPoint, mLastResizeBounds, mTouchSlop, mMinSize, mMaxSize);

        verify(mPipResizeGestureHandler, never()).pilferPointers();
        verify(mPipScheduler, never()).scheduleUserResizePip(any(), anyFloat());
    }

    @Test
    public void onPinchResize_moveThresholdCrossed_resizesAndSetsUserResizedFlag() {
        // Start gesture
        PointF p1Down = new PointF(110, 110);
        PointF p2Down = new PointF(190, 190);
        MotionEvent evDown = obtainMotionEvent(MotionEvent.ACTION_POINTER_DOWN, p1Down, p2Down);
        mPipPinchToResizeHandler.onPinchResize(evDown, mDownPoint, mDownSecondPoint, mDownBounds,
                mLastPoint, mLastSecondPoint, mLastResizeBounds, mTouchSlop, mMinSize, mMaxSize);

        // Move beyond slop
        PointF p1Move = new PointF(110, 110 + mTouchSlop + 1);
        PointF p2Move = new PointF(190, 190);
        MotionEvent evMove = obtainMotionEvent(MotionEvent.ACTION_MOVE, p1Move, p2Move);
        when(mPipResizeGestureHandler.getThresholdCrossed()).thenReturn(false);

        mPipPinchToResizeHandler.onPinchResize(evMove, mDownPoint, mDownSecondPoint, mDownBounds,
                mLastPoint, mLastSecondPoint, mLastResizeBounds, mTouchSlop, mMinSize, mMaxSize);

        verify(mPipResizeGestureHandler).pilferPointers();
        verify(mPipResizeGestureHandler).setThresholdCrossed(true);

        // Now simulate threshold is crossed
        when(mPipResizeGestureHandler.getThresholdCrossed()).thenReturn(true);
        mPipPinchToResizeHandler.onPinchResize(evMove, mDownPoint, mDownSecondPoint, mDownBounds,
                mLastPoint, mLastSecondPoint, mLastResizeBounds, mTouchSlop, mMinSize, mMaxSize);

        verify(mPipScheduler).scheduleUserResizePip(any(), anyFloat());
        verify(mPipBoundsState).setHasUserResizedPip(true);
    }

    @Test
    public void onPinchResize_moveThresholdCrossedAndMenuVisible_hidesMenu() {
        // Start gesture
        PointF p1Down = new PointF(110, 110);
        PointF p2Down = new PointF(190, 190);
        MotionEvent evDown = obtainMotionEvent(MotionEvent.ACTION_POINTER_DOWN, p1Down, p2Down);
        mPipPinchToResizeHandler.onPinchResize(evDown, mDownPoint, mDownSecondPoint, mDownBounds,
                mLastPoint, mLastSecondPoint, mLastResizeBounds, mTouchSlop, mMinSize, mMaxSize);

        // Move beyond slop
        PointF p1Move = new PointF(110, 110 + mTouchSlop + 1);
        PointF p2Move = new PointF(190, 190);
        MotionEvent evMove = obtainMotionEvent(MotionEvent.ACTION_MOVE, p1Move, p2Move);
        when(mPipResizeGestureHandler.getThresholdCrossed()).thenReturn(false);
        when(mPhonePipMenuController.isMenuVisible()).thenReturn(true);

        mPipPinchToResizeHandler.onPinchResize(evMove, mDownPoint, mDownSecondPoint, mDownBounds,
                mLastPoint, mLastSecondPoint, mLastResizeBounds, mTouchSlop, mMinSize, mMaxSize);

        verify(mPhonePipMenuController).hideMenu();
    }

    @Test
    public void onPinchResize_actionUp_endsGestureAndInteraction() {
        // Start gesture
        PointF p1Down = new PointF(110, 110);
        PointF p2Down = new PointF(190, 190);
        MotionEvent evDown = obtainMotionEvent(MotionEvent.ACTION_POINTER_DOWN, p1Down, p2Down);
        mPipPinchToResizeHandler.onPinchResize(evDown, mDownPoint, mDownSecondPoint, mDownBounds,
                mLastPoint, mLastSecondPoint, mLastResizeBounds, mTouchSlop, mMinSize, mMaxSize);

        // Up
        MotionEvent evUp = obtainMotionEvent(MotionEvent.ACTION_UP, p1Down, p2Down);
        mPipPinchToResizeHandler.onPinchResize(evUp, mDownPoint, mDownSecondPoint, mDownBounds,
                mLastPoint, mLastSecondPoint, mLastResizeBounds, mTouchSlop, mMinSize, mMaxSize);

        verify(mPipResizeGestureHandler).setAllowGesture(false);
        verify(mPipResizeGestureHandler).finishResize();
        verify(mPipInteractionHandler).end();
    }
}
