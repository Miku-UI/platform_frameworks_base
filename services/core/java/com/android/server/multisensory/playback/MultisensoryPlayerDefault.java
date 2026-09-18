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
package com.android.server.multisensory.playback;

import android.annotation.NonNull;
import android.os.Binder;
import android.os.VibrationAttributes;
import android.os.VibrationEffect;
import android.os.multisensory.MultisensoryManager;
import android.util.Slog;

import com.android.server.LocalServices;
import com.android.server.multisensory.MultisensoryServiceScope;
import com.android.server.vibrator.VibratorManagerInternal;

/**
 * A default haptic player to be used in the Multisensory Design System.
 *
 * <p>The player is specifically designed to deliver haptics without the need of the VIBRATE
 * permission, and its usage is strictly restricted to the {@link
 * com.android.server.multisensory.MultisensoryService}.
 *
 * @hide
 */
public final class MultisensoryPlayerDefault {
    private static final String TAG = "MultisensoryPlayerDefault";
    private final int mVibratorId;
    private final VibratorManagerInternal mLocalService;

    private final Binder mBinderToken = new Binder();

    public MultisensoryPlayerDefault(int vibratorId) {
        mVibratorId = vibratorId;
        mLocalService = LocalServices.getService(VibratorManagerInternal.class);
    }

    /** Vibrates the device for a given multisensory token without performing permission checks. */
    public void play(
            @MultisensoryManager.Token int tokenConstant,
            @NonNull VibrationEffect effect,
            @NonNull VibrationAttributes attributes) {
        if (android.os.vibrator.Flags.enableTrustedCallers() && mLocalService != null) {
            final long identity = Binder.clearCallingIdentity();
            try {
                String reason = "haptic effect for multisensory token " + tokenConstant;
                mLocalService.vibrateWithoutPermissionCheck(
                        mVibratorId, effect, attributes, reason, mBinderToken);
            } finally {
                Binder.restoreCallingIdentity(identity);
            }
        } else {
            Slog.d(TAG, "Unable to play effect for " + tokenConstant);
        }
    }

    /**
     * Cancel ongoing vibrations with the usage filter from vibration attributes used in the {@link
     * MultisensoryServiceScope}.
     */
    public void cancel() {
        int usageFilter = getMultisensoryVibrationUsageFilter();
        if (android.os.vibrator.Flags.enableTrustedCallers() && mLocalService != null) {
            final long identity = Binder.clearCallingIdentity();
            try {
                mLocalService.cancelVibrateWithoutPermissionCheck(usageFilter, mBinderToken);
            } finally {
                Binder.restoreCallingIdentity(identity);
            }
        } else {
            Slog.d(TAG, "Unable to cancel vibrations with usage filter" + usageFilter);
        }
    }

    private int getMultisensoryVibrationUsageFilter() {
        return MultisensoryServiceScope.sHardwareVibrationAttributes.getUsage()
                | MultisensoryServiceScope.sTouchVibrationAttributes.getUsage();
    }
}
