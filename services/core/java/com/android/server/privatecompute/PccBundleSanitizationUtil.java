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

package com.android.server.privatecompute;

import static com.android.os.privatecompute.PrivateComputeAtomsLog.PCC_INPUT_SANITIZATION_REPORTED__RESULT__FAILED;
import static com.android.os.privatecompute.PrivateComputeAtomsLog.PCC_INPUT_SANITIZATION_REPORTED__RESULT__PERFORMED;
import static com.android.os.privatecompute.PrivateComputeAtomsLog.PCC_INPUT_SANITIZATION_REPORTED__RESULT__SKIPPED;

import android.graphics.Bitmap;
import android.os.BadParcelableException;
import android.os.BaseBundle;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.Parcelable;
import android.os.SharedMemory;
import android.os.SystemClock;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;

/**
 * Utility class to ensure that the bundle passed to the Private Computer Core (PCC) service only
 * contains safe data types.
 */
class PccBundleSanitizationUtil {
    private static final int MAX_BUNDLE_DEPTH = 100;

    /**
     * Validates the bundle to ensure it contains only safe data types.
     *
     * <p> Safe data types strictly enforce a one-way data flow, and cannot be used to establish a
     * two-way communication with the PCC service.
     *
     * @param baseBundle the {@link android.os.BaseBundle} to be sanitized.
     * @throws IllegalArgumentException if the bundle contains data types not in the allowlist
     * or exceeds a recursive depth of 100.
     */
    public static void sanitizeBundle(BaseBundle baseBundle) throws IllegalArgumentException {
        long startTimeNanos = SystemClock.elapsedRealtimeNanos();
        int sanitizationResult = PCC_INPUT_SANITIZATION_REPORTED__RESULT__SKIPPED;
        try {
            if (baseBundle == null) {
                return;
            }

            if (baseBundle instanceof Bundle) {
                Bundle bundle = (Bundle) baseBundle;
                if (bundle.hasBinders() != Bundle.STATUS_BINDERS_NOT_PRESENT) {
                    throw new IllegalArgumentException(
                            "Bundle contains unresolved Parcelables or Binders which are not"
                                    + " permitted.");
                }
            }

            sanitizationResult = PCC_INPUT_SANITIZATION_REPORTED__RESULT__PERFORMED;
            sanitizeBundleInternal(baseBundle, 0);
        } catch (IllegalArgumentException e) {
            sanitizationResult = PCC_INPUT_SANITIZATION_REPORTED__RESULT__FAILED;
            throw e;
        } finally {
            PrivateComputeStatsLogUtil.logPccBundleInputSanitizationLatency(
                    SystemClock.elapsedRealtimeNanos() - startTimeNanos, sanitizationResult);
        }
    }

    private static void sanitizeBundleInternal(BaseBundle bundle, int depth) {
        if (depth > MAX_BUNDLE_DEPTH) {
            throw new IllegalArgumentException("Bundle depth exceeds limit of " + MAX_BUNDLE_DEPTH);
        }

        for (String key : bundle.keySet()) {
            Object value;
            try {
                value = bundle.get(key);
                if (value == null) {
                    throw new IllegalArgumentException("Disallowed null value for key: " + key
                            + ". This can happen if a null value is sent, or a custom parcelable "
                            + "is not recognized by the system. Consider sending it as a byte[].");
                }
            } catch (BadParcelableException e) {
                throw new IllegalArgumentException(
                        "Parcelable not recognized. Consider sending it as a byte[].");
            }

            if (isSafeType(value)) {
                continue;
            }
            switch (value) {
                case BaseBundle subBundle -> sanitizeBundleInternal(subBundle, depth + 1);
                case ParcelFileDescriptor pfd -> validatePfdIsReadOnly(pfd);
                case SharedMemory sm -> {
                    if (com.android.libcore.Flags.enablePccFrameworkSupport()) {
                        if (!sm.isRegionReadOnly()) {
                            throw new IllegalArgumentException("SharedMemory must be read-only.");
                        }
                    } else {
                        sm.setProtect(OsConstants.PROT_READ);
                    }
                }
                // Bitmaps are considered safe to send across IPC. When a Bitmap is parceled, it
                // is either copied into a new blob or, if immutable and backed by shared memory,
                // its file descriptor is transferred. In either case, the receiving process gets
                // a read-only copy and cannot modify the original Bitmap data.
                case Bitmap ignored -> {
                }
                case Parcelable[] parcelables -> validateParcelableArray(parcelables);
                default -> throw new IllegalArgumentException(
                        "Type not permitted: " + value.getClass().getName());
            }
        }
    }

    /**
     * Returns true if the object is a safe type like primitives, PersistableBundle, byte[].
     */
    private static boolean isSafeType(Object value) {
        return (value instanceof Byte) || (value instanceof Character) || (value instanceof Short)
                || (value instanceof Integer) || (value instanceof Long) || (value instanceof Float)
                || (value instanceof Double) || (value instanceof Boolean)
                || (value instanceof String) || (value instanceof byte[])
                || (value instanceof char[]) || (value instanceof short[])
                || (value instanceof int[]) || (value instanceof long[])
                || (value instanceof float[]) || (value instanceof double[])
                || (value instanceof boolean[]) || (value instanceof String[]);
    }

    private static void validatePfdIsReadOnly(ParcelFileDescriptor pfd) {
        try {
            int mode = Os.fcntlInt(pfd.getFileDescriptor(), OsConstants.F_GETFL, 0);
            if ((mode & OsConstants.O_ACCMODE) != OsConstants.O_RDONLY) {
                throw new IllegalArgumentException("ParcelFileDescriptor must be read-only.");
            }
        } catch (ErrnoException e) {
            throw new IllegalArgumentException("Invalid FileDescriptor", e);
        }

    }

    private static void validateParcelableArray(Parcelable[] parcelables) {
        for (Parcelable p : parcelables) {
            switch (p) {
                case ParcelFileDescriptor parcelFileDescriptor -> validatePfdIsReadOnly(
                        parcelFileDescriptor);
                case SharedMemory sharedMemory -> {
                    if (com.android.libcore.Flags.enablePccFrameworkSupport()) {
                        if (!sharedMemory.isRegionReadOnly()) {
                            throw new IllegalArgumentException("SharedMemory must be read-only.");
                        }
                    } else {
                        sharedMemory.setProtect(OsConstants.PROT_READ);
                    }
                }
                case Bitmap ignored -> {
                }
                case null -> {
                    // no-op
                }
                default -> throw new IllegalArgumentException(
                        "Custom parcelables not permitted. Custom parcelable must be sent as byte[]"
                                + p.getClass().getName());
            }
        }
    }
}
