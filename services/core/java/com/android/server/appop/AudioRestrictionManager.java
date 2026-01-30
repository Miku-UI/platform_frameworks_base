/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.server.appop;

import static android.app.AppOpsManager.MODE_ALLOWED;
import static android.app.AppOpsManager.MODE_IGNORED;
import static android.app.AppOpsManager.MODE_ERRORED;
import static android.app.AppOpsManager.MODE_DEFAULT;
import static android.app.AppOpsManager.MODE_FOREGROUND;

import android.app.AppOpsManager;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraDevice.CAMERA_AUDIO_RESTRICTION;
import android.media.AudioAttributes;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Slog;
import com.android.server.utils.EventLogger;
import android.util.SparseArray;
import android.util.SparseBooleanArray;

import com.android.internal.util.Preconditions;

import java.io.PrintWriter;
import java.util.Objects;
import java.util.Set;

/**
 * AudioRestrictionManager host all audio restriction related logic and states for AppOpsService.
 */
public class AudioRestrictionManager {
    static final String TAG = "AudioRestriction";

    // Audio restrictions coming from Zen mode API
    final ArrayMap<Key, Restriction> mZenModeAudioRestrictions = new ArrayMap<>();
    // Audio restrictions coming from Camera2 API
    @CAMERA_AUDIO_RESTRICTION int mCameraAudioRestriction = CameraDevice.AUDIO_RESTRICTION_NONE;

    final EventLogger mEventLogger = new EventLogger(/* size= */ 80, "Historical restrictions");
    // Predefined <code, usages> camera audio restriction settings
    static final SparseArray<SparseBooleanArray> CAMERA_AUDIO_RESTRICTIONS;

    static {
        SparseBooleanArray audioMutedUsages = new SparseBooleanArray();
        SparseBooleanArray vibrationMutedUsages = new SparseBooleanArray();
        for (int usage : AudioAttributes.SDK_USAGES.toArray()) {
            final int suppressionBehavior = AudioAttributes.SUPPRESSIBLE_USAGES.get(usage);
            if (suppressionBehavior == AudioAttributes.SUPPRESSIBLE_NOTIFICATION ||
                    suppressionBehavior == AudioAttributes.SUPPRESSIBLE_CALL ||
                    suppressionBehavior == AudioAttributes.SUPPRESSIBLE_ALARM) {
                audioMutedUsages.append(usage, true);
                vibrationMutedUsages.append(usage, true);
            } else if (suppressionBehavior != AudioAttributes.SUPPRESSIBLE_MEDIA &&
                    suppressionBehavior != AudioAttributes.SUPPRESSIBLE_SYSTEM &&
                    suppressionBehavior != AudioAttributes.SUPPRESSIBLE_NEVER) {
                Slog.e(TAG, "Unknown audio suppression behavior" + suppressionBehavior);
            }
        }
        CAMERA_AUDIO_RESTRICTIONS = new SparseArray<>();
        CAMERA_AUDIO_RESTRICTIONS.append(AppOpsManager.OP_PLAY_AUDIO, audioMutedUsages);
        CAMERA_AUDIO_RESTRICTIONS.append(AppOpsManager.OP_VIBRATE, vibrationMutedUsages);
    }

    private record Key(int op, int usage) {
        @Override
        public String toString() {
            return "op: "
                    + AppOpsManager.opToName(op)
                    + ", usage: "
                    + AudioAttributes.usageToString(usage);
        }
    }

    private record Restriction(int mode, Set<String> exceptionPackages) {
        Restriction {
            Objects.requireNonNull(exceptionPackages);
            // Should not be MODE_ALLOWED
            Preconditions.checkArgument(mode == MODE_IGNORED || mode == MODE_DEFAULT
                    || mode == MODE_ERRORED || mode == MODE_FOREGROUND,
                    "Invalid mode for restriction %d", mode);
        }
        @Override
        public String toString() {
            return "Restriction[mode: "
                    + AppOpsManager.modeToName(mode)
                    + ", exceptions: "
                    + exceptionPackages;
        }
    }

    private static class ZenRestrictionEvent extends EventLogger.Event {
        private Key mKey;
        private Restriction mRestriction;
        ZenRestrictionEvent(Key k, Restriction r) {
            mKey = k;
            mRestriction = r;
        }
        @Override
        public String eventToString() {
            return mKey.toString() + ": " + ((mRestriction != null) ?
                    mRestriction.toString() : "restriction removed");
        }
    }

    public int checkAudioOperation(int code, int usage, int uid, String packageName) {
        synchronized (this) {
            // Check for camera audio restrictions
            if (mCameraAudioRestriction != CameraDevice.AUDIO_RESTRICTION_NONE) {
                if (code == AppOpsManager.OP_VIBRATE || (code == AppOpsManager.OP_PLAY_AUDIO &&
                        mCameraAudioRestriction ==
                                CameraDevice.AUDIO_RESTRICTION_VIBRATION_SOUND)) {
                    final SparseBooleanArray mutedUsages = CAMERA_AUDIO_RESTRICTIONS.get(code);
                    if (mutedUsages != null) {
                        if (mutedUsages.get(usage)) {
                            return MODE_IGNORED;
                        }
                    }
                }
            }

            final int mode = checkZenModeRestrictionLocked(code, usage, packageName);
            if (mode != MODE_ALLOWED) {
                return mode;
            }
        }
        return MODE_ALLOWED;
    }

    private int checkZenModeRestrictionLocked(int code, int usage, String packageName) {
        final Restriction r = mZenModeAudioRestrictions.get(new Key(code, usage));
        if (r != null && (packageName == null || !r.exceptionPackages.contains(packageName))) {
                return r.mode;
        } else {
            return MODE_ALLOWED;
        }
    }

    public void setZenModeAudioRestriction(int code, int usage, int mode,
            String[] exceptionPackages) {
        synchronized (this) {
            final var k = new Key(code, usage);
            if (mode != MODE_ALLOWED) {
                final var r = new Restriction(mode, exceptionPackages != null ?
                                              Set.of(exceptionPackages) : Set.of());
                if (!r.equals(mZenModeAudioRestrictions.put(k, r))) {
                    mEventLogger.enqueue(new ZenRestrictionEvent(k, r));
                }
            } else {
                if (mZenModeAudioRestrictions.remove(k) != null) {
                    mEventLogger.enqueue(new ZenRestrictionEvent(k, null));
                }
            }
        }
    }

    public void setCameraAudioRestriction(@CAMERA_AUDIO_RESTRICTION int mode) {
        synchronized (this) {
            mCameraAudioRestriction = mode;
        }
    }

    // return: needSep used by AppOpsService#dump
    public boolean dump(PrintWriter pw) {
        pw.println("  Audio Restrictions:");
        synchronized (this) {
            pw.println("   Zen Audio Restriction Mode: ");
            mZenModeAudioRestrictions.forEach((k, v) -> pw.println("    " + k + ": " + v));
            pw.println("   Camera Audio Restriction Mode: " +
                    cameraRestrictionModeToName(mCameraAudioRestriction));
        }
        pw.println();
        mEventLogger.dump(pw, "    ");
        pw.println();
        return true;
    }

    private static String cameraRestrictionModeToName(@CAMERA_AUDIO_RESTRICTION int mode) {
        switch (mode) {
            case CameraDevice.AUDIO_RESTRICTION_NONE:
                return "None";
            case CameraDevice.AUDIO_RESTRICTION_VIBRATION:
                return "MuteVibration";
            case CameraDevice.AUDIO_RESTRICTION_VIBRATION_SOUND:
                return "MuteVibrationAndSound";
            default:
                return "Unknown";
        }
    }

}
