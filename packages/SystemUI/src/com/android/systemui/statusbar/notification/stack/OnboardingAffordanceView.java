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

package com.android.systemui.statusbar.notification.stack;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.android.systemui.animation.LaunchableView;
import com.android.systemui.res.R;
import com.android.systemui.statusbar.notification.row.StackScrollerDecorView;

/**
 * Affordance view for notification summaries and bundles onboarding
 */
public class OnboardingAffordanceView extends StackScrollerDecorView implements LaunchableView {

    private ViewGroup mContents;
    private TextView mTurnOnButton;
    private TextView mDismissButton;
    @Nullable private View.OnClickListener mTurnOnClickListener = null;
    @Nullable private View.OnClickListener mOnDismissClickListener = null;
    @Nullable private Runnable mOnActivityLaunchEndListener = null;

    public OnboardingAffordanceView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        mContents = requireViewById(R.id.content);
        bindContents();
        super.onFinishInflate();
        setVisible(true /* visible */, false /* animate */);
    }

    private void bindContents() {
        mDismissButton = requireViewById(R.id.btn_dismiss);
        if (mOnDismissClickListener != null) {
            mDismissButton.setOnClickListener(mOnDismissClickListener);
        }
        mTurnOnButton = requireViewById(R.id.btn_turn_on);
        if (mTurnOnClickListener != null) {
            mTurnOnButton.setOnClickListener(mTurnOnClickListener);
        }
    }

    @Override
    protected View findContentView() {
        return mContents;
    }

    @Override
    protected View findSecondaryView() {
        return null;
    }

    @Override
    public boolean isTransparent() {
        return false;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        return super.onInterceptTouchEvent(ev);
    }


    @Override
    protected void applyContentTransformation(float contentAlpha, float translationY) {
        super.applyContentTransformation(contentAlpha, translationY);
        mDismissButton.setAlpha(contentAlpha);
        mDismissButton.setTranslationY(translationY);
        mTurnOnButton.setAlpha(contentAlpha);
        mTurnOnButton.setTranslationY(translationY);
    }

    /**
     * Click listener for "Got it" button
     * @param listener click listener
     */
    public void setOnDismissClickListener(View.OnClickListener listener) {
        mOnDismissClickListener = listener;
        mDismissButton.setOnClickListener(listener);
    }

    /**
     * Click listener for "Turn on" button
     * @param listener click listener
     */
    public void setOnTurnOnClickListener(View.OnClickListener listener) {
        mTurnOnClickListener = listener;
        mTurnOnButton.setOnClickListener(listener);
    }

    public void setOnActivityLaunchEndListener(@Nullable Runnable listener) {
        mOnActivityLaunchEndListener = listener;
    }

    @Override
    public boolean needsClippingToShelf() {
        return true;
    }

    @Override
    public void setShouldBlockVisibilityChanges(boolean block) {
        // no-op
    }

    @Override
    public boolean isBackgroundOpaque() {
        return true;
    }

    @Override
    public void onActivityLaunchAnimationEnd() {
        if (mOnActivityLaunchEndListener != null) {
            mOnActivityLaunchEndListener.run();
        }
    }
}
