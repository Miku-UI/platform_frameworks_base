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

package com.android.systemui.accessibility;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.os.Handler;
import android.os.SystemClock;
import android.platform.test.annotations.EnableFlags;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import androidx.test.core.view.PointerCoordsBuilder;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.systemui.Flags;
import com.android.systemui.SysuiTestCase;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class MagnificationGestureDetectorTest extends SysuiTestCase {

    private static final int ACTION_DOWN_X = 100;
    private static final int ACTION_DOWN_Y = 200;
    private final int mTouchSlop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
    private MagnificationGestureDetector mGestureDetector;
    private final MotionEventHelper mMotionEventHelper = new MotionEventHelper();
    private View mSpyView;
    @Mock
    private MagnificationGestureDetector.OnGestureListener mListener;
    @Mock
    private Handler mHandler;
    private Runnable mCancelSingleTapRunnable;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        doAnswer((invocation) -> {
            mCancelSingleTapRunnable = invocation.getArgument(0);
            return null;
        }).when(mHandler).postAtTime(any(Runnable.class), anyLong());
        mGestureDetector = new MagnificationGestureDetector(mContext, mHandler, mListener);
        mSpyView = Mockito.spy(new View(mContext));
    }

    @After
    public void tearDown() {
        mMotionEventHelper.recycleEvents();
    }

    @Test
    public void onActionDown_invokeDownCallback() {
        final long downTime = SystemClock.uptimeMillis();
        final MotionEvent downEvent = mMotionEventHelper.obtainMotionEvent(downTime, downTime,
                MotionEvent.ACTION_DOWN, ACTION_DOWN_X, ACTION_DOWN_Y);

        mGestureDetector.onTouch(mSpyView, downEvent);

        verify(mListener).onStart();
    }

    @Test
    public void performSingleTap_invokeCallbacksInOrder() {
        final long downTime = SystemClock.uptimeMillis();
        final MotionEvent downEvent = mMotionEventHelper.obtainMotionEvent(downTime, downTime,
                MotionEvent.ACTION_DOWN, ACTION_DOWN_X, ACTION_DOWN_Y);
        final MotionEvent upEvent = mMotionEventHelper.obtainMotionEvent(downTime, downTime,
                MotionEvent.ACTION_UP, ACTION_DOWN_X, ACTION_DOWN_Y);

        mGestureDetector.onTouch(mSpyView, downEvent);
        mGestureDetector.onTouch(mSpyView, upEvent);

        InOrder inOrder = Mockito.inOrder(mListener);
        inOrder.verify(mListener).onStart();
        inOrder.verify(mListener).onSingleTap(mSpyView);
        inOrder.verify(mListener).onFinish();
        verify(mListener, never()).onDrag(eq(mSpyView), anyInt(), anyInt());
    }

    @Test
    public void performSingleTapWithActionCancel_notInvokeOnSingleTapCallback() {
        final long downTime = SystemClock.uptimeMillis();
        final MotionEvent downEvent = mMotionEventHelper.obtainMotionEvent(downTime, downTime,
                MotionEvent.ACTION_DOWN, ACTION_DOWN_X, ACTION_DOWN_Y);
        final MotionEvent cancelEvent = mMotionEventHelper.obtainMotionEvent(downTime, downTime,
                MotionEvent.ACTION_CANCEL, ACTION_DOWN_X, ACTION_DOWN_Y);

        mGestureDetector.onTouch(mSpyView, downEvent);
        mGestureDetector.onTouch(mSpyView, cancelEvent);

        verify(mListener, never()).onSingleTap(mSpyView);
    }

    @Test
    public void performSingleTapWithTwoPointers_notInvokeSingleTapCallback() {
        final long downTime = SystemClock.uptimeMillis();
        final MotionEvent downEvent = mMotionEventHelper.obtainMotionEvent(downTime, downTime,
                MotionEvent.ACTION_DOWN, ACTION_DOWN_X, ACTION_DOWN_Y);
        final MotionEvent upEvent = mMotionEventHelper.obtainMotionEvent(downTime, downTime,
                MotionEvent.ACTION_POINTER_DOWN, ACTION_DOWN_X, ACTION_DOWN_Y);

        mGestureDetector.onTouch(mSpyView, downEvent);
        mGestureDetector.onTouch(mSpyView, upEvent);

        verify(mListener, never()).onSingleTap(mSpyView);
    }

    @Test
    public void performSingleTapWithBatchedSmallTouchEvents_invokeCallback() {
        mGestureDetector = new MagnificationGestureDetector(mContext, mHandler, mListener);

        final long downTime = SystemClock.uptimeMillis();
        final MotionEvent.PointerCoords coords = PointerCoordsBuilder.newBuilder()
                .setCoords(ACTION_DOWN_X, ACTION_DOWN_Y).build();

        final MotionEvent downEvent = mMotionEventHelper.obtainBatchedMotionEvent(downTime,
                MotionEvent.ACTION_DOWN, InputDevice.SOURCE_TOUCHSCREEN,
                MotionEvent.TOOL_TYPE_FINGER, coords);

        // Move event without changing location.
        final MotionEvent.PointerCoords dragCoords1 = PointerCoordsBuilder.newBuilder()
                .setCoords(ACTION_DOWN_X + mTouchSlop / 2.f, ACTION_DOWN_Y).build();
        final MotionEvent.PointerCoords dragCoords2 = new MotionEvent.PointerCoords(dragCoords1);
        final MotionEvent moveEvent = mMotionEventHelper.obtainBatchedMotionEvent(downTime,
                MotionEvent.ACTION_MOVE, InputDevice.SOURCE_TOUCHSCREEN,
                MotionEvent.TOOL_TYPE_FINGER, dragCoords1, dragCoords2);

        final MotionEvent upEvent = mMotionEventHelper.obtainBatchedMotionEvent(downTime,
                MotionEvent.ACTION_UP, InputDevice.SOURCE_TOUCHSCREEN, MotionEvent.TOOL_TYPE_FINGER,
                coords);

        mGestureDetector.onTouch(mSpyView, downEvent);
        mGestureDetector.onTouch(mSpyView, moveEvent);
        mGestureDetector.onTouch(mSpyView, upEvent);

        verify(mListener).onSingleTap(mSpyView);
    }

    @Test
    public void performLongPress_invokeCallbacksInOrder() {
        final long downTime = SystemClock.uptimeMillis();
        final MotionEvent downEvent = mMotionEventHelper.obtainMotionEvent(downTime, downTime,
                MotionEvent.ACTION_DOWN, ACTION_DOWN_X, ACTION_DOWN_Y);
        final MotionEvent upEvent = mMotionEventHelper.obtainMotionEvent(downTime, downTime,
                MotionEvent.ACTION_UP, ACTION_DOWN_X, ACTION_DOWN_Y);

        mGestureDetector.onTouch(mSpyView, downEvent);
        // Execute the pending message for stopping single-tap detection.
        mCancelSingleTapRunnable.run();
        mGestureDetector.onTouch(mSpyView, upEvent);

        InOrder inOrder = Mockito.inOrder(mListener);
        inOrder.verify(mListener).onStart();
        inOrder.verify(mListener).onFinish();
        verify(mListener, never()).onSingleTap(mSpyView);
    }

    @Test
    public void performDrag_invokeCallbacksInOrder() {
        final long downTime = SystemClock.uptimeMillis();
        final int dragOffset = mTouchSlop + 10;
        final MotionEvent downEvent = mMotionEventHelper.obtainMotionEvent(downTime, downTime,
                MotionEvent.ACTION_DOWN, ACTION_DOWN_X, ACTION_DOWN_Y);
        final MotionEvent moveEvent = mMotionEventHelper.obtainMotionEvent(downTime, downTime,
                MotionEvent.ACTION_MOVE, ACTION_DOWN_X + dragOffset, ACTION_DOWN_Y);
        final MotionEvent upEvent = mMotionEventHelper.obtainMotionEvent(downTime, downTime,
                MotionEvent.ACTION_UP, ACTION_DOWN_X, ACTION_DOWN_Y);

        mGestureDetector.onTouch(mSpyView, downEvent);
        mGestureDetector.onTouch(mSpyView, moveEvent);
        mGestureDetector.onTouch(mSpyView, upEvent);

        InOrder inOrder = Mockito.inOrder(mListener);
        inOrder.verify(mListener).onStart();
        inOrder.verify(mListener).onDrag(mSpyView, dragOffset, 0);
        inOrder.verify(mListener).onFinish();
        verify(mListener, never()).onSingleTap(mSpyView);
    }

    @Test
    @EnableFlags(Flags.FLAG_WINDOW_MAGNIFICATION_MOVE_WITH_MOUSE_ON_EDGE)
    public void performDragWithMouse_invokeCallbacksUsingRelative() {
        mGestureDetector = new MagnificationGestureDetector(mContext, mHandler, mListener);

        final long downTime = SystemClock.uptimeMillis();
        final MotionEvent.PointerCoords coords = PointerCoordsBuilder.newBuilder()
                .setCoords(ACTION_DOWN_X, ACTION_DOWN_Y).build();

        final MotionEvent downEvent = mMotionEventHelper.obtainBatchedMotionEvent(downTime,
                MotionEvent.ACTION_DOWN, InputDevice.SOURCE_MOUSE, MotionEvent.TOOL_TYPE_MOUSE,
                coords);

        // Drag event without changing location but with relative delta on X axis.
        final MotionEvent.PointerCoords dragCoords1 = new MotionEvent.PointerCoords(coords);
        dragCoords1.setAxisValue(MotionEvent.AXIS_RELATIVE_X, mTouchSlop + 10);
        final MotionEvent.PointerCoords dragCoords2 = new MotionEvent.PointerCoords(coords);
        dragCoords2.setAxisValue(MotionEvent.AXIS_RELATIVE_X, 20);
        final MotionEvent moveEvent = mMotionEventHelper.obtainBatchedMotionEvent(downTime,
                MotionEvent.ACTION_MOVE, InputDevice.SOURCE_MOUSE, MotionEvent.TOOL_TYPE_MOUSE,
                dragCoords1, dragCoords2);

        final MotionEvent upEvent = mMotionEventHelper.obtainBatchedMotionEvent(downTime,
                MotionEvent.ACTION_UP, InputDevice.SOURCE_MOUSE, MotionEvent.TOOL_TYPE_MOUSE,
                coords);

        mGestureDetector.onTouch(mSpyView, downEvent);
        mGestureDetector.onTouch(mSpyView, moveEvent);
        mGestureDetector.onTouch(mSpyView, upEvent);

        verify(mListener).onStart();
        verify(mListener).onDrag(mSpyView, mTouchSlop + 30, 0);
        verify(mListener).onFinish();
    }

    @Test
    public void dragWithFractionalOffsets_totalDragOffsetIsCorrect() {
        final long downTime = SystemClock.uptimeMillis();
        final float dragOffsetX = 0.6f;
        final float dragOffsetY = -0.6f;
        final int dragCount = 4;
        final float startX = ACTION_DOWN_X;
        final float startY = ACTION_DOWN_Y;

        final MotionEvent downEvent = mMotionEventHelper.obtainMotionEvent(downTime, downTime,
                MotionEvent.ACTION_DOWN, startX, startY);
        mGestureDetector.onTouch(mSpyView, downEvent);

        final float firstMoveX = startX + mTouchSlop + 1;
        final float firstMoveY = startY - mTouchSlop - 1;
        final MotionEvent firstMoveEvent = mMotionEventHelper.obtainMotionEvent(downTime, downTime,
                MotionEvent.ACTION_MOVE, firstMoveX, firstMoveY);
        mGestureDetector.onTouch(mSpyView, firstMoveEvent);

        for (int i = 1; i <= dragCount; i++) {
            final MotionEvent moveEvent = mMotionEventHelper.obtainMotionEvent(downTime, downTime,
                    MotionEvent.ACTION_MOVE, firstMoveX + dragOffsetX * i,
                    firstMoveY + dragOffsetY * i);
            mGestureDetector.onTouch(mSpyView, moveEvent);
        }

        final MotionEvent upEvent = mMotionEventHelper.obtainMotionEvent(downTime, downTime,
                MotionEvent.ACTION_UP, firstMoveX + dragOffsetX * dragCount,
                firstMoveY + dragOffsetY * dragCount);
        mGestureDetector.onTouch(mSpyView, upEvent);

        ArgumentCaptor<Integer> offsetXCaptor = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> offsetYCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(mListener, times(dragCount + 1)).onDrag(eq(mSpyView), offsetXCaptor.capture(),
                offsetYCaptor.capture());

        int totalOffsetX = offsetXCaptor.getAllValues().stream().mapToInt(Integer::intValue).sum();
        int totalOffsetY = offsetYCaptor.getAllValues().stream().mapToInt(Integer::intValue).sum();
        int expectedTotalOffsetX = (int) (mTouchSlop + 1 + dragOffsetX * dragCount);
        int expectedTotalOffsetY = (int) (-mTouchSlop - 1 + dragOffsetY * dragCount);
        assertEquals("Total X offset should be accumulated correctly",
                expectedTotalOffsetX, totalOffsetX);
        assertEquals("Total Y offset should be accumulated correctly",
                expectedTotalOffsetY, totalOffsetY);
    }
}
