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

package com.android.server.pm;

import static com.google.common.truth.Truth.assertThat;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInfoList;
import android.content.pm.PermissionInfo;
import android.os.Parcel;
import android.platform.test.annotations.Presubmit;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Presubmit
@RunWith(AndroidJUnit4.class)
@SmallTest
public class PackageInfoListTest {
    @Test
    public void testParcelWithSameStrings() {
        List<PackageInfo> rawList = new ArrayList<>();
        String[] permissionStrings = generatePermissionStrings(10);
        // Create test package info objects that share same permission strings
        PackageInfo pi1 = generatePackageInfoWithPermissionStrings("com.foo1", permissionStrings);
        PackageInfo pi2 = generatePackageInfoWithPermissionStrings("com.foo2", permissionStrings);
        rawList.add(pi1);
        rawList.add(pi2);
        PackageInfoList packageInfoList = new PackageInfoList(rawList);
        PackageInfoList parcelled = parcelAndUnparcel(packageInfoList);
        assertPackageInfoListEquals(packageInfoList, parcelled);
    }

    @Test
    public void testParcelWithDifferentStrings() {
        List<PackageInfo> rawList = new ArrayList<>();
        String[] permissionStrings1 = generatePermissionStrings(10);
        String[] permissionStrings2 = generatePermissionStrings(5);
        // Create test package info objects that have different permission strings
        PackageInfo pi1 = generatePackageInfoWithPermissionStrings("com.foo1", permissionStrings1);
        PackageInfo pi2 = generatePackageInfoWithPermissionStrings("com.foo2", permissionStrings2);
        rawList.add(pi1);
        rawList.add(pi2);
        PackageInfoList packageInfoList = new PackageInfoList(rawList);
        PackageInfoList parcelled = parcelAndUnparcel(packageInfoList);
        assertPackageInfoListEquals(packageInfoList, parcelled);
    }

    private PackageInfoList parcelAndUnparcel(PackageInfoList packageInfoList) {
        Parcel parcel = Parcel.obtain();
        try {
            packageInfoList.writeToParcel(parcel, 0);
            parcel.setDataPosition(0);
            return PackageInfoList.CREATOR.createFromParcel(parcel);
        } finally {
            parcel.recycle();
        }
    }

    private PackageInfo generatePackageInfoWithPermissionStrings(
            String packageName, String[] permissionStrings) {
        int numPermissions = permissionStrings.length;
        PackageInfo packageInfo = new PackageInfo();
        packageInfo.packageName = packageName;
        packageInfo.applicationInfo = new ApplicationInfo();
        packageInfo.permissions = new PermissionInfo[numPermissions];
        for (int i = 0; i < numPermissions; i++) {
            packageInfo.permissions[i] = new PermissionInfo((String) null);
            packageInfo.permissions[i].name = permissionStrings[i];
            packageInfo.permissions[i].packageName = packageName;
        }
        return packageInfo;
    }

    private String[] generatePermissionStrings(int numPermissions) {
        String[] permissionStrings = new String[numPermissions];
        for (int i = 0; i < numPermissions; i++) {
            // Create a unique permission string with random characters
            permissionStrings[i] = randomString();
        }
        return permissionStrings;
    }

    private static String randomString() {
        Random random = new Random(1234);
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            b.append((char) (' ' + random.nextInt('~' - ' ' + 1)));
        }
        return b.toString();
    }

    private void assertPackageInfoListEquals(PackageInfoList a, PackageInfoList b) {
        List<PackageInfo> aList = a.getList();
        List<PackageInfo> bList = b.getList();
        assertThat(aList.size()).isEqualTo(bList.size());
        for (int i = 0; i < aList.size(); i++) {
            PackageInfo aPackageInfo = aList.get(i);
            PackageInfo bPackageInfo = bList.get(i);
            assertThat(aPackageInfo.packageName).isEqualTo(bPackageInfo.packageName);
            assertThat(aPackageInfo.permissions.length).isEqualTo(bPackageInfo.permissions.length);
            for (int j = 0; j < aPackageInfo.permissions.length; j++) {
                PermissionInfo aPermissionInfo = aPackageInfo.permissions[j];
                PermissionInfo bPermissionInfo = bPackageInfo.permissions[j];
                assertThat(aPermissionInfo.name).isEqualTo(bPermissionInfo.name);
                assertThat(aPermissionInfo.packageName).isEqualTo(bPermissionInfo.packageName);
            }
        }
    }
}
