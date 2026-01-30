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

package com.android.internal.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.RemoteViews;

import androidx.annotation.Nullable;

/***
 * Used in the collapsed Notification.MetricStyle, this horizontal linear layout renders only
 * the children that can fit within its available width; otherwise it hides them.
 */
@RemoteViews.RemoteView
public class NotificationCollapsedMetricContainer extends LinearLayout {

    public NotificationCollapsedMetricContainer(Context context) {
        super(context);
    }

    public NotificationCollapsedMetricContainer(Context context,
            @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public NotificationCollapsedMetricContainer(Context context, @Nullable AttributeSet attrs,
            int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public NotificationCollapsedMetricContainer(Context context, AttributeSet attrs,
            int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }


    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        final int availableWidth = MeasureSpec.getSize(widthMeasureSpec);
        final int childCount = getChildCount();
        int usedWidth = 0;
        boolean hideRemainingChildren = false;
        for (int i = 0; i < childCount; i++) {
            final View child = getChildAt(i);
            if (child == null || child.getVisibility() == View.GONE) {
                continue;
            }

            final MarginLayoutParams layoutParams = (MarginLayoutParams) child.getLayoutParams();
            final int childUsedWidth = child.getMeasuredWidth()
                    + layoutParams.getMarginStart() + layoutParams.getMarginEnd();
            if (hideRemainingChildren || usedWidth + childUsedWidth > availableWidth) {
                child.setVisibility(View.GONE);
                hideRemainingChildren = true;
            } else {
                usedWidth += childUsedWidth;
            }
        }
    }
}
