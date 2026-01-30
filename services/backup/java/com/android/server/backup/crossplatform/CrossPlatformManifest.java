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

package com.android.server.backup.crossplatform;

import static com.android.server.backup.UserBackupManagerService.CROSS_PLATFORM_MANIFEST_VERSION;

import android.app.backup.FullBackup.BackupScheme.PlatformSpecificParams;
import android.content.pm.PackageInfo;
import android.util.StringBuilderPrinter;

import com.android.server.backup.BackupUtils;

import java.io.IOException;
import java.util.HexFormat;
import java.util.List;

/**
 * The cross-platform manifest contains metadata that is used during the transfer of data to and
 * from a non-Android platform.
 *
 * @hide
 */
public class CrossPlatformManifest {
    private final String mPackageName;
    private final String mPlatform;
    private final List<PlatformSpecificParams> mPlatformSpecificParams;
    private final List<byte[]> mSignatureHashes;

    /**
     * Create a new cross-platform manifest for the given package, platform and platform specific
     * parameters.
     */
    public static CrossPlatformManifest create(
            PackageInfo packageInfo,
            String platform,
            List<PlatformSpecificParams> platformSpecificParams) {
        return new CrossPlatformManifest(
                packageInfo.packageName,
                platform,
                platformSpecificParams,
                BackupUtils.hashSignatureArray(packageInfo.signingInfo.getApkContentsSigners()));
    }

    CrossPlatformManifest(
            String packageName,
            String platform,
            List<PlatformSpecificParams> platformSpecificParams,
            List<byte[]> signatureHashes) {
        mPackageName = packageName;
        mPlatform = platform;
        mPlatformSpecificParams = platformSpecificParams;
        mSignatureHashes = signatureHashes;
    }

    public String getPackageName() {
        return mPackageName;
    }

    public String getPlatform() {
        return mPlatform;
    }

    public List<PlatformSpecificParams> getPlatformSpecificParams() {
        return mPlatformSpecificParams;
    }

    public List<byte[]> getSignatureHashes() {
        return mSignatureHashes;
    }

    /**
     * Creates the app's manifest as a byte array. All data are strings ending in LF.
     *
     * <p>The manifest format is:
     *
     * <pre>
     *     CROSS_PLATFORM_MANIFEST_VERSION
     *     package name
     *     platform
     *     # of platform specific params
     *     N*
     *       bundle id
     *       team id
     *       content version
     *     # of signatures
     *       N* (hexadecimal representation of the signature hash)
     * </pre>
     */
    public byte[] toByteArray() throws IOException {
        StringBuilder builder = new StringBuilder(4096);
        StringBuilderPrinter printer = new StringBuilderPrinter(builder);

        printer.println(Integer.toString(CROSS_PLATFORM_MANIFEST_VERSION));
        printer.println(mPackageName);
        printer.println(mPlatform);

        printer.println(Integer.toString(mPlatformSpecificParams.size()));
        for (PlatformSpecificParams params : mPlatformSpecificParams) {
            printer.println(params.getBundleId());
            printer.println(params.getTeamId());
            printer.println(params.getContentVersion());
        }

        printer.println(Integer.toString(mSignatureHashes.size()));
        for (byte[] hash : mSignatureHashes) {
            printer.println(HexFormat.of().formatHex(hash));
        }

        return builder.toString().getBytes();
    }

    /** Parses the cross-platform manifest bytes created by {@link #toByteArray}. */
    public static CrossPlatformManifest parseFrom(byte[] manifestBytes) throws IOException {
        return CrossPlatformManifestParser.parse(manifestBytes);
    }
}
