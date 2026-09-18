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

package com.android.test.ipcrendering;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.OvalShape;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

public class IPCVriTest extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FrameLayout content = new FrameLayout(this);
        content.setBackgroundColor(Color.WHITE);

        View view = new View(this);
        ShapeDrawable drawable = new ShapeDrawable(new OvalShape());
        drawable.getPaint().setColor(Color.RED);
        view.setBackground(drawable);

        int size = 200;
        FrameLayout.LayoutParams params =
                new FrameLayout.LayoutParams(size, size, Gravity.CENTER_HORIZONTAL | Gravity.TOP);
        params.topMargin = 50;
        content.addView(view, params);

        View vectorView = new View(this);
        Drawable vectorDrawable = getDrawable(R.drawable.ic_star);
        vectorView.setBackground(vectorDrawable);

        FrameLayout.LayoutParams vectorParams = new FrameLayout.LayoutParams(
                size, size, Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
        vectorParams.bottomMargin = 50;
        content.addView(vectorView, vectorParams);

        setContentView(content);

        view.post(() -> {
            float halfWidth = content.getWidth() / 2f;
            view.setTranslationX(-halfWidth);
            ObjectAnimator animator =
                    ObjectAnimator.ofFloat(view, "translationX", -halfWidth, halfWidth);
            animator.setDuration(1000);
            animator.setRepeatCount(5);
            animator.setRepeatMode(ValueAnimator.REVERSE);
            animator.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
            animator.start();
        });

        vectorView.post(() -> {
            ObjectAnimator animator = ObjectAnimator.ofFloat(vectorView, "rotation", 0f, 360f);
            animator.setDuration(1000);
            animator.setRepeatCount(ValueAnimator.INFINITE);
            animator.setInterpolator(new android.view.animation.LinearInterpolator());
            animator.start();
        });
    }
}
