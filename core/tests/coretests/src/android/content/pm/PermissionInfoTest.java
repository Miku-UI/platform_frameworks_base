/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.content.pm;

import static android.content.pm.PermissionInfo.NO_TARGET_SDK_VERSION;
import static android.os.Build.VERSION.SDK_INT;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.os.Parcel;
import android.platform.test.annotations.Presubmit;
import android.util.ArraySet;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.internal.util.CollectionUtils;

import org.junit.Test;
import org.junit.runner.RunWith;

@Presubmit
@RunWith(AndroidJUnit4.class)
public final class PermissionInfoTest {
    private static final String KNOWN_CERT_DIGEST_1 =
            "6a8b96e278e58f62cfe3584022cec1d0527fcb85a9e5d2e1694eb0405be5b599";
    private static final String KNOWN_CERT_DIGEST_2 =
            "9369370ffcfdc1e92dae777252c05c483b8cbb55fa9d5fd9f6317f623ae6d8c6";
    private static final String PURPOSE_1 = "purpose1";
    private static final String PURPOSE_2 = "purpose2";
    private static final int TEST_TARGET_SDK_VERSION = 37;

    @Test
    public void createFromParcel_returnsKnownCerts() {
        // The platform supports a knownSigner permission protection flag that allows one or more
        // trusted signing certificates to be specified with the permission declaration; if a
        // requesting app is signed by any of these trusted certificates the permission is granted.
        // This test verifies the Set of knownCerts is properly parceled / unparceled.
        PermissionInfo permissionInfo = new PermissionInfo();
        permissionInfo.protectionLevel =
                PermissionInfo.PROTECTION_SIGNATURE | PermissionInfo.PROTECTION_FLAG_KNOWN_SIGNER;
        permissionInfo.knownCerts = new ArraySet<>(2);
        permissionInfo.knownCerts.add(KNOWN_CERT_DIGEST_1);
        permissionInfo.knownCerts.add(KNOWN_CERT_DIGEST_2);
        Parcel parcel = Parcel.obtain();
        permissionInfo.writeToParcel(parcel, 0);

        parcel.setDataPosition(0);
        PermissionInfo unparceledPermissionInfo = PermissionInfo.CREATOR.createFromParcel(parcel);

        assertNotNull(unparceledPermissionInfo.knownCerts);
        assertEquals(2, unparceledPermissionInfo.knownCerts.size());
        assertTrue(unparceledPermissionInfo.knownCerts.contains(KNOWN_CERT_DIGEST_1));
        assertTrue(unparceledPermissionInfo.knownCerts.contains(KNOWN_CERT_DIGEST_2));
    }

    @Test
    public void createFromParcel_withDefaultPermissionInfo_returnsEmptyValidPurposes() {
        PermissionInfo permissionInfo = new PermissionInfo();
        Parcel parcel = Parcel.obtain();
        permissionInfo.writeToParcel(parcel, 0);

        parcel.setDataPosition(0);
        PermissionInfo unparceledPermissionInfo = PermissionInfo.CREATOR.createFromParcel(parcel);

        assertEquals(
                NO_TARGET_SDK_VERSION,
                unparceledPermissionInfo.requiresPurposeTargetSdkVersion);
        assertEquals(
                NO_TARGET_SDK_VERSION,
                unparceledPermissionInfo.requiresGeneralPurposeTargetSdkVersion);
        assertEquals(
                NO_TARGET_SDK_VERSION,
                unparceledPermissionInfo.requiresPurposeStringTargetSdkVersion);
        assertNotNull(unparceledPermissionInfo.validPurposes);
        assertNotNull(unparceledPermissionInfo.validGeneralPurposes);
        assertTrue(unparceledPermissionInfo.validPurposes.isEmpty());
        assertTrue(unparceledPermissionInfo.validGeneralPurposes.isEmpty());
    }

