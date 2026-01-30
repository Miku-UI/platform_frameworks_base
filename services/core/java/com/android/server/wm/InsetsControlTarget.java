/*
 * Copyright (C) 2019 The Android Open Source Project
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

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.os.IBinder;
import android.view.WindowInsets;
import android.view.WindowInsets.Type.InsetsType;
import android.view.inputmethod.ImeTracker;

/**
 * Generalization of an object that can control insets state.
 */
interface InsetsControlTarget extends InsetsTarget {

    /**
     * Notifies the control target that the insets control has changed.
     *
     * @param displayId the display hosting the window of this target
     */
    default void notifyInsetsControlChanged(int displayId) {
    }

    /**
     * @return {@link WindowState} of this target, if any.
     */
    @Nullable
    default WindowState getWindow() {
        return null;
    }

    @Nullable
    @Override
    default IBinder getWindowToken() {
        return null;
    }

    @Override
    default boolean isRequestedVisible(@InsetsType int types) {
        return (WindowInsets.Type.defaultVisible() & types) != 0;
    }

    @InsetsType
    @Override
    default int getRequestedVisibleTypes() {
        return WindowInsets.Type.defaultVisible();
    }

    /**
     * Instructs the control target to show inset sources.
     *
     * @param types to specify which types of insets source window should be shown.
     * @param statsToken the token tracking the current IME request or {@code null} otherwise.
     */
    default void showInsets(@InsetsType int types, @Nullable ImeTracker.Token statsToken) {
    }

    /**
     * Instructs the control target to hide inset sources.
     *
     * @param types to specify which types of insets source window should be hidden.
     * @param statsToken the token tracking the current IME request or {@code null} otherwise.
     */
    default void hideInsets(@InsetsType int types, @Nullable ImeTracker.Token statsToken) {
    }

    /**
     * Returns {@code true} if the control target allows the system to show transient windows.
     */
    default boolean canShowTransient() {
        return false;
    }

    /**
     * @param visible the requested visibility for the IME, used for
     * {@link com.android.server.wm.DisplayContent.RemoteInsetsControlTarget}
     * @param statsToken the token tracking the current IME request
     */
    default void setImeInputTargetRequestedVisibility(boolean visible,
            @NonNull ImeTracker.Token statsToken) {
    }

    /**
     * @return {@link WindowInsets.Type.InsetsType}s which are currently animating (showing or
     * hiding).
     */
    default @InsetsType int getAnimatingTypes() {
        return 0;
    }

    /**
     * @param animatingTypes the {@link InsetsType}s, that are currently animating
     * @param statsToken the token tracking the current IME request or {@code null} otherwise.
     */
    default void setAnimatingTypes(@InsetsType int animatingTypes,
            @Nullable ImeTracker.Token statsToken) {
    }

    /** Returns {@code target.getWindow()}, or null if {@code target} is {@code null}. */
    @Nullable
    static WindowState asWindowOrNull(@Nullable InsetsControlTarget target) {
        return target != null ? target.getWindow() : null;
    }
}
