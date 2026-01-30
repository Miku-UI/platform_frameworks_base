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

import android.annotation.DisplayContext;
import android.annotation.NonNull;
import android.content.Context;
import android.graphics.Point;
import android.graphics.PointF;
import android.os.Handler;
import android.view.Display;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import com.android.systemui.Flags;

/**
 * Detects single tap and drag gestures using the supplied {@link MotionEvent}s. The {@link
 * OnGestureListener} callback will notify users when a particular motion event has occurred. This
 * class should only be used with {@link MotionEvent}s reported via touch (don't use for trackball
 * events).
 */
class MagnificationGestureDetector {

    interface OnGestureListener {
        /**
         * Called when a tap is completed within {@link ViewConfiguration#getLongPressTimeout()} and
         * the offset between {@link MotionEvent}s and the down event doesn't exceed {@link
         * ViewConfiguration#getScaledTouchSlop()}.
         *
         * @return {@code true} if this gesture is handled.
         */
        boolean onSingleTap(View view);

        /**
         * Called when the user is performing dragging gesture. It is started after the offset
         * between the down location and the move event location exceed
         * {@link ViewConfiguration#getScaledTouchSlop()}.
         *
         * @param offsetX The X offset in screen coordinate.
         * @param offsetY The Y offset in screen coordinate.
         * @return {@code true} if this gesture is handled.
         */
        boolean onDrag(View view, int offsetX, int offsetY);

        /**
         * Notified when a tap occurs with the down {@link MotionEvent} that triggered it. This will
         * be triggered immediately for every down event. All other events should be preceded by
         * this.
         *
         * @return {@code true} if the down event is handled, otherwise the events won't be sent to
         * the view.
         */
        boolean onStart();

        /**
         * Called when the detection is finished. In other words, it is called when up/cancel {@link
         * MotionEvent} is received. It will be triggered after single-tap.
         *
         * @return {@code true} if the event is handled.
         */
        boolean onFinish();
    }

    @NonNull
    private final MotionAccumulator mAccumulator;
    private final Handler mHandler;
    private final Runnable mCancelTapGestureRunnable;
    private final OnGestureListener mOnGestureListener;
    // Assume the gesture default is a single-tap. Set it to false if the gesture couldn't be a
    // single-tap anymore.
    private boolean mDetectSingleTap = true;
    private boolean mDraggingDetected = false;

    /**
     * @param context  {@link Context} that is from {@link Context#createDisplayContext(Display)}.
     * @param handler  The handler to post the runnable.
     * @param listener The listener invoked for all the callbacks.
     */
    MagnificationGestureDetector(@DisplayContext Context context, @NonNull Handler handler,
            @NonNull OnGestureListener listener) {
        mAccumulator = new MotionAccumulator(ViewConfiguration.get(context).getScaledTouchSlop());
        mHandler = handler;
        mOnGestureListener = listener;
        mCancelTapGestureRunnable = () -> mDetectSingleTap = false;
    }

