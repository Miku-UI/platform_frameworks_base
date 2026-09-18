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

package com.android.systemui.dreams.complication;

import static com.android.systemui.dreams.complication.dagger.DreamComplicationModule.COMPLICATIONS_FADE_OUT_DELAY;
import static com.android.systemui.dreams.complication.dagger.DreamComplicationModule.COMPLICATIONS_RESTORE_TIMEOUT;

import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

import com.android.systemui.ambient.touch.TouchHandler;
import com.android.systemui.ambient.touch.TouchMonitor;
import com.android.systemui.complication.Complication;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.dreams.DreamOverlayStateController;
import com.android.systemui.statusbar.phone.StatusBarKeyguardViewManager;
import com.android.systemui.touch.TouchInsetManager;
import com.android.systemui.util.concurrency.DelayableExecutor;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayDeque;
import java.util.concurrent.ExecutionException;

import javax.inject.Inject;
import javax.inject.Named;

/**
 * {@link HideComplicationTouchHandler} is responsible for hiding the overlay complications from
 * visibility whenever there is touch interactions outside the overlay. The overlay interaction
 * scope includes touches to the complication plus any touch entry region for gestures as specified
 * to the {@link TouchMonitor}.
 *
 * This {@link TouchHandler} is also responsible for fading in the complications at the end
 * of the {@link TouchHandler.TouchSession}.
 */
public class HideComplicationTouchHandler implements TouchHandler {
    private static final String TAG = "HideComplicationHandler";
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    private final int mRestoreTimeout;
    private final int mFadeOutDelay;
    private final StatusBarKeyguardViewManager mStatusBarKeyguardViewManager;
    private final DelayableExecutor mExecutor;
    private final DreamOverlayStateController mOverlayStateController;
    private final TouchInsetManager mTouchInsetManager;
    private final Complication.VisibilityController mVisibilityController;
    private boolean mHidden = false;
    @Nullable
    private Runnable mHiddenCallback;
    private final ArrayDeque<Runnable> mCancelCallbacks = new ArrayDeque<>();


    private final Runnable mRestoreComplications = new Runnable() {
        @Override
        public void run() {
            Log.d(TAG, "Restoring complications...");
            mVisibilityController.setVisibility(View.VISIBLE);
            mHidden = false;
        }
    };

    private final Runnable mHideComplications = new Runnable() {
        @Override
        public void run() {
            if (mOverlayStateController.areExitAnimationsRunning()) {
                // Avoid interfering with the exit animations.
                return;
            }
            Log.d(TAG, "Hiding complications...");
            mVisibilityController.setVisibility(View.INVISIBLE);
            mHidden = true;
            if (mHiddenCallback != null) {
                mHiddenCallback.run();
                mHiddenCallback = null;
            }
        }
    };

    @Inject
    HideComplicationTouchHandler(Complication.VisibilityController visibilityController,
            @Named(COMPLICATIONS_RESTORE_TIMEOUT) int restoreTimeout,
            @Named(COMPLICATIONS_FADE_OUT_DELAY) int fadeOutDelay,
            TouchInsetManager touchInsetManager,
            StatusBarKeyguardViewManager statusBarKeyguardViewManager,
            @Main DelayableExecutor executor,
            DreamOverlayStateController overlayStateController) {
        mVisibilityController = visibilityController;
        mRestoreTimeout = restoreTimeout;
        mFadeOutDelay = fadeOutDelay;
        mStatusBarKeyguardViewManager = statusBarKeyguardViewManager;
        mTouchInsetManager = touchInsetManager;
        mExecutor = executor;
        mOverlayStateController = overlayStateController;
    }

    @Override
    public void onSessionStart(TouchSession session) {
        if (DEBUG) {
            Log.d(TAG, "onSessionStart");
        }

        final boolean bouncerShowing = mStatusBarKeyguardViewManager.isBouncerShowing();

        // If other sessions are interested in this touch, do not fade out elements.
        if (DEBUG) {
            Log.d(TAG, "not fading. Active session count: " + session.getActiveSessionCount()
                    + ". Bouncer showing: " + bouncerShowing);
        }
        session.pop();
        return;
    }

    /**
     * Triggers a runnable after complications have been hidden. Will override any previously set
     * runnable currently waiting for hide to happen.
     */
    private void runAfterHidden(Runnable runnable) {
        mExecutor.execute(() -> {
            if (mHidden) {
                Log.i(TAG, "Executing after hidden runnable immediately...");
                runnable.run();
            } else {
                Log.i(TAG, "Queuing after hidden runnable...");
                mHiddenCallback = runnable;
            }
        });
    }
}
