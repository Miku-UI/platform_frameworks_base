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
package com.android.server.security.authenticationpolicy.settings;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.admin.DevicePolicyManager;
import android.os.Bundle;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.Set;

public class DevicePolicyRestrictionsControllerTest {
    @Rule public MockitoRule mockito = MockitoJUnit.rule();

    @Mock private DevicePolicyManager mDevicePolicyManager;
    @Mock private SettingState<Set<String>> mSettingState;

    private static final int TEST_USER_ID = 0;

    private static final Set<String> DEVICE_POLICY_RESTRICTIONS = Set.of(
            "test-restriction-1", "test-restriction-2", "test-restriction-3"
    );
    private DevicePolicyRestrictionsController mDevicePolicyRestrictionsController;

    @Before
    public void setUp() throws Exception {
        mDevicePolicyRestrictionsController = new DevicePolicyRestrictionsController();
        mDevicePolicyRestrictionsController.setDevicePolicyManager(mDevicePolicyManager);
    }

    @Test
    public void testStoreOriginalValue_skipsIfDevicePolicyManagerIsNull() throws Exception {
        mDevicePolicyRestrictionsController.setDevicePolicyManager(null);

        mDevicePolicyRestrictionsController.storeOriginalValue(mSettingState, TEST_USER_ID);

        verify(mSettingState, never()).setOriginalValue(any());
    }

    @Test
    public void testApplySecureLockDeviceValue_skipsIfDevicePolicyManagerIsNull() throws Exception {
        mDevicePolicyRestrictionsController.setDevicePolicyManager(null);
        when(mSettingState.getSecureLockDeviceValue()).thenReturn(DEVICE_POLICY_RESTRICTIONS);

        mDevicePolicyRestrictionsController.applySecureLockDeviceValue(mSettingState, TEST_USER_ID);

        verify(mDevicePolicyManager, never()).addUserRestrictionGlobally(anyString(), anyString());
    }

    @Test
    public void testApplySecureLockDeviceValue_successfullyAppliesUserRestriction()
            throws Exception {
        when(mSettingState.getSecureLockDeviceValue()).thenReturn(DEVICE_POLICY_RESTRICTIONS);

        mDevicePolicyRestrictionsController.applySecureLockDeviceValue(mSettingState, TEST_USER_ID);

        for (String restriction: DEVICE_POLICY_RESTRICTIONS) {
            verify(mDevicePolicyManager).addUserRestrictionGlobally(anyString(), eq(restriction));
        }
    }

    @Test
    public void testRestoreFromOriginalValue_skipsIfDevicePolicyManagerIsNull() throws Exception {
        mDevicePolicyRestrictionsController.setDevicePolicyManager(null);
        when(mSettingState.getOriginalValue()).thenReturn(DEVICE_POLICY_RESTRICTIONS);
        when(mSettingState.getSecureLockDeviceValue()).thenReturn(DEVICE_POLICY_RESTRICTIONS);

        mDevicePolicyRestrictionsController.restoreFromOriginalValue(mSettingState, TEST_USER_ID);

        verify(mDevicePolicyManager, never()).clearUserRestrictionGlobally(anyString(),
                anyString());
    }

    @Test
    public void testRestoreFromOriginalValue_skipsIfOriginalValueIsNull() throws Exception {
        when(mDevicePolicyManager.getUserRestrictionsGlobally()).thenReturn(new Bundle());
        when(mSettingState.getOriginalValue()).thenReturn(null);
        when(mSettingState.getSecureLockDeviceValue()).thenReturn(DEVICE_POLICY_RESTRICTIONS);

        mDevicePolicyRestrictionsController.restoreFromOriginalValue(mSettingState, TEST_USER_ID);

        verify(mDevicePolicyManager, never()).clearUserRestrictionGlobally(anyString(),
                anyString());
    }

    @Test
    public void testRestoreFromOriginalValue_successfullyClearsUserRestriction()
            throws Exception {
        when(mSettingState.getOriginalValue()).thenReturn(Set.of()); //empty set
        when(mSettingState.getSecureLockDeviceValue()).thenReturn(DEVICE_POLICY_RESTRICTIONS);

        mDevicePolicyRestrictionsController.restoreFromOriginalValue(mSettingState, TEST_USER_ID);
        for (String restriction: DEVICE_POLICY_RESTRICTIONS) {
            verify(mDevicePolicyManager).clearUserRestrictionGlobally(
                    anyString(), eq(restriction));
        }
    }

    @Test
    public void testRestoreFromOriginalValue_doesNotClearIfOriginallyPresent() throws Exception {
        when(mSettingState.getOriginalValue()).thenReturn(DEVICE_POLICY_RESTRICTIONS);
        when(mSettingState.getSecureLockDeviceValue()).thenReturn(DEVICE_POLICY_RESTRICTIONS);

        mDevicePolicyRestrictionsController.restoreFromOriginalValue(mSettingState, TEST_USER_ID);

        verify(mDevicePolicyManager, never()).clearUserRestrictionGlobally(anyString(),
                anyString());
    }
}
