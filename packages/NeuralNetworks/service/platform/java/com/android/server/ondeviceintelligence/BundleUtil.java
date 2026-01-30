/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.ondeviceintelligence;

import static android.system.OsConstants.F_GETFL;
import static android.system.OsConstants.O_ACCMODE;
import static android.system.OsConstants.O_RDONLY;
import static android.system.OsConstants.PROT_READ;

import android.app.ondeviceintelligence.IResponseCallback;
import android.app.ondeviceintelligence.IStreamingResponseCallback;
import android.app.ondeviceintelligence.ITokenInfoCallback;
import android.app.ondeviceintelligence.InferenceInfo;
import android.app.ondeviceintelligence.OnDeviceIntelligenceManager.InferenceParams;
import android.app.ondeviceintelligence.OnDeviceIntelligenceManager.ResponseParams;
import android.app.ondeviceintelligence.OnDeviceIntelligenceManager.StateParams;
import android.app.ondeviceintelligence.TokenInfo;
import android.database.CursorWindow;
import android.graphics.Bitmap;
import android.os.BadParcelableException;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.Parcelable;
import android.os.PersistableBundle;
import android.os.RemoteCallback;
import android.os.RemoteException;
import android.os.SharedMemory;
import android.system.ErrnoException;
import android.system.Os;
import android.util.Log;

import com.android.internal.infra.AndroidFuture;

import java.util.concurrent.Executor;
import java.util.concurrent.TimeoutException;

/**
 * Util methods for ensuring the Bundle passed in various methods are read-only and restricted to
 * some known types.
 */
public class BundleUtil {
    private static final String TAG = "BundleUtil";

    /**
     * Validation of the inference request payload as described in {@link InferenceParams}
     * description.
     *
     * @throws BadParcelableException when the bundle does not meet the read-only requirements.
     */
    public static void sanitizeInferenceParams(@InferenceParams Bundle bundle) {
        ensureValidBundle(bundle);

        if (!bundle.hasFileDescriptors()) {
            return; // safe to exit if there are no FDs and Binders
        }

        for (String key : bundle.keySet()) {
            Object obj = bundle.get(key);
            if (obj == null) {
                /* Null value here could also mean deserializing a custom parcelable has failed,
                 *  and since {@link Bundle} is marked as defusable in system-server - the
                 * {@link ClassNotFoundException} exception is swallowed and `null` is returned
                 * instead. We want to ensure cleanup of null entries in such case.
                 */
                bundle.putObject(key, null);
                continue;
            }
            if (canMarshall(obj) || obj instanceof CursorWindow) {
                continue;
            }
            if (obj instanceof Bundle) {
                sanitizeInferenceParams((Bundle) obj);
            } else if (obj instanceof ParcelFileDescriptor) {
                validatePfdReadOnly((ParcelFileDescriptor) obj);
            } else if (obj instanceof SharedMemory) {
                ((SharedMemory) obj).setProtect(PROT_READ);
            } else if (obj instanceof Bitmap) {
                validateBitmap((Bitmap) obj);
            } else if (obj instanceof Parcelable[]) {
                validateParcelableArray((Parcelable[]) obj);
            } else {
                throw new BadParcelableException(
                        "Unsupported Parcelable type encountered in the Bundle: "
                                + obj.getClass().getSimpleName());
            }
        }
    }

    /**
     * Validation of the inference request payload as described in {@link ResponseParams}
     * description.
     *
     * @throws BadParcelableException when the bundle does not meet the read-only requirements.
     */
    public static void sanitizeResponseParams(@ResponseParams Bundle bundle) {
        ensureValidBundle(bundle);

        if (!bundle.hasFileDescriptors()) {
            return; // safe to exit if there are no FDs and Binders
        }

        for (String key : bundle.keySet()) {
            Object obj = bundle.get(key);
            if (obj == null) {
                /* Null value here could also mean deserializing a custom parcelable has failed,
                 *  and since {@link Bundle} is marked as defusable in system-server - the
                 * {@link ClassNotFoundException} exception is swallowed and `null` is returned
                 * instead. We want to ensure cleanup of null entries in such case.
                 */
                bundle.putObject(key, null);
                continue;
            }
            if (canMarshall(obj)) {
                continue;
            }

            if (obj instanceof Bundle) {
                sanitizeResponseParams((Bundle) obj);
            } else if (obj instanceof ParcelFileDescriptor) {
                validatePfdReadOnly((ParcelFileDescriptor) obj);
            } else if (obj instanceof Bitmap) {
                validateBitmap((Bitmap) obj);
            } else if (obj instanceof Parcelable[]) {
                validateParcelableArray((Parcelable[]) obj);
            } else {
                throw new BadParcelableException(
                        "Unsupported Parcelable type encountered in the Bundle: "
                                + obj.getClass().getSimpleName());
            }
        }
    }

