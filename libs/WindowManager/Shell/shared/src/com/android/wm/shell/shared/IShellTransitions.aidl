/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.wm.shell.shared;

import android.view.SurfaceControl;
import android.window.RemoteTransition;
import android.window.TransitionFilter;

import com.android.wm.shell.shared.IFocusTransitionListener;
import com.android.wm.shell.shared.IHomeTransitionListener;
import com.android.wm.shell.shared.IOverviewOverlayLeashInvalidationCallback;

/**
 * Interface that is exposed to remote callers to manipulate the transitions feature.
 */
interface IShellTransitions {

    /**
     * Registers a remote transition handler for all operations excluding takeovers (see
     * registerRemoteForTakeover()).
     */
    oneway void registerRemote(in RemoteTransition remoteTransition) = 1;

    /**
     * Unregisters a remote transition handler for all operations.
     */
    oneway void unregisterRemote(in RemoteTransition remoteTransition) = 2;

    /**
     * Retrieves the apply-token used by transactions in Shell
     */
    IBinder getShellApplyToken() = 3;

    /**
     * Set listener that will receive callbacks about transitions involving a user's home activity.
     */
    oneway void setHomeTransitionListener(in IHomeTransitionListener listener, int userId) = 4;

    /**
     * Returns a container surface for the home root task.
     */
    SurfaceControl getHomeTaskOverlayContainer() = 5;

    /**
     * Registers a remote transition for takeover operations only.
     */
    oneway void registerRemoteForTakeover(in RemoteTransition remoteTransition) = 6;

    /**
     * Set listener that will receive callbacks about transitions involving focus switch.
     */
    oneway void setFocusTransitionListener(in IFocusTransitionListener listener) = 7;

    /**
     * Returns a container surface for the overview overlay.
     */
    @nullable
    SurfaceControl getOverviewOverlayContainer(int displayId) = 8;

    /**
     * Registers a callback for when the overview overlay leash for the given {@code displayId} is
     * invalidated.
     */
     oneway void registerOverviewOverlayLeashInvalidationCallback(
            int displayId, in IOverviewOverlayLeashInvalidationCallback callback) = 9;

    /**
     * Requests to unregister the given {@code callback} for the given {@code displayId}.
     * <p>
     * No-op if the given {@code callback} is not currently registered for the given
     * {@code displayId}.
     */
     oneway void unregisterOverviewOverlayLeashInvalidationCallback(
            int displayId, in IOverviewOverlayLeashInvalidationCallback callback) = 10;
}
