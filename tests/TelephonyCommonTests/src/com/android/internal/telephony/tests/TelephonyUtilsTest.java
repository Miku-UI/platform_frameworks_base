/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.internal.telephony.tests;

import static android.content.pm.PackageManager.FEATURE_TELEPHONY_MESSAGING;
import static android.content.pm.PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION;
import static android.telephony.NetworkRegistrationInfo.FIRST_SERVICE_TYPE;
import static android.telephony.NetworkRegistrationInfo.LAST_SERVICE_TYPE;
import static android.telephony.TelephonyManager.ENABLE_FEATURE_MAPPING;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.compat.testing.PlatformCompatChangeRule;
import android.content.Context;
import android.content.pm.PackageManager;
import android.telephony.SubscriptionManager;

import com.android.internal.telephony.util.TelephonyUtils;

import libcore.junit.util.compat.CoreCompatChangeRule.DisableCompatChanges;
import libcore.junit.util.compat.CoreCompatChangeRule.EnableCompatChanges;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.List;

public class TelephonyUtilsTest {
    @Rule
    public TestRule compatChangeRule = new PlatformCompatChangeRule();
    @Rule
    public final MockitoRule mockito = MockitoJUnit.rule();

    // Mocked classes
    @Mock
    private Context mContext;
    @Mock
    private SubscriptionManager mSubscriptionManager;
    @Mock
    private PackageManager mPackageManager;

    private static final String FAKE_PACKAGE_NAME = "com.android.internal.telephony.tests";
    private static final String FAKE_METHOD_NAME = "someMethod";

    @Before
    public void setup() {
        doReturn(mSubscriptionManager).when(mContext)
                .getSystemService(eq(SubscriptionManager.class));
        doReturn(mPackageManager).when(mContext).getPackageManager();
    }

    @Test
    public void getSubscriptionUserHandle_subId_invalid() {
        int invalidSubId = -10;
        doReturn(false).when(mSubscriptionManager).isActiveSubscriptionId(eq(invalidSubId));

        TelephonyUtils.getSubscriptionUserHandle(mContext, invalidSubId);

        // getSubscriptionUserHandle should not be called if subID is inactive.
        verify(mSubscriptionManager, never()).getSubscriptionUserHandle(eq(invalidSubId));
    }

    @Test
    public void getSubscriptionUserHandle_subId_valid() {
        int activeSubId = 1;
        doReturn(true).when(mSubscriptionManager).isActiveSubscriptionId(eq(activeSubId));

        TelephonyUtils.getSubscriptionUserHandle(mContext, activeSubId);

        // getSubscriptionUserHandle should be called if subID is active.
        verify(mSubscriptionManager, times(1)).getSubscriptionUserHandle(eq(activeSubId));
    }

    @Test
    public void testIsValidPlmn() {
        assertTrue(TelephonyUtils.isValidPlmn("310260"));
        assertTrue(TelephonyUtils.isValidPlmn("45006"));
        assertFalse(TelephonyUtils.isValidPlmn("1234567"));
        assertFalse(TelephonyUtils.isValidPlmn("1234"));
        assertFalse(TelephonyUtils.isValidPlmn(""));
        assertFalse(TelephonyUtils.isValidPlmn(null));
    }

    @Test
    public void testIsValidService() {
        assertTrue(TelephonyUtils.isValidService(FIRST_SERVICE_TYPE));
        assertTrue(TelephonyUtils.isValidService(LAST_SERVICE_TYPE));
        assertFalse(TelephonyUtils.isValidService(FIRST_SERVICE_TYPE - 1));
        assertFalse(TelephonyUtils.isValidService(LAST_SERVICE_TYPE + 1));
    }

    @Test
    public void testIsValidCountryCode() {
        assertTrue(TelephonyUtils.isValidCountryCode("US"));
        assertTrue(TelephonyUtils.isValidCountryCode("cn"));
        assertFalse(TelephonyUtils.isValidCountryCode("11"));
        assertFalse(TelephonyUtils.isValidCountryCode("USA"));
        assertFalse(TelephonyUtils.isValidCountryCode("chn"));
        assertFalse(TelephonyUtils.isValidCountryCode("U"));
        assertFalse(TelephonyUtils.isValidCountryCode("G7"));
        assertFalse(TelephonyUtils.isValidCountryCode(""));
        assertFalse(TelephonyUtils.isValidCountryCode(null));
    }

