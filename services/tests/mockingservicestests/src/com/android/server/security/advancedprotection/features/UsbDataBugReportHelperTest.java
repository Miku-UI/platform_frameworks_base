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

package com.android.server.security.advancedprotection.features;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import android.content.Context;
import android.os.BugreportParams;
import android.os.BugreportManager;
import androidx.test.ext.junit.runners.AndroidJUnit4;

/**
 * Unit tests for {@link UsbDataBugReportHelper}.
 *
 * <p>atest UsbDataBugReportHelperTest
 */
@RunWith(AndroidJUnit4.class)
public class UsbDataBugReportHelperTest {

    @Mock private Context mContext;
    @Mock private BugreportManager mBugReportManager;

    @Captor private ArgumentCaptor<BugreportParams> mBugReportParamsCaptor;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(mContext.getSystemService(BugreportManager.class)).thenReturn(mBugReportManager);
    }

    @Test
    public void reportFailureToEnableUsbData_reportsBugReport() throws Exception {
        UsbDataBugReportHelper helper = new UsbDataBugReportHelper(mContext);

        helper.report(UsbDataBugReportHelper.REPORT_REASON_FAILURE_TO_ENABLE_USB_DATA);

        verify(mBugReportManager)
                .requestBugreport(mBugReportParamsCaptor.capture(), anyString(), anyString());
        BugreportParams params = mBugReportParamsCaptor.getValue();
        assertEquals(BugreportParams.BUGREPORT_MODE_FULL, params.getMode());
    }

    @Test
    public void reportFailureToEnableUsbData_reportsBugReportOnlyOnce() throws Exception {
        UsbDataBugReportHelper helper = new UsbDataBugReportHelper(mContext);

        helper.report(UsbDataBugReportHelper.REPORT_REASON_FAILURE_TO_ENABLE_USB_DATA);
        helper.report(UsbDataBugReportHelper.REPORT_REASON_FAILURE_TO_ENABLE_USB_DATA);

        verify(mBugReportManager, times(1))
                .requestBugreport(mBugReportParamsCaptor.capture(), anyString(), anyString());
        BugreportParams params = mBugReportParamsCaptor.getValue();
        assertEquals(BugreportParams.BUGREPORT_MODE_FULL, params.getMode());
    }
}
