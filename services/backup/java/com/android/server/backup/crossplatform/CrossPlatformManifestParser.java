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

import android.annotation.Nullable;
import android.app.backup.FullBackup.BackupScheme.PlatformSpecificParams;
import android.text.TextUtils;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;

/**
 * Parses a cross-platform manifest file stored in a backup.
 *
 * @hide
 */
public class CrossPlatformManifestParser {
    /**
     * Takes the contents of a cross platform manifest file and returns a {@link
     * CrossPlatformManifest}.
     *
     * @throws IOException when encountering an unsupported version, incomplete manifest or any
     *     other errors during parsing.
     */
    static CrossPlatformManifest parse(byte[] manifestBytes) throws IOException {
        CrossPlatformManifestParser reader =
                new CrossPlatformManifestParser(new ByteArrayInputStream(manifestBytes));
        return reader.parse();
    }

    private final InputStream mInput;

    private CrossPlatformManifestParser(InputStream inputStream) {
        mInput = inputStream;
    }

    /**
     * Reads the app's cross-platform manifest from a byte stream.
     *
     * @see CrossPlatformManifest#toByteArray() for a description of the manifest format.
     */
    private CrossPlatformManifest parse() throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(mInput))) {
            String version = reader.readLine();
            if (!TextUtils.equals(version, Integer.toString(CROSS_PLATFORM_MANIFEST_VERSION))) {
                throw new IOException("Unsupported cross-platform manifest version: " + version);
            }

            String packageName = reader.readLine();
            if (packageName == null) {
                throw new IOException("Incomplete cross-platform manifest: missing package");
            }

            String platform = reader.readLine();
            if (platform == null) {
                throw new IOException("Incomplete cross-platform manifest: missing platform");
            }

            Integer paramsSize = parseInt(reader.readLine());
            if (paramsSize == null) {
                throw new IOException("Incomplete cross-platform manifest: missing params");
            }

            List<PlatformSpecificParams> platformSpecificParams = Collections.emptyList();
            if (paramsSize > 0) {
                platformSpecificParams = parsePlatformSpecificParams(reader, paramsSize);
            }

            Integer hashesSize = parseInt(reader.readLine());
            if (hashesSize == null) {
                throw new IOException("Incomplete cross-platform manifest: missing signatures");
            }

            List<byte[]> signatureHashes = Collections.emptyList();
            if (hashesSize > 0) {
                signatureHashes = parseSignatureHashes(reader, hashesSize);
            }

            return new CrossPlatformManifest(
                            packageName, platform, platformSpecificParams, signatureHashes);
        }
    }

    private List<PlatformSpecificParams> parsePlatformSpecificParams(
            BufferedReader reader, int numParams) throws IOException {
        ArrayList<PlatformSpecificParams> params = new ArrayList<>(numParams);
        for (int i = 0; i < numParams; i++) {
            String bundleId = reader.readLine();
            if (bundleId == null) {
                throw new IOException(
                        "Incomplete cross-platform manifest: missing bundle id parameter");
            }
            String teamId = reader.readLine();
            if (teamId == null) {
                throw new IOException(
                        "Incomplete cross-platform manifest: missing team id parameter");
            }
            String contentVersion = reader.readLine();
            if (contentVersion == null) {
                throw new IOException(
                        "Incomplete cross-platform manifest: missing content version parameter");
            }
            params.add(new PlatformSpecificParams(bundleId, teamId, contentVersion));
        }
        return params;
    }

    private List<byte[]> parseSignatureHashes(BufferedReader reader, int numSignatureHashes)
            throws IOException {
        ArrayList<byte[]> hashes = new ArrayList<>(numSignatureHashes);
        for (int i = 0; i < numSignatureHashes; i++) {
            String hash = reader.readLine();
            if (hash == null) {
                throw new IOException("Incomplete cross-platform manifest: missing signature");
            }
            hashes.add(HexFormat.of().parseHex(hash));
        }
        return hashes;
    }

    private static Integer parseInt(@Nullable String number) {
        if (number == null) {
            return null;
        }
        return Integer.parseInt(number);
    }
}
