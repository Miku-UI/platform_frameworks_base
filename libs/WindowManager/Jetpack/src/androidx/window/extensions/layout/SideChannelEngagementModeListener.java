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
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * An implementation of {@link EngagementModeUpdateListener} that uses a side-channel.
 */
class SideChannelEngagementModeListener implements EngagementModeUpdateListener {
    private final EngagementModeClient mClient;
    private final BiConsumer<Integer, Integer> mOnEngagementModeChangedCallback;
    private final Supplier<Set<Integer>> mActiveDisplayIdsSupplier;
    private java.util.function.Consumer<Integer> mClientCallback;
    private boolean mIsRegistered = false;

    SideChannelEngagementModeListener(@NonNull Context context,
            @NonNull BiConsumer<Integer, Integer> onEngagementModeChangedCallback,
            @NonNull Supplier<Set<Integer>> activeDisplayIdsSupplier) {
        mClient = createClient(context);
        mOnEngagementModeChangedCallback = onEngagementModeChangedCallback;
        mActiveDisplayIdsSupplier = activeDisplayIdsSupplier;
    }

    @VisibleForTesting
    EngagementModeClient createClient(Context context) {
        if (com.android.window.flags.Flags.deviceEngagementMode()) {
            final Context uiContext = getOrCreateUiContext(context);
            assertUiContext(uiContext);
            // Side channel engagement mode should be accessed from UI Context.
            return new EngagementModeClientImpl(
                    uiContext, new Handler(Looper.getMainLooper()));
        } else {
            return new NoOpEngagementModeClient();
        }
    }

    @Override
    public void register(int displayId) {
        if (!mIsRegistered) {
            mClientCallback = mode -> {
                // The engagement mode callback in the side-channel case is global, so all
                // displays should be updated.
                for (final Integer id : mActiveDisplayIdsSupplier.get()) {
                    mOnEngagementModeChangedCallback.accept(id, mode);
                }
            };
            mClient.addUpdateCallback(Runnable::run, mClientCallback);
            mIsRegistered = true;
        }
        mOnEngagementModeChangedCallback.accept(displayId, mClient.getEngagementModeFlags());
    }

    @Override
    public void unregister() {
        if (!mIsRegistered) return;
        if (mClientCallback != null) {
            mClient.removeUpdateCallback(mClientCallback);
            mClientCallback = null;
        }
        mIsRegistered = false;
    }
}
