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
package com.android.server.companion.datatransfer.crossdevicesync.common.fake;

import android.app.modes.ContextualMode;
import android.app.modes.ContextualModeManager.ContextualModeListener;
import android.app.modes.ContextualModesMutation;
import android.os.UserHandle;
import android.util.Pair;

import com.android.server.companion.datatransfer.crossdevicesync.common.ContextualModeManagerProxy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/** Fake implementation of {@link ContextualModeManagerProxy}. */
public class FakeContextualModeManager implements ContextualModeManagerProxy {
    private final Map<UserHandle, UserModesState> mUserModes = new HashMap<>();
    private boolean mModeSyncSupported;

    public FakeContextualModeManager() {}

    @Override
    public boolean isModeSyncSupported() {
        return mModeSyncSupported;
    }

    /** Set if mode sync is supported. */
    public void setModeSyncSupported(boolean supported) {
        mModeSyncSupported = supported;
    }

    @Override
    public boolean isModeSyncEnabled(UserHandle userHandle) {
        if (!mUserModes.containsKey(userHandle)) {
            return false;
        }
        return mUserModes.get(userHandle).enabled;
    }

    @Override
    public void setModeSyncEnabled(UserHandle userHandle, boolean enabled) {
        UserModesState userModesState =
                mUserModes.computeIfAbsent(userHandle, k -> new UserModesState());
        if (userModesState.enabled == enabled) {
            return;
        }
        userModesState.enabled = enabled;
        userModesState.notifyModeSyncEnabledChanged(enabled);
    }

    @Override
    public List<ContextualMode> getModes(UserHandle userHandle) {
        if (!mUserModes.containsKey(userHandle)) {
            return List.of();
        }
        return List.copyOf(mUserModes.get(userHandle).modes.values());
    }

    @Override
    public void mutateModes(UserHandle userHandle, ContextualModesMutation mutation) {
        UserModesState userModesState = mUserModes.get(userHandle);
        if (userModesState == null) {
            throw new IllegalArgumentException("Unknown user " + userHandle);
        }
        List<ContextualMode> changedModes = new ArrayList<>();
        for (ContextualMode updatedMode : mutation.getUpdatedModes()) {
            if (!userModesState.modes.containsKey(updatedMode.getId())) {
                throw new IllegalArgumentException("Unknown mode " + updatedMode);
            }
            if (userModesState.putMode(updatedMode)) {
                changedModes.add(updatedMode);
            }
        }
        if (!changedModes.isEmpty()) {
            userModesState.notifyModeListeners(l -> l.onModesChanged(changedModes));
        }
    }

    @Override
    public void registerModeListener(
            UserHandle userHandle, Executor executor, ContextualModeListener listener) {
        mUserModes
                .computeIfAbsent(userHandle, k -> new UserModesState())
                .registerModeListener(executor, listener);
    }

    @Override
    public void unregisterModeListener(ContextualModeListener listener) {
        for (UserModesState userModesState : mUserModes.values()) {
            userModesState.unregisterModeListener(listener);
        }
    }

    @Override
    public void registerModeSyncEnabledListener(
            UserHandle userHandle, Executor executor, Consumer<Boolean> listener) {
        mUserModes
                .computeIfAbsent(userHandle, k -> new UserModesState())
                .registerModeSyncEnabledListener(executor, listener);
    }

    @Override
    public void unregisterModeSyncEnabledListener(Consumer<Boolean> listener) {
        for (UserModesState userModesState : mUserModes.values()) {
            userModesState.unregisterModeSyncEnabledListener(listener);
        }
    }

    /** Add or update a mode for a user. */
    public void addOrUpdateMode(UserHandle userHandle, ContextualMode mode) {
        UserModesState userModesState =
                mUserModes.computeIfAbsent(userHandle, k -> new UserModesState());
        if (userModesState.putMode(mode)) {
            userModesState.notifyModeListeners(l -> l.onModesChanged(List.of(mode)));
        }
    }

    /** Remove a mode for a user. */
    public void removeMode(UserHandle userHandle, String modeId) {
        UserModesState userModesState =
                mUserModes.computeIfAbsent(userHandle, k -> new UserModesState());
        if (userModesState.removeMode(modeId)) {
            userModesState.notifyModeListeners(l -> l.onModeRemoved(modeId));
        }
    }

    @SuppressWarnings("EffectivelyPrivate")
    private static class UserModesState {
        public boolean enabled;
        public final Map<String, ContextualMode> modes = new HashMap<>();
        private final List<Pair<Executor, Consumer<Boolean>>> mModeSyncEnabledListeners =
                new ArrayList<>();
        private final List<Pair<Executor, ContextualModeListener>> mModeListeners =
                new ArrayList<>();

        public boolean putMode(ContextualMode mode) {
            if (Objects.equals(modes.get(mode.getId()), mode)) {
                return false;
            }
            modes.put(mode.getId(), mode);
            return true;
        }

        public boolean removeMode(String id) {
            return modes.remove(id) != null;
        }

        public void registerModeSyncEnabledListener(Executor executor, Consumer<Boolean> listener) {
            mModeSyncEnabledListeners.add(Pair.create(executor, listener));
        }

        public void unregisterModeSyncEnabledListener(Consumer<Boolean> listener) {
            mModeSyncEnabledListeners.removeIf(l -> l.second == listener);
        }

        public void notifyModeSyncEnabledChanged(boolean enabled) {
            mModeSyncEnabledListeners.forEach(l -> l.first.execute(() -> l.second.accept(enabled)));
        }

        public void registerModeListener(Executor executor, ContextualModeListener listener) {
            mModeListeners.add(Pair.create(executor, listener));
        }

        public void unregisterModeListener(ContextualModeListener listener) {
            mModeListeners.removeIf(l -> l.second == listener);
        }

        public void notifyModeListeners(Consumer<ContextualModeListener> consumer) {
            mModeListeners.forEach(l -> l.first.execute(() -> consumer.accept(l.second)));
        }
    }
}
