/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.server.firewall;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import android.content.pm.PackageManager;
import android.content.pm.PackageManagerInternal;
import android.os.UserHandle;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(AndroidJUnit4.class)
public class SenderPackageFilterTest {
    private static final String TEST_PACKAGE_NAME = "com.test.package";
    private static final int TEST_CALLER_UID = 12345;

    @Rule
    public final MockitoRule mMockitoRule = MockitoJUnit.rule();

    @Mock
    private IntentFirewall mIntentFirewall;
    @Mock
    private PackageManagerInternal mPackageManagerInternal;

    @Before
    public void setUp() {
        doReturn(mPackageManagerInternal).when(mIntentFirewall).getPackageManager();
    }

    @Test
    public void testMatches_returnsTrue() {
        SenderPackageFilter filter = new SenderPackageFilter(TEST_PACKAGE_NAME);

        when(mPackageManagerInternal.isSameApp(eq(TEST_PACKAGE_NAME),
                eq((long) PackageManager.MATCH_ANY_USER), eq(TEST_CALLER_UID),
                eq(UserHandle.USER_SYSTEM))).thenReturn(true);

        boolean result = filter.matches(mIntentFirewall, null, null, TEST_CALLER_UID, 0, null, 0);

        assertTrue("Filter should match when isSameApp returns true", result);
    }

    @Test
    public void testMatches_returnsFalse() {
        SenderPackageFilter filter = new SenderPackageFilter(TEST_PACKAGE_NAME);

        when(mPackageManagerInternal.isSameApp(eq(TEST_PACKAGE_NAME),
                eq((long) PackageManager.MATCH_ANY_USER), eq(TEST_CALLER_UID),
                eq(UserHandle.USER_SYSTEM))).thenReturn(false);

        boolean result = filter.matches(mIntentFirewall, null, null, TEST_CALLER_UID, 0, null, 0);

        assertFalse("Filter should not match when isSameApp returns false", result);
    }

}
