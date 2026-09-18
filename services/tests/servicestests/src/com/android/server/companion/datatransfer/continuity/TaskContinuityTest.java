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

package com.android.server.companion.datatransfer.continuity;

import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.when;

import android.app.ActivityTaskManager;
import android.app.AppOpsManager;
import android.companion.AssociationInfo;
import android.companion.CompanionDeviceManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManagerInternal;
import android.testing.AndroidTestingRunner;
import com.android.server.LocalServices;
import com.android.server.companion.datatransfer.continuity.connectivity.TaskContinuityMessenger;
import com.android.server.wm.ActivityTaskManagerInternal;
import org.junit.After;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidTestingRunner.class)
public abstract class TaskContinuityTest {

    public static String COMPANION_APP_PACKAGE_NAME = "com.android.companion";
    public static int USER_ID = 0;

    @Mock public Context mMockContext;
    @Mock public PackageManager mMockPackageManager;
    @Mock public ActivityTaskManager mMockActivityTaskManager;
    @Mock public AppOpsManager mMockAppOpsManager;
    @Mock public ActivityTaskManagerInternal mMockActivityTaskManagerInternal;
    @Mock public PackageManagerInternal mMockPackageManagerInternal;
    @Mock public TaskContinuityMessenger mMockTaskContinuityMessenger;

    @Before
    public void initMocks() {
        MockitoAnnotations.initMocks(this);
        when(mMockContext.getPackageManager()).thenReturn(mMockPackageManager);
        when(mMockContext.getSystemService(Context.ACTIVITY_TASK_SERVICE))
                .thenReturn(mMockActivityTaskManager);
        when(mMockContext.getSystemService(Context.APP_OPS_SERVICE)).thenReturn(mMockAppOpsManager);
        LocalServices.addService(
                ActivityTaskManagerInternal.class, mMockActivityTaskManagerInternal);
        LocalServices.addService(PackageManagerInternal.class, mMockPackageManagerInternal);
    }

    @After
    public void tearDown() {
        LocalServices.removeServiceForTest(ActivityTaskManagerInternal.class);
        LocalServices.removeServiceForTest(PackageManagerInternal.class);
    }

    public void setApplicationInfo(String packageName, String label) {
        PackageInfo packageInfo = new PackageInfo();
        packageInfo.packageName = packageName;
        packageInfo.applicationInfo = new ApplicationInfo();
        packageInfo.applicationInfo.name = packageName;
        try {
            when(mMockPackageManager.getPackageInfo(
                            eq(packageName), eq(PackageManager.GET_META_DATA)))
                    .thenReturn(packageInfo);
        } catch (PackageManager.NameNotFoundException e) {
        }
        when(mMockPackageManager.getApplicationLabel(eq(packageInfo.applicationInfo)))
                .thenReturn(label);
    }

    public AssociationInfo createAssociationInfo(int userId, int associationId) {
        String deviceName = "name" + associationId;
        return new AssociationInfo.Builder(associationId, userId, COMPANION_APP_PACKAGE_NAME)
                .setDisplayName(deviceName)
                .setSystemDataSyncFlags(CompanionDeviceManager.FLAG_TASK_CONTINUITY)
                .build();
    }
}