    @Test
    public void createFromParcel_withCustomPermissionInfo_returnsValidPurposes() {
        PermissionInfo permissionInfo = new PermissionInfo();
        permissionInfo.requiresPurposeTargetSdkVersion = SDK_INT;
        permissionInfo.requiresGeneralPurposeTargetSdkVersion = SDK_INT;
        permissionInfo.requiresPurposeStringTargetSdkVersion = SDK_INT;
        permissionInfo.validPurposes =
                CollectionUtils.add(
                        permissionInfo.validPurposes,
                        PURPOSE_1,
                        new ValidPurposeInfo(PURPOSE_1, Integer.MAX_VALUE));
        permissionInfo.validPurposes =
                CollectionUtils.add(
                        permissionInfo.validPurposes,
                        PURPOSE_2,
                        new ValidPurposeInfo(PURPOSE_2, 10));
        permissionInfo.validGeneralPurposes =
                CollectionUtils.add(
                        permissionInfo.validGeneralPurposes,
                        PURPOSE_1,
                        new ValidGeneralPurposeInfo(PURPOSE_1, Integer.MAX_VALUE));
        permissionInfo.validGeneralPurposes =
                CollectionUtils.add(
                        permissionInfo.validGeneralPurposes,
                        PURPOSE_2,
                        new ValidGeneralPurposeInfo(PURPOSE_2, 10));

        Parcel parcel = Parcel.obtain();
        permissionInfo.writeToParcel(parcel, 0);

        parcel.setDataPosition(0);
        PermissionInfo unparceledPermissionInfo = PermissionInfo.CREATOR.createFromParcel(parcel);

        assertEquals(SDK_INT, unparceledPermissionInfo.requiresPurposeTargetSdkVersion);
        assertEquals(SDK_INT, unparceledPermissionInfo.requiresGeneralPurposeTargetSdkVersion);
        assertEquals(SDK_INT, unparceledPermissionInfo.requiresPurposeStringTargetSdkVersion);
        assertNotNull(unparceledPermissionInfo.validPurposes);
        assertEquals(2, unparceledPermissionInfo.validPurposes.size());
        assertNotNull(unparceledPermissionInfo.validGeneralPurposes);
        assertEquals(2, unparceledPermissionInfo.validGeneralPurposes.size());

        assertTrue(unparceledPermissionInfo.validPurposes.containsKey(PURPOSE_1));
        assertEquals(PURPOSE_1, unparceledPermissionInfo.validPurposes.get(PURPOSE_1).getName());
        assertEquals(
                Integer.MAX_VALUE,
                unparceledPermissionInfo.validPurposes.get(PURPOSE_1).getMaxTargetSdkVersion());

        assertTrue(unparceledPermissionInfo.validPurposes.containsKey(PURPOSE_2));
        assertEquals(PURPOSE_2, unparceledPermissionInfo.validPurposes.get(PURPOSE_2).getName());
        assertEquals(
                10, unparceledPermissionInfo.validPurposes.get(PURPOSE_2).getMaxTargetSdkVersion());

        assertTrue(unparceledPermissionInfo.validGeneralPurposes.containsKey(PURPOSE_1));
        assertEquals(
                PURPOSE_1, unparceledPermissionInfo.validGeneralPurposes.get(PURPOSE_1).getName());
        assertEquals(
                Integer.MAX_VALUE,
                unparceledPermissionInfo
                        .validGeneralPurposes
                        .get(PURPOSE_1)
                        .getMaxTargetSdkVersion());

        assertTrue(unparceledPermissionInfo.validGeneralPurposes.containsKey(PURPOSE_2));
        assertEquals(
                PURPOSE_2, unparceledPermissionInfo.validGeneralPurposes.get(PURPOSE_2).getName());
        assertEquals(
                10,
                unparceledPermissionInfo
                        .validGeneralPurposes
                        .get(PURPOSE_2)
                        .getMaxTargetSdkVersion());
    }

    @Test
    public void createFromParcel_withDefaultPurposeTargetSdkVersions_returnsNoTargetSdkVersion() {
        PermissionInfo permissionInfo = new PermissionInfo();
        Parcel parcel = Parcel.obtain();
        permissionInfo.writeToParcel(parcel, 0);

        parcel.setDataPosition(0);
        PermissionInfo unparceledPermissionInfo = PermissionInfo.CREATOR.createFromParcel(parcel);

        assertEquals(
                NO_TARGET_SDK_VERSION,
                unparceledPermissionInfo.requiresGeneralPurposeTargetSdkVersion);
    }

    @Test
    public void createFromParcel_returnsPurposeRelatedFields() {
        PermissionInfo permissionInfo = new PermissionInfo();
        permissionInfo.requiresGeneralPurposeTargetSdkVersion = TEST_TARGET_SDK_VERSION;
        permissionInfo.requiresPurposeStringTargetSdkVersion = TEST_TARGET_SDK_VERSION;
        Parcel parcel = Parcel.obtain();
        permissionInfo.writeToParcel(parcel, 0);

        parcel.setDataPosition(0);
        PermissionInfo unparceledPermissionInfo = PermissionInfo.CREATOR.createFromParcel(parcel);

        assertEquals(
                TEST_TARGET_SDK_VERSION,
                unparceledPermissionInfo.requiresGeneralPurposeTargetSdkVersion);
        assertEquals(
                TEST_TARGET_SDK_VERSION,
                unparceledPermissionInfo.requiresPurposeStringTargetSdkVersion);
    }

