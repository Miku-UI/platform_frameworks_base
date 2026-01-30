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

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.app.backup.FullBackup.BackupScheme.PlatformSpecificParams;

import com.android.server.backup.fullbackup.ShadowSigningInfo;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.IOException;
import java.util.HexFormat;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(
        shadows = {
            ShadowSigningInfo.class,
        })
public class CrossPlatformManifestParserTest {
    private static final String PACKAGE_NAME = "com.example.app";
    private static final String PLATFORM = "ios";
    private static final String BUNDLE_ID = "com.example.bundleid";
    private static final String TEAM_ID = "A1B2C3D4";
    private static final String CONTENT_VERSION = "1.0";
    private static final String SIGNATURE_HASH_1 = "1234";
    private static final String SIGNATURE_HASH_2 = "5678";

    @Test
    public void parse_validManifest_returnsManifest() throws IOException {
        String manifestContent =
                "1\n"
                        + PACKAGE_NAME
                        + "\n"
                        + PLATFORM
                        + "\n"
                        + "1\n"
                        + BUNDLE_ID
                        + "\n"
                        + TEAM_ID
                        + "\n"
                        + CONTENT_VERSION
                        + "\n"
                        + "2\n"
                        + SIGNATURE_HASH_1
                        + "\n"
                        + SIGNATURE_HASH_2
                        + "\n";

        CrossPlatformManifest manifest =
                CrossPlatformManifestParser.parse(manifestContent.getBytes());

        assertThat(manifest).isNotNull();
        assertThat(manifest.getPackageName()).isEqualTo(PACKAGE_NAME);
        assertThat(manifest.getPlatform()).isEqualTo(PLATFORM);
        assertThat(manifest.getPlatformSpecificParams())
                .containsExactly(new PlatformSpecificParams(BUNDLE_ID, TEAM_ID, CONTENT_VERSION));
        List<byte[]> signatureHashes = manifest.getSignatureHashes();
        assertThat(signatureHashes).hasSize(2);
        assertThat(signatureHashes.get(0)).isEqualTo(HexFormat.of().parseHex(SIGNATURE_HASH_1));
        assertThat(signatureHashes.get(1)).isEqualTo(HexFormat.of().parseHex(SIGNATURE_HASH_2));
    }

    @Test
    public void parse_unsupportedVersion_throwsException() {
        String manifestContent = "0\n";

        IOException e =
                assertThrows(
                        IOException.class,
                        () -> CrossPlatformManifestParser.parse(manifestContent.getBytes()));
        assertThat(e).hasMessageThat().isEqualTo("Unsupported cross-platform manifest version: 0");
    }

    @Test
    public void parse_emptyManifest_throwsException() {
        byte[] manifestBytes = new byte[0];

        IOException e =
                assertThrows(
                        IOException.class, () -> CrossPlatformManifestParser.parse(manifestBytes));
        assertThat(e)
                .hasMessageThat()
                .isEqualTo("Unsupported cross-platform manifest version: null");
    }

    @Test
    public void parse_missingPackageName_throwsException() {
        String manifestContent = "1\n";

        IOException e =
                assertThrows(
                        IOException.class,
                        () -> CrossPlatformManifestParser.parse(manifestContent.getBytes()));
        assertThat(e)
                .hasMessageThat()
                .isEqualTo("Incomplete cross-platform manifest: missing package");
    }

    @Test
    public void parse_missingPlatform_throwsException() {
        String manifestContent = "1\n" + PACKAGE_NAME + "\n";

        IOException e =
                assertThrows(
                        IOException.class,
                        () -> CrossPlatformManifestParser.parse(manifestContent.getBytes()));
        assertThat(e)
                .hasMessageThat()
                .isEqualTo("Incomplete cross-platform manifest: missing platform");
    }

    @Test
    public void parse_missingParamsSize_throwsException() {
        String manifestContent = "1\n" + PACKAGE_NAME + "\n" + PLATFORM + "\n";

        IOException e =
                assertThrows(
                        IOException.class,
                        () -> CrossPlatformManifestParser.parse(manifestContent.getBytes()));
        assertThat(e)
                .hasMessageThat()
                .isEqualTo("Incomplete cross-platform manifest: missing params");
    }

    @Test
    public void parse_missingSignaturesSize_throwsException() {
        String manifestContent = "1\n" + PACKAGE_NAME + "\n" + PLATFORM + "\n" + "0\n";

        IOException e =
                assertThrows(
                        IOException.class,
                        () -> CrossPlatformManifestParser.parse(manifestContent.getBytes()));
        assertThat(e)
                .hasMessageThat()
                .isEqualTo("Incomplete cross-platform manifest: missing signatures");
    }

