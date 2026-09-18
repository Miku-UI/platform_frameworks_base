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

package com.android.server.contentsafety;

import static android.system.OsConstants.F_GETFL;
import static android.system.OsConstants.O_ACCMODE;
import static android.system.OsConstants.O_RDONLY;

import android.app.contentsafety.ContentSafetyManager;
import android.app.contentsafety.ContentSafetyManager.CheckContentParams;
import android.os.BadParcelableException;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.Parcelable;
import android.service.contentsafety.ContentSafetySandboxedService.CheckLoadFeatureParams;
import android.system.ErrnoException;
import android.system.Os;
import android.text.TextUtils;
import android.util.ArraySet;
import android.util.Log;

import com.android.framework.protobuf.ByteString;

import java.util.ArrayList;
import java.util.Set;

/**
 * Util methods for ensuring the Bundle passed in various methods are read-only and restricted to
 * some known types.
 */
public class ContentSafetyBundleUtil {
    private static final String TAG = "BundleUtil";

    /**
     * Validation of the inference request payload as described in {@link CheckContentParams}
     * description.
     *
     * @throws BadParcelableException when the bundle does not meet the read-only requirements.
     */
    public static void sanitizeCheckContentParams(@CheckContentParams Bundle bundle) {
        ensureValidBundle(bundle);

        for (String key : bundle.keySet()) {
            ensureValidKey(key);

            // try to get list of ParcelableFileDescriptors
            Object obj = bundle.getParcelableArrayList(key, ParcelFileDescriptor.class);
            if (obj != null) {
                // validate each file descriptor.
                for (ParcelFileDescriptor file : (ArrayList<ParcelFileDescriptor>) obj) {
                    validatePfdReadOnly(file);
                }
            }

            if (obj == null) {
                throw new BadParcelableException(
                        "Unsupported Parcelable type encountered in the Bundle");
            }
        }
    }

    /**
     * Validation of the loadfeature parameter that contains feature files/byte {@link
     * CheckLoadFeatureParams}.
     *
     * @throws BadParcelableException when the bundle does not meet the read-only requirements.
     */
    public static void sanitizeCheckLoadFeatureParams(@CheckLoadFeatureParams Bundle bundle) {
        ensureValidBundle(bundle);

        for (String key : bundle.keySet()) {
            Object obj = bundle.getParcelableArrayList(key, ParcelFileDescriptor.class);
            if (obj != null) {
                // validate each file descriptor.
                for (ParcelFileDescriptor file : (ArrayList<ParcelFileDescriptor>) obj) {
                    validatePfdReadOnly(file);
                }
            } else {
                /* ByteString are immutable and thus read-ony enforced. If byte[] is passed,
                an exception will be thrown since byte[] is mutable. */
                obj = bundle.getParcelableArrayList(key, ByteString.class);
                if (obj == null) {
                    /* Null value here could also mean deserializing has failed */
                    throw new BadParcelableException(
                            "Unsupported Parcelable type encountered in the Bundle: "
                                    + obj.getClass().getSimpleName());
                }
            }
        }
    }

    private static void ensureValidBundle(Bundle bundle) {
        if (bundle == null) {
            throw new IllegalArgumentException("Request passed is expected to be non-null");
        }

        if (bundle.hasBinders() != Bundle.STATUS_BINDERS_NOT_PRESENT) {
            throw new BadParcelableException("Bundle should not contain IBinder objects.");
        }
    }

    private static void ensureValidKey(String key) {
        // Set of valid keys for runtime checking
        Set<Integer> featureTypeSet = getFeatureTypeSet();
        try {
            int keyInt = Integer.parseInt(key);
            if (!featureTypeSet.contains(keyInt)) {
                throw new BadParcelableException(
                        TextUtils.formatSimple("Bundle key %d is not valid.", keyInt));
            }
        } catch (NumberFormatException e) {
            throw new BadParcelableException("Bundle key is null.");
        }
    }

    private static Set<Integer> getFeatureTypeSet() {
        Set<Integer> featureTypeSet = new ArraySet<>();
        featureTypeSet.add(ContentSafetyManager.SENSITIVE_IMAGE);
        featureTypeSet.add(ContentSafetyManager.SENSITIVE_VIDEO);
        return featureTypeSet;
    }

    /**
     * Validate ParcelFileDescriptor files to be read only.
     *
     * @param pfds list of parelFileDescriptors.
     */
    public static void validatePfdsReadOnly(Parcelable[] pfds) {
        for (Parcelable pfd : pfds) {
            validatePfdReadOnly((ParcelFileDescriptor) pfd);
        }
    }

    /**
     * Make sure the provided file had read only mode.
     *
     * @param pfd the parcelFileDescriptor input
     * @throws BadParcelableException if files are not read only.
     */
    public static void validatePfdReadOnly(ParcelFileDescriptor pfd) {
        if (pfd == null) {
            return;
        }
        try {
            int readMode = Os.fcntlInt(pfd.getFileDescriptor(), F_GETFL, 0) & O_ACCMODE;
            if (readMode != O_RDONLY) {
                throw new BadParcelableException(
                        "Bundle contains a parcel file descriptor which is not read-only.");
            }
        } catch (ErrnoException e) {
            throw new BadParcelableException("Invalid File descriptor passed in the Bundle.", e);
        }
    }

    /**
     * Make sure file descriptors that are included in the bundle are closed propoerly.
     *
     * @param bundle input files to be closed.
     */
    public static void tryCloseResource(Bundle bundle) {
        if (bundle == null || bundle.isEmpty() || !bundle.hasFileDescriptors()) {
            return;
        }

        for (String key : bundle.keySet()) {
            Object obj = bundle.get(key);

            try {
                if (obj instanceof ParcelFileDescriptor) {
                    ((ParcelFileDescriptor) obj).close();
                }
            } catch (Exception e) {
                Log.e(TAG, "Error closing resource with key: " + key, e);
            }
        }
    }
}
