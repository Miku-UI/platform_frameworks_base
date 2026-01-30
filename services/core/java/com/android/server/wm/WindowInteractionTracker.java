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


package com.android.server.wm;

import android.annotation.Nullable;

import com.android.internal.util.Preconditions;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Keeps track of the recently interacted windows and stores their {@link WindowState} objects.
 * The tracker maintains a fixed-size history of window interactions. When the history
 * is full, the oldest interaction is removed to make space for new ones.
 *
 * <p> So far, the interaction is defined as the focus changes. When a new window gains focus,
 * regardless of the display it's on, it gets added to the interaction history, assuming that it's
 * recently interacted by the user.
 */
class WindowInteractionTracker {
    private final int mSize;
    /**
     * A queue holding the {@link WindowState} objects of the recently interacted windows.
     * The order is maintained from newest to oldest interaction.
     */
    private final Deque<WindowState> mInteractionHistory;

    /**
     * Constructs a new WindowInteractionTracker with a specified history size.
     *
     * @param size The maximum number of interacted windows to track.
     */
    WindowInteractionTracker(int size) {
        Preconditions.checkArgument(size > 0,
                "WindowInteractionTracker size must be greater than 0");
        mSize = size;
        mInteractionHistory = new ArrayDeque<>(size);
    }

    /**
     * Records a window interaction by adding its {@link WindowState} to the history.
     *
     * @param windowState The {@link WindowState} of the window that was interacted with.
     */
    void add(WindowState windowState) {
        if (mInteractionHistory.size() == mSize) {
            mInteractionHistory.removeLast();
        }
        mInteractionHistory.addFirst(windowState);
    }

    /**
     * Returns the newest element in the history. If the history is empty, returns {@code null}.
     */
    @Nullable
    WindowState peek() {
        return mInteractionHistory.peekFirst();
    }

    /**
     * Returns a new {@link List} containing the {@link WindowState} objects of the recently
     * interacted windows, sorted from most recent to oldest. The list will be empty if no
     * interactions have been recorded.
     */
    List<WindowState> getRecentlyInteractedWindows() {
        return new ArrayList<>(mInteractionHistory);
    }
}
