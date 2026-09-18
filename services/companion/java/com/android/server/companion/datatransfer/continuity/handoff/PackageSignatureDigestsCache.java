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

package com.android.server.companion.datatransfer.continuity.handoff;

import android.annotation.NonNull;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.SigningInfo;
import android.util.ArrayMap;
import android.util.Slog;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import javax.annotation.concurrent.GuardedBy;

public class PackageSignatureDigestsCache {

    private static final String TAG = PackageSignatureDigestsCache.class.getSimpleName();

    private final PackageManager mPackageManager;

    @GuardedBy("this")
    private final ArrayMap<String, byte[][]> mPackageSignatureDigestsMap = new ArrayMap<>();

    public PackageSignatureDigestsCache(@NonNull PackageManager packageManager) {
        mPackageManager = Objects.requireNonNull(packageManager);
    }

    @NonNull
    public byte[][] getSignatureDigestsForPackage(@NonNull String packageName) {
        Objects.requireNonNull(packageName);

        synchronized (this) {
            if (mPackageSignatureDigestsMap.containsKey(packageName)) {
                return mPackageSignatureDigestsMap.get(packageName);
            }
            PackageInfo packageInfo;
            try {
                packageInfo =
                        mPackageManager.getPackageInfo(
                                packageName, PackageManager.GET_SIGNING_CERTIFICATES);
            } catch (PackageManager.NameNotFoundException e) {
                Slog.w(TAG, "Package not found for signature collection: " + packageName);
                return new byte[][] {};
            }

            if (packageInfo == null) {
                return new byte[][] {};
            }

            SigningInfo signingInfo = packageInfo.signingInfo;
            if (signingInfo == null
                    || signingInfo.getApkContentsSigners() == null
                    || signingInfo.getApkContentsSigners().length == 0) {
                Slog.w(TAG, "No signing info found for package: " + packageName);
                return new byte[][] {};
            }

            byte[][] signatureDigests = new byte[signingInfo.getApkContentsSigners().length][];
            try {
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                for (int i = 0; i < signingInfo.getApkContentsSigners().length; i++) {
                    signatureDigests[i] =
                            md.digest(signingInfo.getApkContentsSigners()[i].toByteArray());
                }
            } catch (NoSuchAlgorithmException e) {
                Slog.e(TAG, "SHA-256 not available", e);
                return new byte[][] {};
            }

            mPackageSignatureDigestsMap.put(packageName, signatureDigests);
            return signatureDigests;
        }
    }
}
