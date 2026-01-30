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

import static android.hardware.usb.InternalUsbDataSignalDisableReason.USB_DISABLE_REASON_LOCKDOWN_MODE;
import static android.hardware.usb.UsbPortStatus.DATA_STATUS_DISABLED_FORCE;
import static android.hardware.usb.UsbPortStatus.DATA_STATUS_ENABLED;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.kotlin.MatchersKt.eq;

import android.content.Context;
import android.hardware.usb.IUsbManager;
import android.hardware.usb.IUsbManagerInternal;
import android.hardware.usb.ParcelableUsbPort;
import android.hardware.usb.UsbManager;
import android.hardware.usb.UsbPort;
import android.hardware.usb.UsbPortStatus;
import android.os.RemoteException;
import android.util.Slog;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UsbSettingControllerTest {
    @Rule public MockitoRule mockito = MockitoJUnit.rule();

    @Mock
    private IUsbManager mIUsbManager;
    @Mock
    private IUsbManagerInternal mUsbManagerInternal;
    @Mock
    private SettingState<Map<String, Boolean>> mSettingState;

    private static final String TAG = "UsbSettingControllerTest";
    private static final String TEST_PORT_ID = "test_port";
    private static final int TEST_USER_ID = 0;

    private final Context mContext =
            InstrumentationRegistry.getInstrumentation().getTargetContext();
    private final UsbPortStatus mEnabledUsbPortStatus = new UsbPortStatus(
            0, 0, 0, 0, 0, 0, DATA_STATUS_ENABLED, false, 0);
    private final UsbPortStatus mDisabledUsbPortStatus = new UsbPortStatus(
            0, 0, 0, 0, 0, 0, DATA_STATUS_DISABLED_FORCE, false, 0);

    private Map<String, Boolean> mTestSecureLockDeviceValue;
    private UsbPort mUsbPort;
    private UsbSettingController mUsbSettingController;

    @Before
    public void setUp() throws RemoteException {
        UsbManager usbManager = new UsbManager(mContext, mIUsbManager);
        mUsbSettingController = new UsbSettingController();
        mUsbSettingController.setUsbManager(usbManager);
        mUsbSettingController.setUsbManagerInternal(mUsbManagerInternal);
        mTestSecureLockDeviceValue = new HashMap<>();
        mTestSecureLockDeviceValue.put(TEST_PORT_ID, true);

        mUsbPort = new UsbPort(usbManager, TEST_PORT_ID, 0, 0, true, true, true, 0);
        final ParcelableUsbPort usbPort = ParcelableUsbPort.of(mUsbPort);
        when(mIUsbManager.getPorts()).thenReturn(List.of(usbPort));

        doAnswer(invocation -> {
            boolean enable = invocation.getArgument(1);
            if (enable) {
                when(mIUsbManager.getPortStatus(eq(TEST_PORT_ID)))
                        .thenReturn(mEnabledUsbPortStatus);
            } else {
                when(mIUsbManager.getPortStatus(eq(TEST_PORT_ID)))
                        .thenReturn(mDisabledUsbPortStatus);
            }
            return true;
        }).when(mIUsbManager).enableUsbData(eq(TEST_PORT_ID), anyBoolean(), anyInt(), any());
    }

    @Test
    public void testStoreOriginalValue_skipsIfUsbManagerInternalIsNull() throws Exception {
        mUsbSettingController.setUsbManagerInternal(null);

        mUsbSettingController.storeOriginalValue(mSettingState, TEST_USER_ID);

        verify(mSettingState, never()).setOriginalValue(any());

    }

    @Test
    public void testApplySecureLockDeviceValue_skipsIfTestMode() throws Exception {
        mUsbSettingController.setSkipSecurityFeaturesForTest(true);
        when(mSettingState.getSecureLockDeviceValue()).thenReturn(mTestSecureLockDeviceValue);

        mUsbSettingController.applySecureLockDeviceValue(mSettingState, TEST_USER_ID);

        verify(mUsbManagerInternal, never()).enableUsbDataSignal(anyBoolean(), anyInt());

    }

    @Test
    public void testApplySecureLockDeviceValue_skipsIfUsbManagerInternalIsNull() throws Exception {
        mUsbSettingController.setUsbManagerInternal(null);
        when(mSettingState.getSecureLockDeviceValue()).thenReturn(mTestSecureLockDeviceValue);

        mUsbSettingController.applySecureLockDeviceValue(mSettingState, TEST_USER_ID);

        verify(mUsbManagerInternal, never()).enableUsbDataSignal(anyBoolean(), anyInt());

    }

    @Test
    public void testApplySecureLockDeviceValue_successfullyDisablesUsbData()
            throws Exception {
        when(mSettingState.getSecureLockDeviceValue()).thenReturn(mTestSecureLockDeviceValue);

        mUsbSettingController.applySecureLockDeviceValue(mSettingState, TEST_USER_ID);

        verify(mUsbManagerInternal).enableUsbDataSignal(false,
                USB_DISABLE_REASON_LOCKDOWN_MODE);
    }

    @Test
    public void testRestoreFromOriginalValue_skipsIfUsbManagerIsNull() throws Exception {
        mUsbSettingController.setUsbManager(null);
        when(mSettingState.getOriginalValue()).thenReturn(mTestSecureLockDeviceValue);

        mUsbSettingController.restoreFromOriginalValue(mSettingState, TEST_USER_ID);

        verify(mIUsbManager, never()).getPorts();
    }

    @Test
    public void testRestoreFromOriginalValue_skipsIfOriginalValueIsNull() throws Exception {
        when(mIUsbManager.getPorts()).thenReturn(Collections.emptyList());
        when(mSettingState.getOriginalValue()).thenReturn(null);

        mUsbSettingController.restoreFromOriginalValue(mSettingState, TEST_USER_ID);

        verify(mIUsbManager, never()).enableUsbData(anyString(), anyBoolean(), anyInt(), any());
    }

    @Test
    public void testRestoreFromOriginalValue_restoresPortSuccessfully() throws Exception {
        mUsbPort.enableUsbData(true);
        when(mSettingState.getOriginalValue()).thenReturn(mTestSecureLockDeviceValue);

        mUsbSettingController.restoreFromOriginalValue(mSettingState, TEST_USER_ID);

        UsbPortStatus portStatus = mUsbPort.getStatus();
        if (portStatus != null) {
            int usbDataStatus = portStatus.getUsbDataStatus();
            boolean isPortEnabled = (usbDataStatus & DATA_STATUS_DISABLED_FORCE) == 0;
            assertThat(isPortEnabled).isTrue();
        } else {
            Slog.e(TAG, "UsbPortStatus is null");
        }

    }

    @Test
    public void testRestoreFromOriginalValue_doesNotRestorePortIfOriginallyDisabled()
            throws Exception {
        mUsbPort.enableUsbData(false);
        Map<String, Boolean> originalPortStates = new HashMap<>();
        originalPortStates.put(TEST_PORT_ID, false);
        when(mSettingState.getOriginalValue()).thenReturn(originalPortStates);

        mUsbSettingController.restoreFromOriginalValue(mSettingState, TEST_USER_ID);

        UsbPortStatus portStatus = mUsbPort.getStatus();
        if (portStatus != null) {
            int usbDataStatus = portStatus.getUsbDataStatus();
            boolean isPortEnabled = (usbDataStatus & DATA_STATUS_DISABLED_FORCE) == 0;
            assertThat(isPortEnabled).isFalse();
        } else {
            Slog.e(TAG, "UsbPortStatus is null");
        }
    }
}
