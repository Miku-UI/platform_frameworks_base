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

package com.android.server.vibrator;

import android.Manifest;
import android.annotation.EnforcePermission;
import android.annotation.NonNull;
import android.os.RemoteException;
import android.os.vibrator.IHapticChannelStream;

/**
 * A haptic generator stream
 *
 * <p>This stream represents a single, active conversion of a {@link android.os.VibrationEffect}
 * into haptic PCM data. It is created by a {@link HapticGeneratorSession}.
 *
 * <p>This object handles reading PCM data from the HAL and ensures the stream is stopped when
 * {@link #close()} is called.
 */
public class HapticGeneratorChannelStream extends IHapticChannelStream.Stub {
    private final HapticGeneratorSession mHapticGeneratorSession;
    private final long mStreamId;

    /**
     * Creates a new haptic channel stream implementation.
     */
    HapticGeneratorChannelStream(
            @NonNull HapticGeneratorSession session,
            long streamId) {
        mHapticGeneratorSession = session;
        mStreamId = streamId;
    }

    @Override
    @EnforcePermission(Manifest.permission.USE_VIBRATOR_HAPTIC_GENERATOR)
    public int read(byte[] buffer) throws RemoteException {
        read_enforcePermission();
        return mHapticGeneratorSession.readHapticGeneratorStream(mStreamId, buffer);
    }

    @Override
    @EnforcePermission(Manifest.permission.USE_VIBRATOR_HAPTIC_GENERATOR)
    public boolean close() {
        close_enforcePermission();
        return mHapticGeneratorSession.stopHapticGeneratorStream(mStreamId);
    }
}
