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
 package android.app.modes;

 import android.app.modes.ContextualMode;
 import android.app.modes.ContextualModesMutation;
 import android.app.modes.IContextualModeListener;
 import android.app.modes.IContextualModeSyncListener;
 import android.os.UserHandle;

 import java.util.List;

/**
 * Internal interface used to control contextual modes.
 *
 * <p>Use the {@link android.app.modes.ContextualModeManager} class rather than going through
 * this Binder interface directly. See {@link android.app.modes.ContextualModeManager} for
 * more complete documentation.
 *
 * @hide
 */
interface IContextualModeManager {
    /** Check if mode sync is supported on this device. */
    boolean isModeSyncSupported();

    /** Check if mode sync is enabled. */
    boolean isModeSyncEnabled(in UserHandle userHandle);

    /** Enable or disable mode sync. */
    @EnforcePermission("WRITE_SECURE_SETTINGS")
    void setModeSyncEnabled(in UserHandle userHandle, boolean enabled);

    /** Get all modes. */
    @EnforcePermission("MANAGE_CONTEXTUAL_MODES")
    List<ContextualMode> getModes(in UserHandle userHandle);

    /** Apply a mutation to modes. */
    @EnforcePermission("MANAGE_CONTEXTUAL_MODES")
    void mutateModes(in UserHandle userHandle, in ContextualModesMutation mutation);

    /** Register a listener for mode sync changes. */
    void registerModeSyncListener(in UserHandle userHandle,
        in IContextualModeSyncListener listener);

    /** Unregister a {@link IContextualModeSyncListener} */
    void unregisterModeSyncListener(in IContextualModeSyncListener listener);

    /** Register a listener for mode changes. */
    @EnforcePermission("MANAGE_CONTEXTUAL_MODES")
    void registerModeListener(in UserHandle userHandle, in IContextualModeListener listener);

    /** Unregister a {@link IContextualModeListener} */
    @EnforcePermission("MANAGE_CONTEXTUAL_MODES")
    void unregisterModeListener(in IContextualModeListener listener);
}
