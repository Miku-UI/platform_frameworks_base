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

package android.test.appwarnings;

import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.platform.test.annotations.AppModeFull;

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.FileNotFoundException;

@RunWith(DeviceJUnit4ClassRunner.class)
public class AppWarningsHostTest extends BaseHostJUnit4Test {
    private static final String TEST_APP_COMPAT_ENABLED = "app_with_4kb_elf.apk";
    private static final String TEST_APP_COMPAT_DISABLED = "page_size_compat_disabled_app.apk";
    private static final String TEST_APP_NON_DEBUG = "app_with_4kb_elf_no_override.apk";
    private static final String TEST_APP_DEBUG = "app_with_4kb_elf_no_override_debuggable.apk";

    private static final String TEST_NO_WARNINGS = "testNoWarnings";
    private static final String TEST_WARNINGS = "testWarnings";
    private static final String TEST_INSTALL_VIA_SESSION = "installAppViaSession";

    private static final int DEVICE_WAIT_TIMEOUT = 120000;
    private static final String TEST_APP_PACKAGE = "android.test.pagesizecompat";

    private CompatibilityBuildHelper mBuildHelper;

    @Before
    public void setup() throws Exception {
        mBuildHelper = new CompatibilityBuildHelper(getBuild());
        // Only run on userdebug devices.
        String buildType = getDevice().getProperty("ro.build.type");
        assumeTrue(buildType.equals("userdebug") || buildType.equals("eng"));

        getDevice().waitForDeviceAvailable(DEVICE_WAIT_TIMEOUT);
        getDevice().executeShellCommand("input keyevent KEYCODE_WAKEUP");
        getDevice().executeShellCommand("wm dismiss-keyguard");
    }

    private void installAppViaAdb(String appName)
            throws FileNotFoundException, DeviceNotAvailableException {
        String fullPath = mBuildHelper.getTestFile(appName).getAbsolutePath();
        getDevice().executeAdbCommand("install", fullPath);
    }

    private void runPageSizeWarningsTest(String appName, String testMethodName)
            throws FileNotFoundException, DeviceNotAvailableException, TargetSetupError {
        getDevice().enableAdbRoot();
        if (isPackageInstalled(TEST_APP_PACKAGE)) {
            uninstallPackage(TEST_APP_PACKAGE);
        }

        installAppViaAdb(appName);

        String testPackage = "android.content.pm.tests";
        String testName = "AppWarningsTest";
        assertTrue(isPackageInstalled(testPackage));
        assertTrue(isPackageInstalled(TEST_APP_PACKAGE));

        assertTrue(runDeviceTests(testPackage, testPackage + "." + testName, testMethodName));

        if (isPackageInstalled(TEST_APP_PACKAGE)) {
            uninstallPackage(TEST_APP_PACKAGE);
        }
    }

    @Test
    @AppModeFull
    public void testNoWarnings_installedBySession()
            throws FileNotFoundException, DeviceNotAvailableException, TargetSetupError {
        String appPackage = "android.content.pm.tests";
        String testName = "AppWarningsTest";
        assertTrue(isPackageInstalled(appPackage));
        // Run a test to install the app using pacakage manager session first and then check
        // for no warnings.
        runDeviceTests(appPackage, appPackage + "." + testName, TEST_INSTALL_VIA_SESSION);
        runDeviceTests(appPackage, appPackage + "." + testName, TEST_NO_WARNINGS);
    }

    @Test
    @AppModeFull
    public void runAppWith4KbLib_overrideCompatMode()
            throws FileNotFoundException, DeviceNotAvailableException, TargetSetupError {
        runPageSizeWarningsTest(TEST_APP_COMPAT_ENABLED, TEST_WARNINGS);
    }

    @Test
    @AppModeFull
    public void runAppWith4KbLib_disabledCompatMode()
            throws FileNotFoundException, DeviceNotAvailableException, TargetSetupError {
        runPageSizeWarningsTest(TEST_APP_COMPAT_DISABLED, TEST_WARNINGS);
    }

    @Test
    @AppModeFull
    public void runAppWith4KbLib_installedByAdb()
            throws FileNotFoundException, DeviceNotAvailableException, TargetSetupError {
        runPageSizeWarningsTest(TEST_APP_DEBUG, TEST_WARNINGS);
    }

    @Test
    @AppModeFull
    public void runNonDebugAppWith4KbLib_installedByAdb()
            throws FileNotFoundException, DeviceNotAvailableException, TargetSetupError {
        runPageSizeWarningsTest(TEST_APP_NON_DEBUG, TEST_NO_WARNINGS);
    }
}
