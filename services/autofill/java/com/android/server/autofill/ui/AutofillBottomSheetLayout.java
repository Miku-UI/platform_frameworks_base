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

package com.android.server.autofill.ui;

import static com.android.server.autofill.Helper.sDebug;

import android.annotation.Nullable;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Insets;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Slog;
import android.view.WindowInsets;
import android.widget.FrameLayout;

import com.android.internal.R;

/** {@link FrameLayout} that displays content of autofill dialogs. */
public class AutofillBottomSheetLayout extends FrameLayout {

    private static final String TAG = "AutofillBottomSheetLayout";

    private int mSystemTopPadding;

    public AutofillBottomSheetLayout(Context context) {
        super(context);
    }

    public AutofillBottomSheetLayout(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public AutofillBottomSheetLayout(Context context, @Nullable AttributeSet attrs,
            int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        this.setOnApplyWindowInsetsListener((v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(
                    WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            mSystemTopPadding = insets.top;
            v.setPadding(0, 0, 0, insets.bottom);
            return windowInsets;
        });
    }

    @Override
    public void onMeasure(int widthSpec, int heightSpec) {
        Context context = getContext();
        Resources resources = (context != null) ? context.getResources() : null;
        if (context == null || resources == null) {
            super.onMeasure(widthSpec, heightSpec);
            Slog.w(TAG, "onMeasure failed due to missing context or missing resources.");
            return;
        }

        DisplayMetrics displayMetrics = resources.getDisplayMetrics();

        final int pxOffset = resources.getDimensionPixelSize(R.dimen.autofill_dialog_offset);
        final int horizontalMargin = resources.getDimensionPixelSize(
                R.dimen.autofill_dialog_min_margin_horizontal);

        final int screenHeight = displayMetrics.heightPixels;
        final int screenWidth = displayMetrics.widthPixels;

        final int maxHeight = screenHeight - mSystemTopPadding - pxOffset;

        int maxWidth = screenWidth - 2 * horizontalMargin;
        maxWidth = Math.min(maxWidth,
                resources.getDimensionPixelSize(R.dimen.autofill_dialog_max_width));

        super.onMeasure(MeasureSpec.makeMeasureSpec(maxWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.AT_MOST));


        if (sDebug) {
            Slog.d(TAG, "onMeasure() values in dp:"
                    + " screenHeight: " + screenHeight / displayMetrics.density + ", screenWidth: "
                    + screenWidth / displayMetrics.density
                    + ", maxHeight: " + maxHeight / displayMetrics.density
                    + ", maxWidth: " + maxWidth / displayMetrics.density + ", getMeasuredWidth(): "
                    + getMeasuredWidth() / displayMetrics.density + ", getMeasuredHeight(): "
                    + getMeasuredHeight() / displayMetrics.density);
        }
        setMeasuredDimension(getMeasuredWidth(), getMeasuredHeight());
    }
}
