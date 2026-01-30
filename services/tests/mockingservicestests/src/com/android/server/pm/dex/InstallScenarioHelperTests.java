/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.server.pm.dex;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.BatteryManager;
import android.os.PowerManager;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.dx.mockito.inline.extended.StaticMockitoSession;
import com.android.server.art.ReasonMapping;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.quality.Strictness;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class InstallScenarioHelperTests {
    private static final int TEST_BATTERY_LEVEL_CRITICAL = 10;
    private static final int TEST_BATTERY_LEVEL_DEFAULT = 80;

    public StaticMockitoSession mMockitoSession;
    @Mock BatteryManager mMockBatteryManager;
    @Mock PowerManager mMockPowerManager;

    private InstallScenarioHelper mInstallScenarioHelper;

    @Before
    public void setup() {
        // Initialize Static Mocking

        mMockitoSession = ExtendedMockito.mockitoSession()
            .initMocks(this)
            .strictness(Strictness.LENIENT)
            .startMocking();

        // Mock....

        mMockBatteryManager = ExtendedMockito.mock(BatteryManager.class);
        mMockPowerManager   = ExtendedMockito.mock(PowerManager.class);

        setDefaultMockValues();

        Resources mockResources = ExtendedMockito.mock(Resources.class);
        ExtendedMockito.when(mockResources
            .getInteger(com.android.internal.R.integer.config_criticalBatteryWarningLevel))
                .thenReturn(15);

        Context mockContext = ExtendedMockito.mock(Context.class);
        ExtendedMockito.doReturn(mockResources)
            .when(mockContext)
                .getResources();
        ExtendedMockito.doReturn(mMockBatteryManager)
            .when(mockContext)
                .getSystemService(BatteryManager.class);
        ExtendedMockito.doReturn(mMockPowerManager)
            .when(mockContext)
                .getSystemService(PowerManager.class);

        mInstallScenarioHelper = new InstallScenarioHelper(mockContext);
    }

    @After
    public void teardown() throws Exception {
        mMockitoSession.finishMocking();
    }

    private void setDefaultMockValues() {
        ExtendedMockito.doReturn(BatteryManager.BATTERY_STATUS_DISCHARGING)
            .when(mMockBatteryManager)
                .getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS);

        ExtendedMockito.doReturn(TEST_BATTERY_LEVEL_DEFAULT)
            .when(mMockBatteryManager)
                .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);

        ExtendedMockito.doReturn(PowerManager.THERMAL_STATUS_NONE)
            .when(mMockPowerManager)
                .getCurrentThermalStatus();
    }

    @Test
    public void testInstallScenarioToReasonDefault() {
        assertEquals(
                ReasonMapping.REASON_INSTALL,
                mInstallScenarioHelper.getCompilationReasonForInstallScenario(
                        PackageManager.INSTALL_SCENARIO_DEFAULT));

        assertEquals(
                ReasonMapping.REASON_INSTALL_FAST,
                mInstallScenarioHelper.getCompilationReasonForInstallScenario(
                        PackageManager.INSTALL_SCENARIO_FAST));

        assertEquals(
                ReasonMapping.REASON_INSTALL_BULK,
                mInstallScenarioHelper.getCompilationReasonForInstallScenario(
                        PackageManager.INSTALL_SCENARIO_BULK));

        assertEquals(
                ReasonMapping.REASON_INSTALL_BULK_SECONDARY,
                mInstallScenarioHelper.getCompilationReasonForInstallScenario(
                        PackageManager.INSTALL_SCENARIO_BULK_SECONDARY));
    }

    @Test
    public void testInstallScenarioToReasonThermal() {
        ExtendedMockito.doReturn(PowerManager.THERMAL_STATUS_SEVERE)
            .when(mMockPowerManager)
                .getCurrentThermalStatus();

        assertEquals(
                ReasonMapping.REASON_INSTALL,
                mInstallScenarioHelper.getCompilationReasonForInstallScenario(
                        PackageManager.INSTALL_SCENARIO_DEFAULT));

        assertEquals(
                ReasonMapping.REASON_INSTALL_FAST,
                mInstallScenarioHelper.getCompilationReasonForInstallScenario(
                        PackageManager.INSTALL_SCENARIO_FAST));

        assertEquals(
                ReasonMapping.REASON_INSTALL_BULK_DOWNGRADED,
                mInstallScenarioHelper.getCompilationReasonForInstallScenario(
                        PackageManager.INSTALL_SCENARIO_BULK));

        assertEquals(
                ReasonMapping.REASON_INSTALL_BULK_SECONDARY_DOWNGRADED,
                mInstallScenarioHelper.getCompilationReasonForInstallScenario(
                        PackageManager.INSTALL_SCENARIO_BULK_SECONDARY));
    }

    @Test
    public void testInstallScenarioToReasonBatteryDischarging() {
        ExtendedMockito.doReturn(TEST_BATTERY_LEVEL_CRITICAL)
            .when(mMockBatteryManager)
                .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);

        assertEquals(
                ReasonMapping.REASON_INSTALL,
                mInstallScenarioHelper.getCompilationReasonForInstallScenario(
                        PackageManager.INSTALL_SCENARIO_DEFAULT));

        assertEquals(
                ReasonMapping.REASON_INSTALL_FAST,
                mInstallScenarioHelper.getCompilationReasonForInstallScenario(
                        PackageManager.INSTALL_SCENARIO_FAST));

        assertEquals(
                ReasonMapping.REASON_INSTALL_BULK_DOWNGRADED,
                mInstallScenarioHelper.getCompilationReasonForInstallScenario(
                        PackageManager.INSTALL_SCENARIO_BULK));

        assertEquals(
                ReasonMapping.REASON_INSTALL_BULK_SECONDARY_DOWNGRADED,
                mInstallScenarioHelper.getCompilationReasonForInstallScenario(
                        PackageManager.INSTALL_SCENARIO_BULK_SECONDARY));
    }

    @Test
    public void testInstallScenarioToReasonBatteryCharging() {
        ExtendedMockito.doReturn(TEST_BATTERY_LEVEL_CRITICAL)
            .when(mMockBatteryManager)
                .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);

        ExtendedMockito.doReturn(BatteryManager.BATTERY_STATUS_CHARGING)
            .when(mMockBatteryManager)
                .getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS);

        assertEquals(
                ReasonMapping.REASON_INSTALL,
                mInstallScenarioHelper.getCompilationReasonForInstallScenario(
                        PackageManager.INSTALL_SCENARIO_DEFAULT));

        assertEquals(
                ReasonMapping.REASON_INSTALL_FAST,
                mInstallScenarioHelper.getCompilationReasonForInstallScenario(
                        PackageManager.INSTALL_SCENARIO_FAST));

        assertEquals(
                ReasonMapping.REASON_INSTALL_BULK,
                mInstallScenarioHelper.getCompilationReasonForInstallScenario(
                        PackageManager.INSTALL_SCENARIO_BULK));

        assertEquals(
                ReasonMapping.REASON_INSTALL_BULK_SECONDARY,
                mInstallScenarioHelper.getCompilationReasonForInstallScenario(
                        PackageManager.INSTALL_SCENARIO_BULK_SECONDARY));
    }
}
