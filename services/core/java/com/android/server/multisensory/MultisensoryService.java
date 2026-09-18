/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.server.multisensory;

import static android.Manifest.permission.REMOTE_MULTISENSORY_PLAYBACK;
import static android.os.Trace.TRACE_TAG_VIBRATOR;

import android.annotation.EnforcePermission;
import android.annotation.RequiresNoPermission;
import android.content.Context;
import android.os.RemoteException;
import android.os.Trace;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.os.multisensory.IMultisensoryPlayer;
import android.os.multisensory.IMultisensoryService;
import android.os.multisensory.MultisensoryContinuousEffectModifier;
import android.util.Slog;

import androidx.annotation.NonNull;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.SystemService;
import com.android.server.multisensory.playback.MultisensoryPlayerDefault;
import com.android.server.multisensory.repository.MultisensoryRepository;

import java.util.List;

/**
 * The Multisensory system service in charge of playing audio-haptic feedback in the Multisensory
 * Design System
 *
 * @hide
 */
public class MultisensoryService extends IMultisensoryService.Stub {

    public static final String TAG = "MultisensoryService";

    public static class Lifecycle extends SystemService {
        private MultisensoryService mService;

        public Lifecycle(@NonNull Context context) {
            super(context);
        }

        @Override
        public void onStart() {
            mService = new MultisensoryService(getContext());
            publishBinderService(Context.MULTISENSORY_MANAGER_SERVICE, mService);
        }

        @VisibleForTesting
        MultisensoryService getService() {
            return mService;
        }

        @Override
        public void onBootPhase(int phase) {
            if (phase == SystemService.PHASE_DEVICE_SPECIFIC_SERVICES_READY) {
                mService.initialize();
            }
        }
    }

    private final Context mContext;
    private MultisensoryServiceScope mServiceScope;

    MultisensoryService(Context context) {
        mContext = context;
    }

    @VisibleForTesting
    void initialize() {
        Trace.traceBegin(TRACE_TAG_VIBRATOR, "MultisensoryService#initialize");
        try {
            VibratorManager vibratorManager = mContext.getSystemService(VibratorManager.class);
            Vibrator defaultVibrator = vibratorManager.getDefaultVibrator();
            mServiceScope =
                    new MultisensoryServiceScope(
                            new MultisensoryRepository(),
                            new MultisensoryPlayerDefault(defaultVibrator.getId()),
                            defaultVibrator,
                            mContext.getContentResolver());
        } finally {
            Trace.traceEnd(TRACE_TAG_VIBRATOR);
        }
    }

    /** Return whether the service has initialized */
    public boolean isInitialized() {
        return mServiceScope != null;
    }

    @Override
    @RequiresNoPermission
    public void playToken(int tokenConstant) throws RemoteException {
        Trace.traceBegin(TRACE_TAG_VIBRATOR, "MultisensoryService#playToken");
        try {
            if (isInitialized()) {
                mServiceScope.playToken(tokenConstant);
            } else {
                Slog.w(
                        TAG,
                        "Ignored call to play token "
                                + tokenConstant
                                + ". Service has not initialized");
            }
        } finally {
            Trace.traceEnd(TRACE_TAG_VIBRATOR);
        }
    }

    @Override
    @RequiresNoPermission
    public void openContinuousFeedbackForToken(int tokenConstant) throws RemoteException {
        // TODO(b/475599755): Implement this API
    }

    @Override
    @RequiresNoPermission
    public void startContinuousFeedbackForToken(int tokenConstant) throws RemoteException {
        // TODO(b/475599755): Implement this API
    }

    @Override
    @RequiresNoPermission
    public void modifyContinuousFeedbackForToken(
            int tokenConstant, List<MultisensoryContinuousEffectModifier> modifiers)
            throws RemoteException {
        // TODO(b/475599755): Implement this API
    }

    @Override
    @RequiresNoPermission
    public void stopContinuousFeedbackForToken(int tokenConstant) throws RemoteException {
        // TODO(b/475599755): Implement this API
    }

    @Override
    @EnforcePermission(REMOTE_MULTISENSORY_PLAYBACK)
    public void setPlayer(IMultisensoryPlayer player)
            throws RemoteException, IllegalStateException {
        setPlayer_enforcePermission();
        Trace.traceBegin(TRACE_TAG_VIBRATOR, "MultisensoryService#setPlayer");
        try {
            if (isInitialized()) {
                boolean registeredSuccessfully = mServiceScope.registerRemotePlayer(player);
                if (!registeredSuccessfully) {
                    throw new RemoteException(
                            "The MultisensoryService failed to register the remote player. The"
                                + " player could have been dropped in the registration process");
                }
            } else {
                throw new IllegalStateException(
                        "The MultisensoryService has not initialized and a player cannot be"
                                + " registered");
            }
        } finally {
            Trace.traceEnd(TRACE_TAG_VIBRATOR);
        }
    }
}