    /**
     * Validation of the inference request payload as described in {@link StateParams} description.
     *
     * @throws BadParcelableException when the bundle does not meet the read-only requirements.
     */
    public static void sanitizeStateParams(@StateParams Bundle bundle) {
        ensureValidBundle(bundle);

        if (!bundle.hasFileDescriptors()) {
            return; // safe to exit if there are no FDs and Binders
        }

        for (String key : bundle.keySet()) {
            Object obj = bundle.get(key);
            if (obj == null) {
                /* Null value here could also mean deserializing a custom parcelable has failed,
                 *  and since {@link Bundle} is marked as defusable in system-server - the
                 * {@link ClassNotFoundException} exception is swallowed and `null` is returned
                 * instead. We want to ensure cleanup of null entries in such case.
                 */
                bundle.putObject(key, null);
                continue;
            }
            if (canMarshall(obj)) {
                continue;
            }

            if (obj instanceof ParcelFileDescriptor) {
                validatePfdReadOnly((ParcelFileDescriptor) obj);
            } else {
                throw new BadParcelableException(
                        "Unsupported Parcelable type encountered in the Bundle: "
                                + obj.getClass().getSimpleName());
            }
        }
    }

    public static IStreamingResponseCallback wrapWithValidation(
            IStreamingResponseCallback streamingResponseCallback,
            Executor resourceClosingExecutor,
            AndroidFuture future,
            InferenceInfoStore inferenceInfoStore,
            boolean shouldForwardInferenceInfo) {
        return new IStreamingResponseCallback.Stub() {
            @Override
            public void onNewContent(Bundle processedResult) throws RemoteException {
                try {
                    sanitizeResponseParams(processedResult);
                    streamingResponseCallback.onNewContent(processedResult);
                } finally {
                    resourceClosingExecutor.execute(() -> tryCloseResource(processedResult));
                }
            }

            @Override
            public void onSuccess(Bundle resultBundle) throws RemoteException {
                try {
                    sanitizeResponseParams(resultBundle);
                    streamingResponseCallback.onSuccess(resultBundle);
                } finally {
                    inferenceInfoStore.addInferenceInfoFromBundle(resultBundle);
                    resourceClosingExecutor.execute(() -> tryCloseResource(resultBundle));
                    future.complete(null);
                }
            }

            @Override
            public void onFailure(int errorCode, String errorMessage, PersistableBundle errorParams)
                    throws RemoteException {
                try {
                    streamingResponseCallback.onFailure(errorCode, errorMessage, errorParams);
                    inferenceInfoStore.addInferenceInfoFromBundle(errorParams);
                } finally {
                    future.complete(null);
                }
            }

            @Override
            public void onDataAugmentRequest(Bundle processedContent, RemoteCallback remoteCallback)
                    throws RemoteException {
                try {
                    sanitizeResponseParams(processedContent);
                    streamingResponseCallback.onDataAugmentRequest(
                            processedContent,
                            new RemoteCallback(
                                    augmentedData -> {
                                        try {
                                            sanitizeInferenceParams(augmentedData);
                                            remoteCallback.sendResult(augmentedData);
                                        } finally {
                                            resourceClosingExecutor.execute(
                                                    () -> tryCloseResource(augmentedData));
                                        }
                                    }));
                } finally {
                    resourceClosingExecutor.execute(() -> tryCloseResource(processedContent));
                }
            }

            @Override
            public void onInferenceInfo(InferenceInfo info) throws RemoteException {
                inferenceInfoStore.add(info);
                if (shouldForwardInferenceInfo) {
                    streamingResponseCallback.onInferenceInfo(info);
                }
            }
        };
    }

    public static IResponseCallback wrapWithValidation(
            IResponseCallback responseCallback,
            Executor resourceClosingExecutor,
            AndroidFuture future,
            InferenceInfoStore inferenceInfoStore,
            boolean shouldForwardInferenceInfo) {
        return new IResponseCallback.Stub() {
            @Override
            public void onSuccess(Bundle resultBundle) throws RemoteException {
                try {
                    sanitizeResponseParams(resultBundle);
                    responseCallback.onSuccess(resultBundle);
                } finally {
                    inferenceInfoStore.addInferenceInfoFromBundle(resultBundle);
                    resourceClosingExecutor.execute(() -> tryCloseResource(resultBundle));
                    future.complete(null);
                }
            }

            @Override
            public void onFailure(int errorCode, String errorMessage, PersistableBundle errorParams)
                    throws RemoteException {
                try {
                    responseCallback.onFailure(errorCode, errorMessage, errorParams);
                    inferenceInfoStore.addInferenceInfoFromBundle(errorParams);
                } finally {
                    future.complete(null);
                }
            }

            @Override
            public void onDataAugmentRequest(Bundle processedContent, RemoteCallback remoteCallback)
                    throws RemoteException {
                try {
                    sanitizeResponseParams(processedContent);
                    responseCallback.onDataAugmentRequest(
                            processedContent,
                            new RemoteCallback(
                                    augmentedData -> {
                                        try {
                                            sanitizeInferenceParams(augmentedData);
                                            remoteCallback.sendResult(augmentedData);
                                        } finally {
                                            resourceClosingExecutor.execute(
                                                    () -> tryCloseResource(augmentedData));
                                        }
                                    }));
                } finally {
                    resourceClosingExecutor.execute(() -> tryCloseResource(processedContent));
                }
            }

            @Override
            public void onInferenceInfo(InferenceInfo info) throws RemoteException {
                inferenceInfoStore.add(info);
                if (shouldForwardInferenceInfo) {
                    responseCallback.onInferenceInfo(info);
                }
            }
        };
    }

