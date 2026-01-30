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

import android.app.backup.FullBackup.BackupScheme.PlatformSpecificParams;
import android.content.pm.PackageInfo;
import android.content.pm.Signature;
import android.content.pm.SigningDetails;
import android.content.pm.SigningInfo;

import com.android.server.backup.BackupUtils;
import com.android.server.backup.fullbackup.ShadowSigningInfo;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(
        shadows = {
            ShadowSigningInfo.class,
        })
public class CrossPlatformManifestTest {

    private static final String PACKAGE_NAME = "com.example.app";
    private static final String PLATFORM = "ios";
    private static final Signature[] SIGNATURES = new Signature[] {new Signature("1234")};
    private static final String BUNDLE_ID = "com.example.bundleid";
    private static final String TEAM_ID = "A1B2C3D4";
    private static final String CONTENT_VERSION = "1.0";

    private PackageInfo mPackageInfo;
    private List<PlatformSpecificParams> mPlatformSpecificParams;

    @Before
    public void setUp() {
        mPlatformSpecificParams = new ArrayList<>();
        mPlatformSpecificParams.add(
                new PlatformSpecificParams(BUNDLE_ID, TEAM_ID, CONTENT_VERSION));
    }

    @Test
    public void create_returnsManifestWithSignatures() {
        mPackageInfo = createPackageInfo(PACKAGE_NAME, SIGNATURES);

        CrossPlatformManifest manifest =
                CrossPlatformManifest.create(mPackageInfo, PLATFORM, mPlatformSpecificParams);

        assertThat(manifest.getPackageName()).isEqualTo(PACKAGE_NAME);
        assertThat(manifest.getPlatform()).isEqualTo(PLATFORM);
        assertThat(manifest.getPlatformSpecificParams())
                .containsExactly(new PlatformSpecificParams(BUNDLE_ID, TEAM_ID, CONTENT_VERSION));
        List<byte[]> signatureHashes = manifest.getSignatureHashes();
        assertThat(signatureHashes).hasSize(1);
        assertThat(signatureHashes.getFirst()).isEqualTo(BackupUtils.hashSignature(SIGNATURES[0]));
    }

    @Test
    public void toByteArray_returnsSerializedManifest() throws Exception {
        mPackageInfo = createPackageInfo(PACKAGE_NAME, SIGNATURES);
        CrossPlatformManifest manifest =
                CrossPlatformManifest.create(mPackageInfo, PLATFORM, mPlatformSpecificParams);

        byte[] manifestBytes = manifest.toByteArray();

        String signatureHash = HexFormat.of().formatHex(BackupUtils.hashSignature(SIGNATURES[0]));
        String expectedManifestBytes =
                "1\n"
                        + "com.example.app\n"
                        + "ios\n"
                        + "1\n"
                        + "com.example.bundleid\n"
                        + "A1B2C3D4\n"
                        + "1.0\n"
                        + "1\n"
                        + signatureHash
                        + "\n";
        assertThat(new String(manifestBytes)).isEqualTo(expectedManifestBytes);
    }

    @Test
    public void toByteArray_emptySignatures_returnsSerializedManifest() throws Exception {
        mPackageInfo = createPackageInfo(PACKAGE_NAME, new Signature[] {});
        CrossPlatformManifest manifest =
                CrossPlatformManifest.create(mPackageInfo, PLATFORM, mPlatformSpecificParams);

        byte[] manifestBytes = manifest.toByteArray();

        String expectedManifestBytes =
                "1\n"
                        + "com.example.app\n"
                        + "ios\n"
                        + "1\n"
                        + "com.example.bundleid\n"
                        + "A1B2C3D4\n"
                        + "1.0\n"
                        + "0\n";
        assertThat(new String(manifestBytes)).isEqualTo(expectedManifestBytes);
    }

    private static PackageInfo createPackageInfo(String packageName, Signature[] signatures) {
        PackageInfo packageInfo = new PackageInfo();
        packageInfo.packageName = PACKAGE_NAME;
        packageInfo.signingInfo =
                new SigningInfo(
                        new SigningDetails(
                                signatures,
                                SigningDetails.SignatureSchemeVersion.SIGNING_BLOCK_V3,
                                /* keys= */ null,
                                /* pastSigningCertificates= */ null));
        return packageInfo;
    }
}