    /**
     * Analyzes the given motion event and if applicable to trigger the appropriate callbacks on the
     * {@link OnGestureListener} supplied.
     *
     * @param event The current motion event.
     * @return {@code True} if the {@link OnGestureListener} consumes the event, else false.
     */
    boolean onTouch(View view, MotionEvent event) {
        mAccumulator.onMotionEvent(event);
        boolean handled = false;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mHandler.postAtTime(mCancelTapGestureRunnable,
                        event.getDownTime() + ViewConfiguration.getLongPressTimeout());
                handled |= mOnGestureListener.onStart();
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                stopSingleTapDetection();
                break;
            case MotionEvent.ACTION_MOVE:
                stopSingleTapDetectionIfNeeded();
                handled |= notifyDraggingGestureIfNeeded(view);
                break;
            case MotionEvent.ACTION_UP:
                stopSingleTapDetectionIfNeeded();
                if (mDetectSingleTap) {
                    handled |= mOnGestureListener.onSingleTap(view);
                }
                // Fall through
            case MotionEvent.ACTION_CANCEL:
                handled |= mOnGestureListener.onFinish();
                reset();
                break;
        }
        return handled;
    }

    private void stopSingleTapDetectionIfNeeded() {
        if (mDraggingDetected) {
            return;
        }

        if (mAccumulator.isDraggingDetected()) {
            mDraggingDetected = true;
            stopSingleTapDetection();
        }
    }

    private void stopSingleTapDetection() {
        mHandler.removeCallbacks(mCancelTapGestureRunnable);
        mDetectSingleTap = false;
    }

    private boolean notifyDraggingGestureIfNeeded(View view) {
        if (!mDraggingDetected) {
            return false;
        }
        final Point delta = mAccumulator.getAndConsumeDelta();
        return mOnGestureListener.onDrag(view, delta.x, delta.y);
    }

    private void reset() {
        mAccumulator.reset();
        mHandler.removeCallbacks(mCancelTapGestureRunnable);
        mDetectSingleTap = true;
        mDraggingDetected = false;
    }

    /**
     * A helper class to accumulate raw motion events and determine if a dragging gesture is
     * happening. It provides the delta between events for the client to perform dragging actions.
     *
     * <p>For dragging actions, the UI uses integer values for pixel offsets. This class accumulates
     * the gesture's relative offset as a floating-point value. To avoid accumulated errors from
     * float-to-int conversions, the class keeps the fractional part of the offset internally. It
     * only reports the integer part of the offset to event handlers via {@link #getDeltaX()} and
     * {@link #getDeltaY()}, and the consumed integer offset is then subtracted from the internal
     * accumulated offset (see b/436696444).
     */
    private static class MotionAccumulator {
        private final PointF mAccumulatedDelta = new PointF(Float.NaN, Float.NaN);
        private final PointF mLastLocation = new PointF(Float.NaN, Float.NaN);
        private final int mTouchSlopSquare;

        /**
         * @param touchSlop Distance a touch can wander before becoming a drag.
         */
        MotionAccumulator(int touchSlop) {
            mTouchSlopSquare = touchSlop * touchSlop;
        }

        /**
         * Processes a {@link MotionEvent} to accumulate the gesture's deltas.
         *
         * <p>This method tracks the movement difference between events. For touch events, it's the
         * change in raw screen coordinates. For mouse events, it uses the relative motion axes
         * ({@link MotionEvent#AXIS_RELATIVE_X} and {@link MotionEvent#AXIS_RELATIVE_Y}) to support
         * dragging even when the pointer is at the screen edge.
         *
         * @param event The motion event to process.
         */
        void onMotionEvent(MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    mAccumulatedDelta.set(0, 0);
                    mLastLocation.set(event.getRawX(), event.getRawY());
                    break;
                case MotionEvent.ACTION_MOVE:
                case MotionEvent.ACTION_UP:
                    float dx = 0;
                    float dy = 0;
                    if (Flags.windowMagnificationMoveWithMouseOnEdge() && isMouseEvent(event)) {
                        // With mouse input, we use relative delta values so that user can drag
                        // even at the edge of the screen, where the pointer location doesn't change
                        // but input event still contain the delta value.
                        for (int i = 0; i < event.getHistorySize(); i++) {
                            dx += event.getHistoricalAxisValue(MotionEvent.AXIS_RELATIVE_X, i);
                            dy += event.getHistoricalAxisValue(MotionEvent.AXIS_RELATIVE_Y, i);
                        }
                        dx += event.getAxisValue(MotionEvent.AXIS_RELATIVE_X);
                        dy += event.getAxisValue(MotionEvent.AXIS_RELATIVE_Y);
                    } else {
                        dx = event.getRawX() - mLastLocation.x;
                        dy = event.getRawY() - mLastLocation.y;
                    }
                    mAccumulatedDelta.offset(dx, dy);
                    mLastLocation.set(event.getRawX(), event.getRawY());
                    break;
            }
        }

        /**
         * Gets the delta X of accumulated motions, or zero if no motion is added.
         *
         * <p>Please note, when reporting delta offset, it uses casting so that the offset is always
         * truncated and the fractional part could be accumulated and used with future moves.
         *
         * @return The X offset of the accumulated motions.
         */
        int getDeltaX() {
            return (int) mAccumulatedDelta.x;
        }

        /**
         * Gets the delta Y of accumulated motions, or zero if no motion is added.
         *
         * <p>Please note, when reporting delta offset, it uses casting so that the offset is always
         * truncated and the fractional part could be accumulated and used with future moves.
         *
         * @return The Y offset of the accumulated motions.
         */
        int getDeltaY() {
            return (int) mAccumulatedDelta.y;
        }

        /**
         * Returns whether a dragging gesture has been detected.
         *
         * @return {@code true} if a drag has been detected, {@code false} otherwise.
         */
        boolean isDraggingDetected() {
            if (Float.isNaN(mAccumulatedDelta.x) || Float.isNaN(mAccumulatedDelta.y)) {
                return false;
            }

            final float distanceSquare = (mAccumulatedDelta.x * mAccumulatedDelta.x)
                    + (mAccumulatedDelta.y * mAccumulatedDelta.y);
            if (distanceSquare > mTouchSlopSquare) {
                return true;
            }

            return false;
        }

        /**
         * Gets the integer part of the accumulated motion delta and consumes it, leaving the
         * fractional part for the next calculation.
         *
         * @return A {@link Point} containing the (x, y) integer delta.
         */
        @NonNull
        Point getAndConsumeDelta() {
            final Point delta = new Point(getDeltaX(), getDeltaY());
            mAccumulatedDelta.offset(-delta.x, -delta.y);
            return delta;
        }

        /** Resets the accumulator to its initial state. */
        void reset() {
            resetPointF(mAccumulatedDelta);
            resetPointF(mLastLocation);
        }

        private static void resetPointF(PointF pointF) {
            pointF.x = Float.NaN;
            pointF.y = Float.NaN;
        }

        private static boolean isMouseEvent(MotionEvent event) {
            return (event.getSource() & InputDevice.SOURCE_MOUSE) == InputDevice.SOURCE_MOUSE;
        }
    }
}