    @Test
    public void enforceTelephonyFeature_skipWhenPackageIsNull() {
        // No exception thrown
        TelephonyUtils.enforceTelephonyFeatureWithException(
                null, mPackageManager,
                TelephonyUtils.TELEPHONY_FEATURE_ENFORCEMENT_VENDOR_API_LEVEL,
                FEATURE_TELEPHONY_SUBSCRIPTION, FAKE_METHOD_NAME);

        // No exception thrown
        List<String> features = List.of(FEATURE_TELEPHONY_SUBSCRIPTION,
                FEATURE_TELEPHONY_MESSAGING);
        TelephonyUtils.enforceTelephonyFeatureWithException(
                null, mPackageManager,
                TelephonyUtils.TELEPHONY_FEATURE_ENFORCEMENT_VENDOR_API_LEVEL,
                features, FAKE_METHOD_NAME);

        // hasSystemFeature should not be checked if enforcement is skipped.
        verify(mPackageManager, never()).hasSystemFeature(anyString());
    }

    @Test
    public void enforceTelephonyFeature_skipWhenPackageManagerIsNull() {
        // No exception thrown
        TelephonyUtils.enforceTelephonyFeatureWithException(
                FAKE_PACKAGE_NAME, null,
                TelephonyUtils.TELEPHONY_FEATURE_ENFORCEMENT_VENDOR_API_LEVEL,
                FEATURE_TELEPHONY_SUBSCRIPTION, FAKE_METHOD_NAME);

        // No exception thrown
        List<String> features = List.of(FEATURE_TELEPHONY_SUBSCRIPTION,
                FEATURE_TELEPHONY_MESSAGING);
        TelephonyUtils.enforceTelephonyFeatureWithException(
                FAKE_PACKAGE_NAME, null,
                TelephonyUtils.TELEPHONY_FEATURE_ENFORCEMENT_VENDOR_API_LEVEL,
                features, FAKE_METHOD_NAME);

        // hasSystemFeature should not be checked if enforcement is skipped.
        verify(mPackageManager, never()).hasSystemFeature(anyString());
    }

    @Test
    @EnableCompatChanges({ENABLE_FEATURE_MAPPING})
    public void enforceTelephonyFeature_skipWhenVendorApiLevelIsLow() {
        // Just a bit lower than when the feature enforcement was introduced.
        int vendorApiLevel = TelephonyUtils.TELEPHONY_FEATURE_ENFORCEMENT_VENDOR_API_LEVEL - 1;

        // No exception thrown
        TelephonyUtils.enforceTelephonyFeatureWithException(
                FAKE_PACKAGE_NAME, mPackageManager,
                vendorApiLevel,
                FEATURE_TELEPHONY_SUBSCRIPTION, FAKE_METHOD_NAME);

        // No exception thrown
        List<String> features = List.of(FEATURE_TELEPHONY_SUBSCRIPTION,
                FEATURE_TELEPHONY_MESSAGING);
        TelephonyUtils.enforceTelephonyFeatureWithException(
                FAKE_PACKAGE_NAME, mPackageManager,
                vendorApiLevel,
                features, FAKE_METHOD_NAME);

        // hasSystemFeature should not be checked if enforcement is skipped.
        verify(mPackageManager, never()).hasSystemFeature(anyString());
    }

    @Test
    @DisableCompatChanges({ENABLE_FEATURE_MAPPING})
    public void enforceTelephonyFeature_skipWhenCompatChangeIsDisabled() {
        // No exception thrown
        TelephonyUtils.enforceTelephonyFeatureWithException(
                FAKE_PACKAGE_NAME, mPackageManager,
                TelephonyUtils.TELEPHONY_FEATURE_ENFORCEMENT_VENDOR_API_LEVEL,
                FEATURE_TELEPHONY_SUBSCRIPTION, FAKE_METHOD_NAME);

        // No exception thrown
        List<String> features = List.of(FEATURE_TELEPHONY_SUBSCRIPTION,
                FEATURE_TELEPHONY_MESSAGING);
        TelephonyUtils.enforceTelephonyFeatureWithException(
                FAKE_PACKAGE_NAME, mPackageManager,
                TelephonyUtils.TELEPHONY_FEATURE_ENFORCEMENT_VENDOR_API_LEVEL,
                features, FAKE_METHOD_NAME);

        verify(mPackageManager, never()).hasSystemFeature(anyString());
    }

