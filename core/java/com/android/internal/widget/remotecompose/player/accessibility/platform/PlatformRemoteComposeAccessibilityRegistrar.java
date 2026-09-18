/*
 * Copyright 2026 The Android Open Source Project
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
package com.android.internal.widget.remotecompose.player.accessibility.platform;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SuppressLint;
import android.graphics.Rect;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

import com.android.internal.widget.ExploreByTouchHelper;
import com.android.internal.widget.remotecompose.core.CoreDocument;
import com.android.internal.widget.remotecompose.core.RemoteContextActions;
import com.android.internal.widget.remotecompose.player.accessibility.CoreDocumentAccessibility;
import com.android.internal.widget.remotecompose.player.accessibility.RemoteComposeAccessibilityRegistrar;

/**
 * Trivial wrapper for calling setAccessibilityDelegate on a View. This exists primarily because the
 * RemoteDocumentPlayer is either running in the platform on a known API version, or outside in
 * which case it must use the Androidx ViewCompat class.
 */
@SuppressLint({"RestrictedApiAndroidX", "deprecation", "UnknownNullness"})
public class PlatformRemoteComposeAccessibilityRegistrar
        implements RemoteComposeAccessibilityRegistrar {

    /** return helper */
    public PlatformRemoteComposeTouchHelper forRemoteComposePlayer(
            View player, @NonNull CoreDocument coreDocument) {
        return new PlatformRemoteComposeTouchHelper(
                player,
                new CoreDocumentAccessibility(coreDocument, ((RemoteContextActions) player)),
                new AndroidPlatformSemanticNodeApplier(player));
    }

    /**
     * set delegate
     *
     * @param remoteComposePlayer The View representing the remote compose player.
     * @param document The CoreDocument containing the accessibility information for the UI
     *     elements.
     */
    @Override
    public void setAccessibilityDelegate(
            @NonNull View remoteComposePlayer, @NonNull CoreDocument document) {
        remoteComposePlayer.setAccessibilityDelegate(
                forRemoteComposePlayer(remoteComposePlayer, document));
    }

    /**
     * clear delegate
     *
     * @param remoteComposePlayer The View representing the remote compose player.
     */
    @Override
    public void clearAccessibilityDelegate(@NonNull View remoteComposePlayer) {
        remoteComposePlayer.setAccessibilityDelegate(null);
    }

    /**
     * Gets the accessibility delegate from the player if it is an instance of {@link
     * ExploreByTouchHelper}.
     *
     * @param remoteComposePlayer The View representing the remote compose player.
     * @return The ExploreByTouchHelper delegate, or null if it's not set or not of that type.
     */
    public @Nullable ExploreByTouchHelper getAccessibilityDelegate(
            @NonNull View remoteComposePlayer) {
        View.AccessibilityDelegate accessibilityDelegate =
                remoteComposePlayer.getAccessibilityDelegate();
        return accessibilityDelegate instanceof ExploreByTouchHelper
                ? (ExploreByTouchHelper) accessibilityDelegate
                : null;
    }

    /**
     * Dispatch a hover event.
     *
     * @param event the motion event to be dispatched.
     * @return true if the event was handled by the view, false otherwise.
     */
    @Override
    public boolean dispatchHoverEvent(
            @NonNull View remoteComposePlayer, @NonNull MotionEvent event) {
        ExploreByTouchHelper exploreByTouchHelper = getAccessibilityDelegate(remoteComposePlayer);
        return (exploreByTouchHelper != null && exploreByTouchHelper.dispatchHoverEvent(event));
    }

    /**
     * Dispatch a key event to the next view on the focus path.
     *
     * @param event the key event to be dispatched.
     * @return true if the event was handled, false otherwise.
     */
    @Override
    public boolean dispatchKeyEvent(@NonNull View remoteComposePlayer, @NonNull KeyEvent event) {
        // Not present in platform
        return false;
    }

    /**
     * Called by the view system when the focus state of this view changes. When the focus change
     * event is caused by directional navigation, direction and previouslyFocusedRect provide
     * insight into where the focus is coming from.
     *
     * @param gainFocus true if the View has focus; false otherwise.
     * @param direction the direction focus has moved when requestFocus() is called to give this
     *     view focus.
     * @param previouslyFocusedRect the rectangle, in this view's coordinate system, of the
     *     previously focused view.
     */
    @Override
    public void onFocusChanged(
            @NonNull View remoteComposePlayer,
            boolean gainFocus,
            int direction,
            @Nullable Rect previouslyFocusedRect) {
        // Not present in platform
    }
}
