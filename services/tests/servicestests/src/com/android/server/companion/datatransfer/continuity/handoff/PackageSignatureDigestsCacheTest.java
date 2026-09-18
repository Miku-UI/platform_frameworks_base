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

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.Signature;
import android.content.pm.SigningDetails;
import android.content.pm.SigningInfo;
import android.platform.test.annotations.Presubmit;
import android.testing.AndroidTestingRunner;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

@Presubmit
@RunWith(AndroidTestingRunner.class)
public class PackageSignatureDigestsCacheTest {
    @Mock
    private PackageManager mockPackageManager;

    private PackageSignatureDigestsCache packageSignatureDigestsCache;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        packageSignatureDigestsCache = new PackageSignatureDigestsCache(mockPackageManager);
    }

    @Test
    public void testGetSigningInfoForPackage_returnsSigningInfo()
            throws NameNotFoundException, NoSuchAlgorithmException {
        String packageName = "testPackage";
        Signature sig = new Signature("1234");
        SigningInfo signingInfo = new SigningInfo(
                new SigningDetails(
                        new Signature[]{sig},
                        SigningDetails.SignatureSchemeVersion.SIGNING_BLOCK_V3,
                        null,
                        null));
        PackageInfo packageInfo = new PackageInfo();
        packageInfo.packageName = packageName;
        packageInfo.signingInfo = signingInfo;
        when(mockPackageManager.getPackageInfo(anyString(), anyInt())).thenReturn(packageInfo);
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] expectedDigests = md.digest(sig.toByteArray());

        byte[][] packageSignatureDigests =
                packageSignatureDigestsCache.getSignatureDigestsForPackage(
                        packageName);
        assertThat(packageSignatureDigests.length).isEqualTo(1);
        assertThat(Arrays.equals(packageSignatureDigests[0],
                expectedDigests)).isTrue();

        // Should hit the cache
        byte[][] packageSignatureDigestsFromCache =
                packageSignatureDigestsCache.getSignatureDigestsForPackage(
                        packageName);
        assertThat(packageSignatureDigestsFromCache.length).isEqualTo(1);
        assertThat(Arrays.equals(packageSignatureDigestsFromCache[0],
                expectedDigests)).isTrue();
    }

    @Test
    public void testGetSigningInfoForPackage_emptyPackageInfo_returnsEmpty()
            throws NameNotFoundException {
        when(mockPackageManager.getPackageInfo(anyString(), anyInt())).thenReturn(null);
        assertThat(packageSignatureDigestsCache.getSignatureDigestsForPackage("testPackage").length
                == 0).isTrue();
    }

    @Test
    public void testGetSigningInfoForPackage_emptySignInfo_returnsEmpty()
            throws NameNotFoundException {
        String packageName = "testPackage";
        PackageInfo packageInfo = new PackageInfo();
        packageInfo.packageName = packageName;
        when(mockPackageManager.getPackageInfo(anyString(), anyInt())).thenReturn(packageInfo);
        assertThat(packageSignatureDigestsCache.getSignatureDigestsForPackage("testPackage").length
                == 0).isTrue();
    }

    @Test
    public void testGetSigningInfoForPackage_exceptionGettingSigningInfo_returnsEmpty()
            throws NameNotFoundException {
        when(mockPackageManager.getPackageInfo(anyString(), anyInt())).thenThrow(
                PackageManager.NameNotFoundException.class);
        assertThat(packageSignatureDigestsCache.getSignatureDigestsForPackage("testPackage").length
                == 0).isTrue();
    }
}
