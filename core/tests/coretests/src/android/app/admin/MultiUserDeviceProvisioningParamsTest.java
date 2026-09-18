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

package android.app.admin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import android.content.ComponentName;
import android.platform.test.annotations.Presubmit;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import java.util.Locale;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class MultiUserDeviceProvisioningParamsTest {

    private static final String TEST_PACKAGE_NAME = "com.example.clouddpc";
    private static final String TEST_RECEIVER_CLASS_NAME =
            TEST_PACKAGE_NAME + ".DeviceAdminReceiver";

   @Test
    public void testEqualsAndHashCode() {
        MultiUserDeviceProvisioningParams params = new MultiUserDeviceProvisioningParams.Builder(
                new ComponentName(TEST_PACKAGE_NAME, TEST_RECEIVER_CLASS_NAME))
                .setLeaveAllSystemAppsEnabled(true)
                .setTimeZone("Europe/London")
                .setLocalTime(123456787L)
                .setLocale(Locale.UK)
                .build();
        MultiUserDeviceProvisioningParams params2 = new MultiUserDeviceProvisioningParams.Builder(
                new ComponentName(TEST_PACKAGE_NAME, TEST_RECEIVER_CLASS_NAME))
                .setLeaveAllSystemAppsEnabled(true)
                .setTimeZone("Europe/London")
                .setLocalTime(123456787L)
                .setLocale(Locale.UK)
                .build();
        assertEquals(params, params2);
        assertEquals(params.hashCode(), params2.hashCode());

        MultiUserDeviceProvisioningParams params3 = new MultiUserDeviceProvisioningParams.Builder(
                new ComponentName(TEST_PACKAGE_NAME, TEST_RECEIVER_CLASS_NAME))
                .setLeaveAllSystemAppsEnabled(false)
                .setTimeZone("Europe/London")
                .setLocalTime(123456788L)
                .setLocale(Locale.UK)
                .build();
        assertFalse(params.equals(params3));
        assertFalse(params.hashCode() == params3.hashCode());
    }
}