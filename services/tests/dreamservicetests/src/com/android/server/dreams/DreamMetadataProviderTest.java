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

package com.android.server.dreams;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.service.dreams.DreamItem;
import android.service.dreams.DreamService;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class DreamMetadataProviderTest {
    @Rule public final MockitoRule mMockitoRule = MockitoJUnit.rule();

    @Mock private Context mContext;
    @Mock private PackageManager mPackageManager;
    @Mock private DreamMetadataProvider.DreamMetadataParser mMetadataParser;

    private DreamMetadataProvider mProvider;

    private static final ComponentName COMPONENT =
            ComponentName.unflattenFromString("com.example/.Dream");

    @Before
    public void setUp() {
        when(mContext.getPackageManager()).thenReturn(mPackageManager);
        mProvider = new DreamMetadataProvider(mContext, mMetadataParser);
    }

    @Test
    public void testGetDreamItem_success() throws Exception {
        ServiceInfo serviceInfo = new ServiceInfo();
        serviceInfo.applicationInfo = new ApplicationInfo();
        serviceInfo.applicationInfo.targetSdkVersion = Build.VERSION_CODES.LOLLIPOP;
        serviceInfo.permission = android.Manifest.permission.BIND_DREAM_SERVICE;
        serviceInfo.packageName = COMPONENT.getPackageName();
        serviceInfo.name = COMPONENT.getClassName();

        when(mPackageManager.getServiceInfo(eq(COMPONENT), anyInt())).thenReturn(serviceInfo);
        serviceInfo.nonLocalizedLabel = "Title";

        DreamService.DreamMetadata metadata = mock(DreamService.DreamMetadata.class);
        when(mMetadataParser.getDreamMetadata(mPackageManager, serviceInfo)).thenReturn(metadata);

        DreamItem item = mProvider.getDreamItem(COMPONENT);
        assertThat(item).isNotNull();
        assertThat(item.componentName).isEqualTo(COMPONENT);
        assertThat(item.title).isEqualTo("Title");
    }

    @Test
    public void testGetDreamItem_cacheHit() throws Exception {
        ServiceInfo serviceInfo = new ServiceInfo();
        serviceInfo.applicationInfo = new ApplicationInfo();
        serviceInfo.applicationInfo.targetSdkVersion = Build.VERSION_CODES.LOLLIPOP;
        serviceInfo.permission = android.Manifest.permission.BIND_DREAM_SERVICE;
        serviceInfo.packageName = COMPONENT.getPackageName();

        when(mPackageManager.getServiceInfo(eq(COMPONENT), anyInt())).thenReturn(serviceInfo);
        serviceInfo.nonLocalizedLabel = "Title";

        // First call
        DreamItem item1 = mProvider.getDreamItem(COMPONENT);
        assertThat(item1).isNotNull();

        // Second call should not hit PackageManager
        DreamItem item2 = mProvider.getDreamItem(COMPONENT);
        assertThat(item2).isNotNull();
        assertThat(item2).isEqualTo(item1);

        verify(mPackageManager, times(1)).getServiceInfo(eq(COMPONENT), anyInt());
    }

    @Test
    public void testGetDreamItem_componentNotFound() throws Exception {
        when(mPackageManager.getServiceInfo(eq(COMPONENT), anyInt()))
                .thenThrow(new PackageManager.NameNotFoundException());

        DreamItem item = mProvider.getDreamItem(COMPONENT);
        assertThat(item).isNull();
    }

    @Test
    public void testGetDreamItem_missingPermission() throws Exception {
        ServiceInfo serviceInfo = new ServiceInfo();
        serviceInfo.applicationInfo = new ApplicationInfo();
        serviceInfo.applicationInfo.targetSdkVersion = Build.VERSION_CODES.LOLLIPOP;
        serviceInfo.permission = "android.permission.WRONG_PERMISSION";
        serviceInfo.packageName = COMPONENT.getPackageName();

        when(mPackageManager.getServiceInfo(eq(COMPONENT), anyInt())).thenReturn(serviceInfo);

        DreamItem item = mProvider.getDreamItem(COMPONENT);
        assertThat(item).isNull();
    }

    @Test
    public void testGetDreamItem_legacyApp_noPermissionRequired() throws Exception {
        ServiceInfo serviceInfo = new ServiceInfo();
        serviceInfo.applicationInfo = new ApplicationInfo();
        serviceInfo.applicationInfo.targetSdkVersion = Build.VERSION_CODES.KITKAT;
        serviceInfo.permission = null; // No permission
        serviceInfo.packageName = COMPONENT.getPackageName();

        when(mPackageManager.getServiceInfo(eq(COMPONENT), anyInt())).thenReturn(serviceInfo);
        serviceInfo.nonLocalizedLabel = "Title";

        DreamItem item = mProvider.getDreamItem(COMPONENT);
        assertThat(item).isNotNull();
    }

    @Test
    public void testInvalidatePackage() throws Exception {
        ServiceInfo serviceInfo = new ServiceInfo();
        serviceInfo.applicationInfo = new ApplicationInfo();
        serviceInfo.applicationInfo.targetSdkVersion = Build.VERSION_CODES.LOLLIPOP;
        serviceInfo.permission = android.Manifest.permission.BIND_DREAM_SERVICE;
        serviceInfo.packageName = COMPONENT.getPackageName();

        when(mPackageManager.getServiceInfo(eq(COMPONENT), anyInt())).thenReturn(serviceInfo);

        // Load item
        mProvider.getDreamItem(COMPONENT);
        verify(mPackageManager, times(1)).getServiceInfo(eq(COMPONENT), anyInt());

        // Invalidate unrelated package
        mProvider.invalidatePackage("com.other.package");
        mProvider.getDreamItem(COMPONENT);
        verify(mPackageManager, times(1))
                .getServiceInfo(eq(COMPONENT), anyInt()); // Should still use cache

        // Invalidate component's package
        mProvider.invalidatePackage(COMPONENT.getPackageName());
        mProvider.getDreamItem(COMPONENT);
        verify(mPackageManager, times(2)).getServiceInfo(eq(COMPONENT), anyInt()); // Should re-load
    }

    @Test
    public void testInvalidateCache() throws Exception {
        ServiceInfo serviceInfo = new ServiceInfo();
        serviceInfo.applicationInfo = new ApplicationInfo();
        serviceInfo.applicationInfo.targetSdkVersion = Build.VERSION_CODES.LOLLIPOP;
        serviceInfo.permission = android.Manifest.permission.BIND_DREAM_SERVICE;
        serviceInfo.packageName = COMPONENT.getPackageName();

        when(mPackageManager.getServiceInfo(eq(COMPONENT), anyInt())).thenReturn(serviceInfo);

        // Load item
        mProvider.getDreamItem(COMPONENT);
        verify(mPackageManager, times(1)).getServiceInfo(eq(COMPONENT), anyInt());

        // Invalidate all
        mProvider.invalidateCache();
        mProvider.getDreamItem(COMPONENT);
        verify(mPackageManager, times(2)).getServiceInfo(eq(COMPONENT), anyInt());
    }

    @Test
    public void testGetDreamItem_cachedNull() throws Exception {
        // First call fails (e.g. not found)
        when(mPackageManager.getServiceInfo(eq(COMPONENT), anyInt()))
                .thenThrow(new PackageManager.NameNotFoundException());
        assertThat(mProvider.getDreamItem(COMPONENT)).isNull();

        assertThat(mProvider.getDreamItem(COMPONENT)).isNull();
        verify(mPackageManager, times(1)).getServiceInfo(eq(COMPONENT), anyInt());
    }
}
