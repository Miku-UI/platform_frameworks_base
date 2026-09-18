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

package android.app.admin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class MultiuserManagedDeviceProvisioningParamsTest {

    private static final String TEST_PACKAGE_NAME = "com.example.clouddpc";
    private static final String TEST_PACKAGE_NAME_2 = "com.example.clouddpc2";

    @Test
    public void testEqualsAndHashCode() {
        MultiuserManagedDeviceProvisioningParams params =
                new MultiuserManagedDeviceProvisioningParams.Builder(TEST_PACKAGE_NAME).build();
        MultiuserManagedDeviceProvisioningParams params2 =
                new MultiuserManagedDeviceProvisioningParams.Builder(TEST_PACKAGE_NAME).build();
        assertEquals(params, params2);
        assertEquals(params.hashCode(), params2.hashCode());

        MultiuserManagedDeviceProvisioningParams params3 =
                new MultiuserManagedDeviceProvisioningParams.Builder(TEST_PACKAGE_NAME_2).build();
        assertFalse(params.equals(params3));
        assertFalse(params.hashCode() == params3.hashCode());
    }
}
