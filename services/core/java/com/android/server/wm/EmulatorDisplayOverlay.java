/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.server.wm;

import static android.hardware.display.DisplayManagerGlobal.INTERNAL_EVENT_FLAG_DISPLAY_BRIGHTNESS_CHANGED;

import static com.android.server.wm.WindowManagerDebugConfig.TAG_WITH_CLASS_NAME;
import static com.android.server.wm.WindowManagerDebugConfig.TAG_WM;

import android.content.Context;
import android.graphics.BLASTBufferQueue;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.hardware.display.BrightnessInfo;
import android.hardware.display.DisplayManager;
import android.hardware.display.DisplayManagerGlobal;
import android.util.Slog;
import android.view.Display;
import android.view.Surface;
import android.view.Surface.OutOfResourcesException;
import android.view.SurfaceControl;

import com.android.internal.annotations.VisibleForTesting;

class EmulatorDisplayOverlay implements DisplayManager.DisplayListener {
    private static final String TAG = TAG_WITH_CLASS_NAME ? "EmulatorDisplayOverlay" : TAG_WM;

    private static final String TITLE = "EmulatorDisplayOverlay";

    private static final float FALLBACK_BRIGHTNESS = 1.0f;
    private static final int MAX_OVERLAY_ALPHA = 0xFF;

    // Display dimensions
    private Point mScreenSize;

    private final SurfaceControl mSurfaceControl;
    private final Surface mSurface;
    private final BLASTBufferQueue mBlastBufferQueue;

    private final WindowManagerService mWmService;
    private final int mDisplayId;
    private final boolean mEnableScreenBrightnessDim;

    private int mLastDW;
    private int mLastDH;
    private boolean mDrawNeeded;
    private final Drawable mOverlay;
    private int mRotation;
    private boolean mVisible;

    private float mScreenBrightness;

    EmulatorDisplayOverlay(Context context, WindowManagerService wmService, DisplayContent dc,
            int zOrder, SurfaceControl.Transaction t, boolean enableCircularOverlay,
            boolean enableScreenBrightnessDim) {
        this(context, wmService, dc, zOrder, t, enableCircularOverlay, enableScreenBrightnessDim,
                new BLASTBufferQueue(TITLE, /* updateDestinationFrame */ true));
    }

    @VisibleForTesting
    EmulatorDisplayOverlay(Context context, WindowManagerService wmService, DisplayContent dc,
            int zOrder, SurfaceControl.Transaction t, boolean enableCircularOverlay,
            boolean enableScreenBrightnessDim, BLASTBufferQueue blastBufferQueue) {
        final Display display = dc.getDisplay();
        mScreenSize = new Point();
        display.getSize(mScreenSize);

        SurfaceControl ctrl = null;
        try {
            ctrl = dc.makeOverlay()
                    .setName(TITLE)
                    .setBLASTLayer()
                    .setFormat(PixelFormat.TRANSLUCENT)
                    .setCallsite(TITLE)
                    .build();
            t.setLayer(ctrl, zOrder);
            t.setPosition(ctrl, 0, 0);
            t.show(ctrl);
            // Ensure we aren't considered as obscuring for Input purposes.
            InputMonitor.setTrustedOverlayInputInfo(ctrl, t, dc.getDisplayId(), TITLE);
        } catch (OutOfResourcesException e) {
        }
        mSurfaceControl = ctrl;
        mDrawNeeded = true;
        mOverlay = enableCircularOverlay
                ? context.getDrawable(
                com.android.internal.R.drawable.emulator_circular_window_overlay)
                : null;

        mBlastBufferQueue = blastBufferQueue;
        mBlastBufferQueue.update(mSurfaceControl, mScreenSize.x, mScreenSize.y,
                PixelFormat.RGBA_8888);
        mSurface = mBlastBufferQueue.createSurface();

        mWmService = wmService;
        mDisplayId = dc.getDisplayId();
        mEnableScreenBrightnessDim = enableScreenBrightnessDim;

        if (enableScreenBrightnessDim) {
            updateScreenBrightness(mDisplayId);
            DisplayManagerGlobal.getInstance().registerDisplayListener(
                    this, null, INTERNAL_EVENT_FLAG_DISPLAY_BRIGHTNESS_CHANGED,
                    context.getPackageName()
            );
        }
    }

    private void drawIfNeeded() {
        if (!mDrawNeeded || !mVisible) {
            return;
        }
        mDrawNeeded = false;

        Canvas c = null;
        try {
            c = mSurface.lockCanvas(null);
        } catch (IllegalArgumentException | OutOfResourcesException e) {
        }
        if (c == null) {
            return;
        }
        c.drawColor(getBackgroundColor(), PorterDuff.Mode.SRC);
        // Always draw the overlay with square dimensions
        int size = Math.max(mScreenSize.x, mScreenSize.y);
        if (mOverlay != null) {
            mOverlay.setBounds(0, 0, size, size);
            mOverlay.draw(c);
        }
        mSurface.unlockCanvasAndPost(c);
    }

    private int getBackgroundColor() {
        if (mEnableScreenBrightnessDim) {
            return getScreenBrightnessDimColor();
        } else {
            return Color.TRANSPARENT;
        }
    }

    private int getScreenBrightnessDimColor() {
        int alpha = Math.round((1.0f - mScreenBrightness) * MAX_OVERLAY_ALPHA);
        return (alpha << 24) | 0x000000;
    }

    // Note: caller responsible for being inside
    // Surface.openTransaction() / closeTransaction()
    public void setVisibility(boolean on, SurfaceControl.Transaction t) {
        if (mSurfaceControl == null) {
            return;
        }
        mVisible = on;
        drawIfNeeded();
        if (on) {
            t.show(mSurfaceControl);
        } else {
            t.hide(mSurfaceControl);
        }
    }

    void positionSurface(int dw, int dh, int rotation, SurfaceControl.Transaction t) {
        if (mLastDW == dw && mLastDH == dh && mRotation == rotation) {
            return;
        }
        Slog.d(TAG, "positionSurface: dw=" + dw + ", dh=" + dh);
        mLastDW = dw;
        mLastDH = dh;
        mDrawNeeded = true;
        mRotation = rotation;
        drawIfNeeded();
    }

    @Override
    public void onDisplayAdded(int displayId) {
        // Do nothing
    }

    @Override
    public void onDisplayRemoved(int displayId) {
        // Do nothing
    }

    @Override
    public void onDisplayChanged(int displayId) {
        synchronized (mWmService.mGlobalLock) {
            if (mDisplayId != displayId) {
                return;
            }
            if (updateScreenBrightness(displayId)) {
                mDrawNeeded = true;
                drawIfNeeded();
            }
        }
    }

    private boolean updateScreenBrightness(int displayId) {
        BrightnessInfo brightnessInfo = DisplayManagerGlobal.getInstance().getBrightnessInfo(
                displayId);
        if (brightnessInfo == null) {
            Slog.d(TAG, "Cannot update brightness since BrightnessInfo is null");
            return false;
        }
        float adjustedBrightness = clampedBrightness(
                DisplayManagerGlobal.getInstance().getBrightnessInfo(
                        displayId).adjustedBrightness);
        if (mScreenBrightness == adjustedBrightness) {
            return false;
        }
        mScreenBrightness = adjustedBrightness;
        return true;
    }

    private float clampedBrightness(float brightness) {
        if (brightness < 0.0f || brightness > 1.0f) {
            return FALLBACK_BRIGHTNESS;
        }
        return brightness;
    }
}
