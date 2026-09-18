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

package com.android.wm.shell.hierarchy.experimental.testsplit;

import android.content.Context;
import android.graphics.Color;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

public class DividerView extends FrameLayout implements View.OnTouchListener {
    boolean mIsLeftRight = true;
    private int mLastTouchPos;
    private View mDividerBar;

    private SplitWindowManager mSplitWindowManager;
    private int mDividerType;

    public static final int TYPE_VERTICAL = 0;

    public static final int TYPE_HORIZONTAL = 1;

    public static final int DIVIDER_SIZE_DP = 30;

    public DividerView(@NonNull Context context, SplitWindowManager splitWindowManager, int dividerType) {
        super(context);
        mSplitWindowManager = splitWindowManager;
        mDividerType = dividerType;
        mDividerBar = new View(context);
        mDividerBar.setBackgroundColor(Color.BLACK);
        final float density = context.getResources().getDisplayMetrics().density;
        final int barSize = (int) (DIVIDER_SIZE_DP * density);
        FrameLayout.LayoutParams lp = new LayoutParams(
                mIsLeftRight ? barSize : ViewGroup.LayoutParams.MATCH_PARENT,
                mIsLeftRight ? ViewGroup.LayoutParams.MATCH_PARENT : barSize);
        lp.gravity = android.view.Gravity.CENTER;
        addView(mDividerBar, lp);
        setOnTouchListener(this);
    }

    int getDividerType() {
        return mDividerType;
    }

    void setIsLeftRight(boolean isLeftRight) {
        if (mIsLeftRight == isLeftRight) return;
        android.util.Log.d("DividerView", "setIsLeftRight: " + isLeftRight);
        mIsLeftRight = isLeftRight;
        final float density = getContext().getResources().getDisplayMetrics().density;
        final int barSize = (int) (DIVIDER_SIZE_DP * density);
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) mDividerBar.getLayoutParams();
        if (lp != null) {
            lp.width = mIsLeftRight ? barSize : ViewGroup.LayoutParams.MATCH_PARENT;
            lp.height = mIsLeftRight ? ViewGroup.LayoutParams.MATCH_PARENT : barSize;
            lp.gravity = android.view.Gravity.CENTER;
            mDividerBar.setLayoutParams(lp);
        }
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        final int touchPos = (int) (mIsLeftRight ? event.getRawX() : event.getRawY());
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mLastTouchPos = touchPos;
                if (mSplitWindowManager != null) {
                    android.util.Log.d("DividerView", "ACTION_DOWN type=" + mDividerType);
                }
                return true;
            case MotionEvent.ACTION_MOVE:
                final int delta = touchPos - mLastTouchPos;
                mLastTouchPos = touchPos;
                if (mSplitWindowManager != null) {
                    mSplitWindowManager.onDrag(delta, false /* finished */);
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (mSplitWindowManager != null) {
                    mSplitWindowManager.onDrag(0, true /* finished */);
                }
                return true;
        }
        return false;
    }
}
