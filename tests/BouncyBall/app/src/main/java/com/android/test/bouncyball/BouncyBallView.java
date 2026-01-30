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

package com.android.test.bouncyball;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Trace;
import android.util.AttributeSet;
import android.view.View;

public class BouncyBallView extends View {
    private static final int BACKGROUND_COLOR = 0xFF400080;
    private static final int BALL_DIAMETER = 200;
    private static final int SEC_TO_NANOS = 1000 * 1000 * 1000;
    private static final long BALL_VELOCITY = 400;

    public BouncyBallView(Context context, AttributeSet attrs) {
        super(context, attrs);
        Trace.beginSection("BouncyBallView constructor");
        setWillNotDraw(false);

        mBallPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mBallPaint.setColor(0xFF00FF00);
        mBallPaint.setStyle(Paint.Style.FILL);

        Trace.endSection();
    }

    private void drawBall(Canvas canvas) {
        Trace.beginSection("BouncyBallView drawBall");
        final int width = canvas.getWidth();
        final int height = canvas.getHeight();

        final long pos = System.nanoTime() * BALL_VELOCITY / SEC_TO_NANOS;
        final int xMax = width - BALL_DIAMETER;
        final int yMax = height - BALL_DIAMETER;
        final long xOffset = pos % xMax;
        final long yOffset = pos % yMax;

        float left, right, top, bottom;

        if (((pos / xMax) & 1) == 0) {
            left = xMax - xOffset;
        } else {
            left = xOffset;
        }
        right = left + BALL_DIAMETER;

        if (((pos / yMax) & 1) == 0) {
            top = yMax - yOffset;
        } else {
            top = yOffset;
        }
        bottom = top + BALL_DIAMETER;

        // Draw the ball
        canvas.drawColor(BACKGROUND_COLOR);
        canvas.drawOval(left, top, right, bottom, mBallPaint);

        invalidate();
        Trace.endSection();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        Trace.beginSection("BouncyBallView onDraw");
        drawBall(canvas);
        Trace.endSection();
    }

    private final Paint mBallPaint;
}