    @Test
    public void parse_incompletePlatformSpecificParams_throwsException() {
        String manifestContent =
                "1\n"
                        + PACKAGE_NAME
                        + "\n"
                        + PLATFORM
                        + "\n"
                        + "1\n"
                        + BUNDLE_ID
                        + "\n"
                        + TEAM_ID
                        + "\n";

        IOException e =
                assertThrows(
                        IOException.class,
                        () -> CrossPlatformManifestParser.parse(manifestContent.getBytes()));
        assertThat(e)
                .hasMessageThat()
                .isEqualTo("Incomplete cross-platform manifest: missing content version parameter");
    }

    @Test
    public void parse_incompleteSignatures_throwsException() {
        String manifestContent =
                "1\n"
                        + PACKAGE_NAME
                        + "\n"
                        + PLATFORM
                        + "\n"
                        + "0\n"
                        + "2\n"
                        + SIGNATURE_HASH_1
                        + "\n";

        IOException e =
                assertThrows(
                        IOException.class,
                        () -> CrossPlatformManifestParser.parse(manifestContent.getBytes()));
        assertThat(e)
                .hasMessageThat()
                .isEqualTo("Incomplete cross-platform manifest: missing signature");
    }

    @Test
    public void parse_noPlatformParamsAndNoSignatures_returnsManifest() throws IOException {
        String manifestContent = "1\n" + PACKAGE_NAME + "\n" + PLATFORM + "\n" + "0\n" + "0\n";

        CrossPlatformManifest manifest =
                CrossPlatformManifestParser.parse(manifestContent.getBytes());

        assertThat(manifest).isNotNull();
        assertThat(manifest.getPackageName()).isEqualTo(PACKAGE_NAME);
        assertThat(manifest.getPlatform()).isEqualTo(PLATFORM);
        assertThat(manifest.getPlatformSpecificParams()).isEmpty();
        assertThat(manifest.getSignatureHashes()).isEmpty();
    }

    @Test
    public void parse_multiplePlatformParamsAndSignatures_returnsManifest() throws IOException {
        String manifestContent =
                "1\n"
                        + PACKAGE_NAME
                        + "\n"
                        + PLATFORM
                        + "\n"
                        + "2\n"
                        + BUNDLE_ID
                        + "_1\n"
                        + TEAM_ID
                        + "_1\n"
                        + CONTENT_VERSION
                        + "_1\n"
                        + BUNDLE_ID
                        + "_2\n"
                        + TEAM_ID
                        + "_2\n"
                        + CONTENT_VERSION
                        + "_2\n"
                        + "2\n"
                        + SIGNATURE_HASH_1
                        + "\n"
                        + SIGNATURE_HASH_2
                        + "\n";

        CrossPlatformManifest manifest =
                CrossPlatformManifestParser.parse(manifestContent.getBytes());

        assertThat(manifest).isNotNull();
        assertThat(manifest.getPackageName()).isEqualTo(PACKAGE_NAME);
        assertThat(manifest.getPlatform()).isEqualTo(PLATFORM);

        List<PlatformSpecificParams> platformSpecificParams = manifest.getPlatformSpecificParams();
        assertThat(platformSpecificParams).hasSize(2);
        assertThat(platformSpecificParams.get(0).getBundleId()).isEqualTo(BUNDLE_ID + "_1");
        assertThat(platformSpecificParams.get(0).getTeamId()).isEqualTo(TEAM_ID + "_1");
        assertThat(platformSpecificParams.get(0).getContentVersion())
                .isEqualTo(CONTENT_VERSION + "_1");
        assertThat(platformSpecificParams.get(1).getBundleId()).isEqualTo(BUNDLE_ID + "_2");
        assertThat(platformSpecificParams.get(1).getTeamId()).isEqualTo(TEAM_ID + "_2");
        assertThat(platformSpecificParams.get(1).getContentVersion())
                .isEqualTo(CONTENT_VERSION + "_2");

        List<byte[]> signatureHashes = manifest.getSignatureHashes();
        assertThat(signatureHashes).hasSize(2);
        assertThat(signatureHashes.get(0)).isEqualTo(HexFormat.of().parseHex(SIGNATURE_HASH_1));
        assertThat(signatureHashes.get(1)).isEqualTo(HexFormat.of().parseHex(SIGNATURE_HASH_2));
    }
}
