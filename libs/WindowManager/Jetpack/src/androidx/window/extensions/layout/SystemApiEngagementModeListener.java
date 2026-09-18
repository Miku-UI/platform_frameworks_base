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

package androidx.window.extensions.layout;

import static androidx.window.extensions.layout.UiContextUtils.assertUiContext;
import static androidx.window.extensions.layout.UiContextUtils.getOrCreateUiContext;

import android.content.Context;
import android.os.Trace;
import android.view.WindowManager;
import android.view.WindowManager.DisplayEngagementModeState;

import androidx.annotation.NonNull;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * An implementation of {@link EngagementModeUpdateListener} that uses the System API.
 */
class SystemApiEngagementModeListener implements EngagementModeUpdateListener {
    private final BiConsumer<Integer, Integer> mOnEngagementModeChangedCallback;
    private final WindowManager mWindowManager;
    private final Consumer<DisplayEngagementModeState> mClientCallback;
    private final Executor mMainExecutor;
    private final Executor mBackgroundExecutor;
    private boolean mIsRegistered = false;

    SystemApiEngagementModeListener(@NonNull Context context,
            @NonNull BiConsumer<Integer, Integer> onEngagementModeChangedCallback) {
        final Context uiContext = getOrCreateUiContext(context);
        assertUiContext(uiContext);
        // WindowManager and its APIs should be only be accessed from UI Context.
        mWindowManager = uiContext.getSystemService(WindowManager.class);
        mMainExecutor = uiContext.getMainExecutor();
        mBackgroundExecutor = Executors.newSingleThreadExecutor();
        mOnEngagementModeChangedCallback = onEngagementModeChangedCallback;
        mClientCallback = state -> mOnEngagementModeChangedCallback.accept(state.getDisplayId(),
                state.getEngagementModeFlags());
    }

    @Override
    public void register(int displayId) {
        mBackgroundExecutor.execute(() -> {
            Trace.beginSection("WindowManager#registerDisplayEngagementModeCallback");
            try {
                if (!mIsRegistered) {
                    // The callback will be executed on the main thread.
                    mWindowManager.registerDisplayEngagementModeCallback(mMainExecutor,
                            mClientCallback);
                    mIsRegistered = true;
                }
            } finally {
                Trace.endSection();
            }

            Trace.beginSection("WindowManager#getDisplayEngagementMode");
            try {
                final int currentMode = mWindowManager.getDisplayEngagementMode(displayId);
                // Trigger an initial update of the engagement mode on the main thread.
                mMainExecutor.execute(() -> {
                    mOnEngagementModeChangedCallback.accept(displayId, currentMode);
                });
            } finally {
                Trace.endSection();
            }
        });
    }

    @Override
    public void unregister() {
        if (!mIsRegistered) return;
        mBackgroundExecutor.execute(() -> {
            Trace.beginSection("WindowManager#unregisterDisplayEngagementModeCallback");
            try {
                mWindowManager.unregisterDisplayEngagementModeCallback(mClientCallback);
                mIsRegistered = false;
            } finally {
                Trace.endSection();
            }
        });
    }
}