    public static ITokenInfoCallback wrapWithValidation(
            ITokenInfoCallback responseCallback,
            AndroidFuture future,
            InferenceInfoStore inferenceInfoStore) {
        return new ITokenInfoCallback.Stub() {
            @Override
            public void onSuccess(TokenInfo tokenInfo) throws RemoteException {
                try {
                    responseCallback.onSuccess(tokenInfo);
                    if (tokenInfo.getInfoParams() != null) {
                        inferenceInfoStore.addInferenceInfoFromBundle(tokenInfo.getInfoParams());
                    }
                } finally {
                    future.complete(null);
                }
            }

            @Override
            public void onFailure(int errorCode, String errorMessage, PersistableBundle errorParams)
                    throws RemoteException {
                try {
                    responseCallback.onFailure(errorCode, errorMessage, errorParams);
                    inferenceInfoStore.addInferenceInfoFromBundle(errorParams);
                } finally {
                    future.complete(null);
                }
            }
        };
    }

    private static boolean canMarshall(Object obj) {
        return obj instanceof byte[]
                || obj instanceof PersistableBundle
                || PersistableBundle.isValidType(obj);
    }

    private static void ensureValidBundle(Bundle bundle) {
        if (bundle == null) {
            throw new IllegalArgumentException("Request passed is expected to be non-null");
        }

        if (bundle.hasBinders() != Bundle.STATUS_BINDERS_NOT_PRESENT) {
            throw new BadParcelableException("Bundle should not contain IBinder objects.");
        }
    }

    private static void validateParcelableArray(Parcelable[] parcelables) {
        if (parcelables.length > 0 && parcelables[0] instanceof ParcelFileDescriptor) {
            // Safe to cast
            validatePfdsReadOnly(parcelables);
        } else if (parcelables.length > 0 && parcelables[0] instanceof Bitmap) {
            validateBitmapsImmutable(parcelables);
        } else {
            throw new BadParcelableException("Could not cast to any known parcelable array");
        }
    }

    public static void validatePfdsReadOnly(Parcelable[] pfds) {
        for (Parcelable pfd : pfds) {
            validatePfdReadOnly((ParcelFileDescriptor) pfd);
        }
    }

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

    private static void validateBitmap(Bitmap obj) {
        if (obj.isMutable()) {
            throw new BadParcelableException(
                    "Encountered a mutable Bitmap in the Bundle at key : " + obj);
        }
    }

    private static void validateBitmapsImmutable(Parcelable[] bitmaps) {
        for (Parcelable bitmap : bitmaps) {
            validateBitmap((Bitmap) bitmap);
        }
    }

    private static void tryCloseParcelableArray(Parcelable[] parcelables) {
        for (Parcelable p : parcelables) {
            try {
                if (p instanceof ParcelFileDescriptor pfd) {
                    pfd.close();
                } else if (p instanceof Bitmap bitmap) {
                    bitmap.recycle();
                }
            } catch (Exception e) {
                Log.e(TAG, "Error closing a resource in a Parcelable array", e);
            }
        }
    }

    public static void tryCloseResource(Bundle bundle) {
        if (bundle == null || bundle.isEmpty()) {
            return;
        }

        for (String key : bundle.keySet()) {
            Object obj = bundle.get(key);

            try {
                // TODO(b/329898589) : This can be cleaned up after the flag passing is fixed.
                if (obj instanceof ParcelFileDescriptor pfd) {
                    pfd.close();
                } else if (obj instanceof CursorWindow cursorWindow) {
                    cursorWindow.close();
                } else if (obj instanceof SharedMemory sharedMemory) {
                    // TODO(b/331796886) : Shared memory should honour parcelable flags.
                    sharedMemory.close();
                } else if (obj instanceof Bitmap bitmap) {
                    bitmap.recycle();
                } else if (obj instanceof Parcelable[] parcelables) {
                    tryCloseParcelableArray(parcelables);
                } else if (obj instanceof Bundle nestedBundle) {
                    tryCloseResource(nestedBundle);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error closing resource with key: " + key, e);
            }
        }
    }
}
