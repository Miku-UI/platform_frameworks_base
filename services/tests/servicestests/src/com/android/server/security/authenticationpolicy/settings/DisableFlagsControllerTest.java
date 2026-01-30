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

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.util.Pair;

import com.android.internal.statusbar.IStatusBarService;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class DisableFlagsControllerTest {
    @Rule public MockitoRule mockito = MockitoJUnit.rule();

    @Mock private IStatusBarService mStatusBarService;
    @Mock private SettingState<Pair<Integer, Integer>> mSettingState;

    private static final String TEST_PACKAGE_NAME = "com.example.test";
    private static final int TEST_USER_ID = 0;
    private static final int DISABLE_FLAGS = 1;
    private static final int DISABLE2_FLAGS = 2;
    private static final Pair<Integer, Integer> TEST_SECURE_LOCK_DEVICE_VALUE =
            new Pair<>(DISABLE_FLAGS, DISABLE2_FLAGS);

    private DisableFlagsController mDisableFlagsController;

    @Before
    public void setUp() throws Exception {
        mDisableFlagsController = new DisableFlagsController(TEST_PACKAGE_NAME, mStatusBarService);
    }

    @Test
    public void testStoreOriginalValue_successfullyStoresOriginalValue() throws Exception {
        int[] disableFlags = {DISABLE_FLAGS, DISABLE2_FLAGS};
        when(mStatusBarService.getDisableFlags(any(), eq(TEST_USER_ID))).thenReturn(disableFlags);

        mDisableFlagsController.storeOriginalValue(mSettingState, TEST_USER_ID);

        verify(mSettingState).setOriginalValue(new Pair<>(DISABLE_FLAGS,
                DISABLE2_FLAGS));
    }

    @Test
    public void testStoreOriginalValue_skipsIfStatusBarServiceIsNull() {
        DisableFlagsController controller = new DisableFlagsController(TEST_PACKAGE_NAME, null);

        try {
            controller.storeOriginalValue(mSettingState, TEST_USER_ID);
        } catch (Exception e) {
            assertThat(e.getMessage()).isEqualTo("IStatusBarService is null, cannot retrieve "
                    + "status bar state.");
        }

        verify(mSettingState, never()).setOriginalValue(any());
    }


    @Test
    public void testApplySecureLockDeviceValue_successfullyAppliesSecureLockDeviceValue()
            throws Exception {
        when(mSettingState.getSecureLockDeviceValue()).thenReturn(TEST_SECURE_LOCK_DEVICE_VALUE);

        mDisableFlagsController.applySecureLockDeviceValue(mSettingState, TEST_USER_ID);

        verify(mStatusBarService).disable(eq(DISABLE_FLAGS), any(),
                eq(TEST_PACKAGE_NAME));
        verify(mStatusBarService).disable2(eq(DISABLE2_FLAGS), any(),
                eq(TEST_PACKAGE_NAME));
    }

    @Test
    public void testApplySecureLockDeviceValue_skipsIfStatusBarServiceIsNull() throws Exception {
        DisableFlagsController controller = new DisableFlagsController(TEST_PACKAGE_NAME, null);
        when(mSettingState.getSecureLockDeviceValue()).thenReturn(TEST_SECURE_LOCK_DEVICE_VALUE);

        controller.applySecureLockDeviceValue(mSettingState, TEST_USER_ID);

        verify(mStatusBarService, never()).disable(anyInt(), any(), anyString());
        verify(mStatusBarService, never()).disable2(anyInt(), any(), anyString());
    }

    @Test
    public void testRestoreFromOriginalValue_restoresOriginalValueWhenAvailable() throws Exception {
        when(mSettingState.getOriginalValue()).thenReturn(new Pair<>(DISABLE_FLAGS,
                DISABLE2_FLAGS));

        mDisableFlagsController.restoreFromOriginalValue(mSettingState, TEST_USER_ID);

        verify(mStatusBarService).disable(eq(DISABLE_FLAGS), any(), eq(TEST_PACKAGE_NAME));
        verify(mStatusBarService).disable2(eq(DISABLE2_FLAGS), any(), eq(TEST_PACKAGE_NAME));
    }

    @Test
    public void testRestoreFromOriginalValue_skipsIfStatusBarServiceIsNull() throws Exception {
        DisableFlagsController controller = new DisableFlagsController(TEST_PACKAGE_NAME, null);
        when(mSettingState.getOriginalValue()).thenReturn(new Pair<>(DISABLE_FLAGS,
                DISABLE2_FLAGS));

        controller.restoreFromOriginalValue(mSettingState, TEST_USER_ID);

        verify(mStatusBarService, never()).disable(anyInt(), any(), anyString());
        verify(mStatusBarService, never()).disable2(anyInt(), any(), anyString());
    }

    @Test
    public void testRestoreFromOriginalValue_skipsIfOriginalValueIsNull() throws Exception {
        when(mSettingState.getOriginalValue()).thenReturn(null);

        mDisableFlagsController.restoreFromOriginalValue(mSettingState, TEST_USER_ID);

        verify(mStatusBarService, never()).disable(anyInt(), any(), anyString());
        verify(mStatusBarService, never()).disable2(anyInt(), any(), anyString());
    }

}
