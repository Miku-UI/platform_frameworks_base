/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.wm.shell.windowdecor;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;

import com.android.wm.shell.R;

/**
 * A {@link LinearLayout} that takes an additional task focused drawable state. The new state is
 * used to select the correct background color for views in the window decoration.
 */
public class WindowDecorLinearLayout extends LinearLayout implements TaskFocusStateConsumer {
    private static final int[] TASK_FOCUSED_STATE = { R.attr.state_task_focused };

    private boolean mIsTaskFocused;
    private GestureInterceptor mGestureInterceptor;

    public WindowDecorLinearLayout(Context context) {
        super(context);
    }

    public WindowDecorLinearLayout(Context context,
            @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public WindowDecorLinearLayout(Context context, @Nullable AttributeSet attrs,
            int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public WindowDecorLinearLayout(Context context, AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public void setGestureInterceptor(GestureInterceptor gestureInterceptor) {
        mGestureInterceptor = gestureInterceptor;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent e) {
        if (mGestureInterceptor == null) {
            return false;
        }
        return mGestureInterceptor.onInterceptTouchEvent(this, e);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (mGestureInterceptor == null) {
            return false;
        }
        return mGestureInterceptor.onTouch(this, e);
    }

    @Override
    public void setTaskFocusState(boolean focused) {
        mIsTaskFocused = focused;

        refreshDrawableState();
    }

    @Override
    protected int[] onCreateDrawableState(int extraSpace) {
        if (!mIsTaskFocused) {
            return super.onCreateDrawableState(extraSpace);
        }

        final int[] states = super.onCreateDrawableState(extraSpace + 1);
        mergeDrawableStates(states, TASK_FOCUSED_STATE);
        return states;
    }

    /**
     * A pair of listeners to work with {@link ViewGroup#onInterceptTouchEvent(MotionEvent)} and
     * {@link View#onTouchEvent(MotionEvent)}.
     */
    public interface GestureInterceptor extends View.OnTouchListener {
        /**
         * Called when {@link WindowDecorLinearLayout#onInterceptTouchEvent(MotionEvent)} is called.
         *
         * @param v the {@link WindowDecorLinearLayout}
         * @param e the {@link MotionEvent}
         * @return {@code true} if this gesture should be intercepted; {@code false} otherwise
         */
        boolean onInterceptTouchEvent(ViewGroup v, MotionEvent e);
    }
}
