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

package com.android.extensions.computercontrol.view;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.SurfaceTexture;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.extensions.computercontrol.ComputerControlSession;
import com.android.extensions.computercontrol.InteractiveMirror;
import com.android.extensions.computercontrol.input.TouchEvent;

/**
 * A view which allows interactive mirroring of a given {@link ComputerControlSession}.
 */
public class MirrorView extends FrameLayout {
    private MirrorHelper mMirrorHelper;
    private Overlay mOverlay;
    private OnTouchListener mOnTouchListener = null;

    public MirrorView(Context context) {
        super(context);
        init(context);
    }

    public MirrorView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public MirrorView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    public MirrorView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(context);
    }

    /**
     * Sets the {@link ComputerControlSession} to be mirrored, or {@code null} if nothing needs to
     * be shown.
     */
    public void setComputerControlSession(@Nullable ComputerControlSession computerControlSession) {
        mMirrorHelper.setComputerControlSession(computerControlSession);
        if (computerControlSession != null) {
            computerControlSession.setTouchListener(mOverlay);
            ComputerControlSession.Params params = computerControlSession.getParams();
            mOverlay.setSurfaceBounds(params.getDisplayWidthPx(), params.getDisplayHeightPx());
        }
    }

    /**
     * Sets whether user input can control the session being mirrored. By default, this is set to
     * {@code false}.
     * <p>
     * Note that when this is set to {@code true}, {@link #onTouchEvent(MotionEvent)} would not get
     * called, and the return value of
     * {@link android.view.View.OnTouchListener#onTouch(View, MotionEvent)} of any
     * {@link android.view.View.OnTouchListener} would be ignored. However, any
     * {@link android.view.View.OnTouchListener} set through
     * {@link #setOnTouchListener(OnTouchListener)} would still be invoked.
     */
    public void setInteractive(boolean interactive) {
        mMirrorHelper.setInteractive(interactive);
    }

    /**
     * Sets whether touch events injected into the mirrored {@link ComputerControlSession} should
     * be shown using a circular indicator. By default, this is set to {@code false}.
     */
    public void setShowTouches(boolean showTouches) {
        mOverlay.setShowTouches(showTouches);
    }

    /**
     * Sets the color of the circular indicator showing the injected touch events. By default, this
     * is set to {@code Color#RED}.
     */
    public void setTouchIndicatorColor(@ColorInt int color) {
        mOverlay.setDotColor(color);
    }

    /**
     * Sets the radius (in pixels) of the circular indicator showing the injected touch events. By
     * default, this is set to {@code 25}.
     */
    public void setTouchIndicatorRadius(int radius) {
        if (radius <= 0) {
            throw new IllegalArgumentException("Radius must be positive");
        }
        if (radius >= getWidth() || radius >= getHeight()) {
            throw new IllegalArgumentException("Radius cannot be greater than view bounds");
        }
        mOverlay.setDotRadius(radius);
    }

    @Override
    public void setOnTouchListener(OnTouchListener listener) {
        // Don't call super.setOnTouchListener as that would overwrite our own listener. Instead,
        // store this so it can be invoked from our own listener.
        mOnTouchListener = listener;
    }

    private void init(@NonNull Context context) {
        TextureView textureView = new TextureView(context);
        mOverlay = new Overlay(context);
        addView(textureView,
                new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        addView(mOverlay,
                new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        mMirrorHelper = new MirrorHelper();
        textureView.setSurfaceTextureListener(mMirrorHelper);

        super.setOnTouchListener((v, event) -> {
            boolean handled = mOnTouchListener != null && mOnTouchListener.onTouch(v, event);
            handled |= mMirrorHelper.handleTouch(event);
            return handled;
        });
    }

    private static final class MirrorHelper implements TextureView.SurfaceTextureListener {
        private final HandlerThread mHandlerThread;

        // The following members are always written on the main thread, and always read from the
        // single thread of the executor.
        private volatile ComputerControlSession mComputerControlSession = null;
        private volatile Surface mSurface = null;
        private volatile int mWidth = 0;
        private volatile int mHeight = 0;

        // This is always read and written from the single thread of the executor.
        private volatile InteractiveMirror mInteractiveMirror = null;

        private boolean mInteractive = false;

        MirrorHelper() {
            mHandlerThread = new HandlerThread("mirrorHelper");
            mHandlerThread.start();
        }

        @Override
        public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surfaceTexture, int width,
                int height) {
            mSurface = new Surface(surfaceTexture);
            mWidth = width;
            mHeight = height;
            createOrResizeMirrorIfPossible();
        }

        @Override
        public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surfaceTexture, int width,
                int height) {
            mWidth = width;
            mHeight = height;
            createOrResizeMirrorIfPossible();
        }

        @Override
        public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surfaceTexture) {
            destroyMirror();
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surfaceTexture) {}

        void setComputerControlSession(@Nullable ComputerControlSession computerControlSession) {
            if (mComputerControlSession != null) {
                destroyMirror();
            }
            mComputerControlSession = computerControlSession;
            createOrResizeMirrorIfPossible();
        }

        void setInteractive(boolean interactive) {
            mInteractive = interactive;
        }

        boolean handleTouch(@NonNull MotionEvent event) {
            if (!mInteractive) {
                return false;
            }
            if (!event.getDevice().supportsSource(InputDevice.SOURCE_TOUCHSCREEN)) {
                return false;
            }
            if (mInteractiveMirror == null) {
                return false;
            }
            MotionEvent copy = event.copy();
            mHandlerThread.getThreadExecutor().execute(() -> {
                mInteractiveMirror.sendTouchEvent(copy);
                copy.recycle();
            });
            return true;
        }

        private void createOrResizeMirrorIfPossible() {
            mHandlerThread.getThreadExecutor().execute(() -> {
                if (mComputerControlSession == null || mSurface == null) {
                    return;
                }

                if (mInteractiveMirror != null) {
                    // This indicates that a mirror is already there, and only needs to be resized.
                    mInteractiveMirror.resize(mWidth, mHeight);
                    return;
                }

                // Start mirroring.
                mInteractiveMirror =
                        mComputerControlSession.createInteractiveMirror(mWidth, mHeight, mSurface);
            });
        }

        private void destroyMirror() {
            mHandlerThread.getThreadExecutor().execute(() -> {
                if (mInteractiveMirror != null) {
                    // Stop mirroring.
                    mInteractiveMirror.close();
                    mInteractiveMirror = null;
                }
            });
        }
    }

    private static final class Overlay
            extends View implements ComputerControlSession.TouchListener {
        private static final int INVALID_POSITION = -1;
        private static final long DOT_FADE_DURATION_MS = 500L;
        private static final int DOT_RADIUS = 25;
        private static final int DOT_COLOR = Color.RED;

        private final Handler mHandler = new Handler(Looper.getMainLooper());
        private final Paint mPaint = new Paint();
        private int mSurfaceWidth = INVALID_POSITION;
        private int mSurfaceHeight = INVALID_POSITION;
        private int mTouchX = INVALID_POSITION;
        private int mTouchY = INVALID_POSITION;
        private boolean mShowTouches = false;
        private int mDotRadius = DOT_RADIUS;
        private ValueAnimator mAnimator = null;

        Overlay(Context context) {
            super(context);
            mPaint.setColor(DOT_COLOR);
            mPaint.setStyle(Paint.Style.FILL);
        }

        void setShowTouches(boolean showTouches) {
            if (showTouches != mShowTouches) {
                mShowTouches = showTouches;
                invalidate();
            }
        }

        void setDotColor(int color) {
            if (color != mPaint.getColor()) {
                mPaint.setColor(color);
                invalidate();
            }
        }

        void setDotRadius(int radius) {
            if (radius != mDotRadius) {
                mDotRadius = radius;
                invalidate();
            }
        }

        void setSurfaceBounds(int width, int height) {
            mSurfaceWidth = width;
            mSurfaceHeight = height;
        }

        @Override
        public void onTouchEvent(@NonNull TouchEvent event) {
            mHandler.post(() -> {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                    case MotionEvent.ACTION_MOVE:
                        stopFadeAnimation();
                        calculateTouchCoordinates(event);
                        invalidate();
                        break;
                    case MotionEvent.ACTION_CANCEL:
                    case MotionEvent.ACTION_UP:
                        startFadeAnimation();
                        break;
                }
            });
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            super.onDraw(canvas);
            if (!mShowTouches) {
                return;
            }

            if (mTouchX >= 0 && mTouchY >= 0) {
                canvas.drawCircle(mTouchX, mTouchY, mDotRadius, mPaint);
            }
        }

        private void calculateTouchCoordinates(@NonNull TouchEvent event) {
            int viewWidth = getWidth();
            int viewHeight = getHeight();
            float surfaceAspectRatio = (float) mSurfaceWidth / mSurfaceHeight;
            boolean fitWidth = (mSurfaceWidth > mSurfaceHeight)
                    || ((mSurfaceHeight == mSurfaceWidth) && (viewHeight > viewWidth));
            if (fitWidth) {
                int height = (int) (viewWidth / surfaceAspectRatio);
                int pillarBoxHeight = (viewHeight - height) / 2;
                mTouchX = (int) (event.getX() * (viewWidth / (float) mSurfaceWidth));
                mTouchY =
                        pillarBoxHeight + (int) (event.getY() * (height / (float) mSurfaceHeight));
            } else {
                int width = (int) (surfaceAspectRatio * viewHeight);
                int pillarBoxWidth = (viewWidth - width) / 2;
                mTouchY = (int) (event.getY() * (viewHeight / (float) mSurfaceHeight));
                mTouchX = pillarBoxWidth + (int) (event.getX() * (width / (float) mSurfaceWidth));
            }
        }

        private void startFadeAnimation() {
            mAnimator = ValueAnimator.ofInt(Color.alpha(mPaint.getColor()), 0);
            mAnimator.addUpdateListener(animation -> {
                int color = mPaint.getColor();
                setDotColor(Color.argb((int) animation.getAnimatedValue(), Color.red(color),
                        Color.green(color), Color.blue(color)));
            });
            mAnimator.addListener(new Animator.AnimatorListener() {
                private final int mOriginalColor = mPaint.getColor();

                @Override
                public void onAnimationStart(@NonNull Animator animation) {}

                @Override
                public void onAnimationEnd(@NonNull Animator animation) {
                    finish();
                }

                @Override
                public void onAnimationCancel(@NonNull Animator animation) {
                    finish();
                }

                @Override
                public void onAnimationRepeat(@NonNull Animator animation) {}

                private void finish() {
                    mPaint.setColor(mOriginalColor);
                    mTouchX = INVALID_POSITION;
                    mTouchY = INVALID_POSITION;
                }
            });
            mAnimator.setDuration(DOT_FADE_DURATION_MS);
            mAnimator.start();
        }

        private void stopFadeAnimation() {
            if (mAnimator != null) {
                mAnimator.cancel();
                mAnimator = null;
            }
        }
    }
}