    @Test
    @EnableCompatChanges({ENABLE_FEATURE_MAPPING})
    public void enforceTelephonyFeature_enforcedAndFeaturePresent() {
        doReturn(true).when(mPackageManager).hasSystemFeature(eq(FEATURE_TELEPHONY_SUBSCRIPTION));

        // No exception thrown
        TelephonyUtils.enforceTelephonyFeatureWithException(
                FAKE_PACKAGE_NAME, mPackageManager,
                TelephonyUtils.TELEPHONY_FEATURE_ENFORCEMENT_VENDOR_API_LEVEL,
                FEATURE_TELEPHONY_SUBSCRIPTION, FAKE_METHOD_NAME);

        verify(mPackageManager, times(1)).hasSystemFeature(eq(FEATURE_TELEPHONY_SUBSCRIPTION));
    }

    @Test
    @EnableCompatChanges({ENABLE_FEATURE_MAPPING})
    public void enforceTelephonyFeature_enforcedAndFeatureMissing_throwsException() {
        doReturn(false).when(mPackageManager).hasSystemFeature(eq(FEATURE_TELEPHONY_SUBSCRIPTION));

        assertThrows(UnsupportedOperationException.class,
                () -> TelephonyUtils.enforceTelephonyFeatureWithException(
                        FAKE_PACKAGE_NAME, mPackageManager,
                        TelephonyUtils.TELEPHONY_FEATURE_ENFORCEMENT_VENDOR_API_LEVEL,
                        FEATURE_TELEPHONY_SUBSCRIPTION, FAKE_METHOD_NAME));
    }

    @Test
    @EnableCompatChanges({ENABLE_FEATURE_MAPPING})
    public void enforceTelephonyFeatureList_enforcedAndFirstFeaturePresent() {
        List<String> features = List.of(FEATURE_TELEPHONY_SUBSCRIPTION,
                FEATURE_TELEPHONY_MESSAGING);
        doReturn(true).when(mPackageManager).hasSystemFeature(eq(FEATURE_TELEPHONY_SUBSCRIPTION));
        doReturn(false).when(mPackageManager).hasSystemFeature(eq(FEATURE_TELEPHONY_MESSAGING));

        // No exception thrown
        TelephonyUtils.enforceTelephonyFeatureWithException(
                FAKE_PACKAGE_NAME, mPackageManager,
                TelephonyUtils.TELEPHONY_FEATURE_ENFORCEMENT_VENDOR_API_LEVEL,
                features, FAKE_METHOD_NAME);

        verify(mPackageManager, times(1)).hasSystemFeature(eq(FEATURE_TELEPHONY_SUBSCRIPTION));
        verify(mPackageManager, never()).hasSystemFeature(eq(FEATURE_TELEPHONY_MESSAGING));
    }

    @Test
    @EnableCompatChanges({ENABLE_FEATURE_MAPPING})
    public void enforceTelephonyFeatureList_enforcedAndSecondFeaturePresent() {
        List<String> features = List.of(FEATURE_TELEPHONY_SUBSCRIPTION,
                FEATURE_TELEPHONY_MESSAGING);
        doReturn(false).when(mPackageManager).hasSystemFeature(eq(FEATURE_TELEPHONY_SUBSCRIPTION));
        doReturn(true).when(mPackageManager).hasSystemFeature(eq(FEATURE_TELEPHONY_MESSAGING));

        // No exception thrown
        TelephonyUtils.enforceTelephonyFeatureWithException(
                FAKE_PACKAGE_NAME, mPackageManager,
                TelephonyUtils.TELEPHONY_FEATURE_ENFORCEMENT_VENDOR_API_LEVEL,
                features, FAKE_METHOD_NAME);

        verify(mPackageManager, times(1)).hasSystemFeature(eq(FEATURE_TELEPHONY_SUBSCRIPTION));
        verify(mPackageManager, times(1)).hasSystemFeature(eq(FEATURE_TELEPHONY_MESSAGING));
    }

    @Test
    @EnableCompatChanges({ENABLE_FEATURE_MAPPING})
    public void enforceTelephonyFeatureList_enforcedAndAllFeaturesMissing_throwsException() {
        List<String> features = List.of(FEATURE_TELEPHONY_SUBSCRIPTION,
                FEATURE_TELEPHONY_MESSAGING);
        doReturn(false).when(mPackageManager).hasSystemFeature(eq(FEATURE_TELEPHONY_SUBSCRIPTION));
        doReturn(false).when(mPackageManager).hasSystemFeature(eq(FEATURE_TELEPHONY_MESSAGING));

        assertThrows(UnsupportedOperationException.class,
                () -> TelephonyUtils.enforceTelephonyFeatureWithException(
                        FAKE_PACKAGE_NAME, mPackageManager,
                        TelephonyUtils.TELEPHONY_FEATURE_ENFORCEMENT_VENDOR_API_LEVEL,
                        features, FAKE_METHOD_NAME));
    }
}