    @Test
    public void isAllowedInPrivateComputeCore_whenFlagIsSet_returnsTrue() {
        PermissionInfo permissionInfo = new PermissionInfo();

        // Case 1: Flag is set by itself.
        permissionInfo.flags = PermissionInfo.FLAG_ALLOWED_IN_PRIVATE_COMPUTE_CORE;
        assertTrue("Should be true when the flag is set",
                permissionInfo.isAllowedInPrivateComputeCore());

        // Case 2: Flag is set along with other flags.
        permissionInfo.flags |= PermissionInfo.FLAG_COSTS_MONEY | PermissionInfo.FLAG_INSTALLED;
        assertTrue("Should be true when the flag is set with others",
                permissionInfo.isAllowedInPrivateComputeCore());
    }

    @Test
    public void isAllowedInPrivateComputeCore_whenFlagIsNotSet_returnsFalse() {
        PermissionInfo permissionInfo = new PermissionInfo();

        // Case 1: Flag is not set.
        permissionInfo.flags = 0;
        assertFalse("Should be false when no flags are set",
                permissionInfo.isAllowedInPrivateComputeCore());

        // Case 2: Other flags are set, but not the target flag.
        permissionInfo.flags = PermissionInfo.FLAG_COSTS_MONEY | PermissionInfo.FLAG_INSTALLED;
        assertFalse("Should be false when other flags are set",
                permissionInfo.isAllowedInPrivateComputeCore());
    }

    @Test
    public void flagsToString_withAllowedInPrivateComputeCore_containsCorrectString() {
        // Verifies that flagsToString includes "allowedInPrivateComputeCore" when the flag is set.
        int flags = PermissionInfo.FLAG_ALLOWED_IN_PRIVATE_COMPUTE_CORE;
        String result1 = PermissionInfo.flagsToString(flags);
        assertTrue("String representation should contain 'allowedInPrivateComputeCore'",
                result1.contains("allowedInPrivateComputeCore"));

        // Verify with multiple flags.
        flags |= PermissionInfo.FLAG_COSTS_MONEY;
        String result2 = PermissionInfo.flagsToString(flags);
        assertTrue("String representation should contain 'allowedInPrivateComputeCore'",
                result2.contains("allowedInPrivateComputeCore"));
        assertTrue("String representation should contain 'costsMoney'",
                result2.contains("costsMoney"));
    }

    @Test
    public void parceling_whenAllowedInPrivateComputeCoreFlagSet_preservesFlag() {
        // Verifies that FLAG_ALLOWED_IN_PRIVATE_COMPUTE_CORE is preserved after parceling.
        PermissionInfo originalInfo = new PermissionInfo();
        originalInfo.flags = PermissionInfo.FLAG_ALLOWED_IN_PRIVATE_COMPUTE_CORE
                | PermissionInfo.FLAG_COSTS_MONEY;

        PermissionInfo unparceledInfo = parcelAndUnparcel(originalInfo);

        assertTrue(unparceledInfo.isAllowedInPrivateComputeCore());
        assertEquals(originalInfo.flags, unparceledInfo.flags);
    }

    @Test
    public void parceling_whenAllowedInPrivateComputeCoreFlagNotSet_preservesFlag() {
        // Verifies that the absence of FLAG_ALLOWED_IN_PRIVATE_COMPUTE_CORE is preserved.
        PermissionInfo originalInfo = new PermissionInfo();
        originalInfo.flags = PermissionInfo.FLAG_COSTS_MONEY;

        PermissionInfo unparceledInfo = parcelAndUnparcel(originalInfo);

        assertFalse(unparceledInfo.isAllowedInPrivateComputeCore());
        assertEquals(originalInfo.flags, unparceledInfo.flags);
    }

    private PermissionInfo parcelAndUnparcel(PermissionInfo originalInfo) {
        Parcel parcel = Parcel.obtain();
        try {
            originalInfo.writeToParcel(parcel, 0);
            parcel.setDataPosition(0);
            return PermissionInfo.CREATOR.createFromParcel(parcel);
        } finally {
            parcel.recycle();
        }
    }
}
