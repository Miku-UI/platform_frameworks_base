/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.server;

import java.util.concurrent.Executor;

/**
 * UiModeManager local system service interface.
 *
 * @hide Only for use within the system server.
 */
public abstract class UiModeManagerInternal {

    /** Returns whether night mode is enabled on the given display. */
    public abstract boolean isNightMode(int displayId);

    /**
     * Sets the UI mode for the given display.
     *
     * <p>UiModeManagerService does not track displays. It is the caller's responsibility to clear
     * any existing overrides when a display becomes removed/invalid/inactive.</p>
     */
    public abstract void setDisplayUiMode(int displayId, int uiMode);

    /** Returns the UI mode for the given display. */
    public abstract int getDisplayUiMode(int displayId);

    /** Returns contrast level for the given user. */
    public abstract float getContrast(int userId);

    public interface ContrastListenerInternal {
        /** Called when the contrast level changes. */
        void onContrastChange(int userId, float contrastLevel);
    }

    /** Adds a contrast listener for all users. */
    public abstract void addContrastListener(ContrastListenerInternal listener, Executor executor);
}
