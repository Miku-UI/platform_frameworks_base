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
package com.android.server.companion.datatransfer.crossdevicesync.common;

import android.app.modes.ContextualMode;
import android.app.modes.ContextualModeManager;
import android.app.modes.ContextualModeManager.ContextualModeListener;
import android.app.modes.ContextualModesMutation;
import android.os.UserHandle;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/** Proxy interface for {@link ContextualModeManager}. */
public interface ContextualModeManagerProxy {

    /** See {@link ContextualModeManager#isModeSyncSupported()}. */
    boolean isModeSyncSupported();

    /** See {@link ContextualModeManager#isModeSyncEnabled(UserHandle)}. */
    boolean isModeSyncEnabled(UserHandle userHandle);

    /** See {@link ContextualModeManager#setModeSyncEnabled(UserHandle, boolean)}. */
    void setModeSyncEnabled(UserHandle userHandle, boolean enabled);

    /** See {@link ContextualModeManager#getModes(UserHandle)}. */
    List<ContextualMode> getModes(UserHandle userHandle);

    /** See {@link ContextualModeManager#mutateModes(UserHandle, ContextualModesMutation)}. */
    void mutateModes(UserHandle userHandle, ContextualModesMutation mutation);

    /**
     * See {@link ContextualModeManager#registerModeListener(UserHandle, Executor,
     * ContextualModeListener)}.
     */
    void registerModeListener(
            UserHandle userHandle, Executor executor, ContextualModeListener listener);

    /** See {@link ContextualModeManager#unregisterModeListener(ContextualModeListener)}. */
    void unregisterModeListener(ContextualModeListener listener);

    /**
     * See {@link ContextualModeManager#registerModeSyncEnabledListener(UserHandle, Executor,
     * Consumer)}.
     */
    void registerModeSyncEnabledListener(
            UserHandle userHandle, Executor executor, Consumer<Boolean> listener);

    /** See {@link ContextualModeManager#unregisterModeSyncEnabledListener(Consumer)}. */
    void unregisterModeSyncEnabledListener(Consumer<Boolean> listener);
}
