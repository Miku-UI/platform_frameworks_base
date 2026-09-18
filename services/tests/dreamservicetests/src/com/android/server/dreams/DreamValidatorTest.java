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

import static android.Manifest.permission.BIND_DREAM_SERVICE;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Build;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class DreamValidatorTest {
    @Rule public final MockitoRule mMockitoRule = MockitoJUnit.rule();

    @Mock private Context mContext;
    @Mock private PackageManager mPackageManager;

    private DreamValidator mDreamValidator;

    @Before
    public void setUp() throws Exception {
        when(mContext.getPackageManager()).thenReturn(mPackageManager);
        mDreamValidator = new DreamValidator(mContext);
    }

    @Test
    public void testValidate_nullComponent() {
        assertThat(mDreamValidator.validate(null, 0)).isFalse();
    }

    @Test
    public void testValidate_notFound() throws Exception {
        final ComponentName component = new ComponentName("package", "class");
        when(mPackageManager.getServiceInfo(eq(component), anyInt()))
                .thenThrow(new PackageManager.NameNotFoundException());
        assertThat(mDreamValidator.validate(component, 0)).isFalse();
    }

    @Test
    public void testValidate_missingPermission() throws Exception {
        final ComponentName component = new ComponentName("package", "class");
        final ServiceInfo serviceInfo = new ServiceInfo();
        serviceInfo.applicationInfo = new ApplicationInfo();
        serviceInfo.applicationInfo.targetSdkVersion = Build.VERSION_CODES.LOLLIPOP;
        serviceInfo.permission = "other.permission";

        when(mPackageManager.getServiceInfo(eq(component), anyInt())).thenReturn(serviceInfo);
        assertThat(mDreamValidator.validate(component, 0)).isFalse();
    }

    @Test
    public void testValidate_valid() throws Exception {
        final ComponentName component = new ComponentName("package", "class");
        final ServiceInfo serviceInfo = new ServiceInfo();
        serviceInfo.applicationInfo = new ApplicationInfo();
        serviceInfo.applicationInfo.targetSdkVersion = Build.VERSION_CODES.LOLLIPOP;
        serviceInfo.permission = BIND_DREAM_SERVICE;

        when(mPackageManager.getServiceInfo(eq(component), anyInt())).thenReturn(serviceInfo);
        assertThat(mDreamValidator.validate(component, 0)).isTrue();
    }

    @Test
    public void testValidate_legacyApp() throws Exception {
        final ComponentName component = new ComponentName("package", "class");
        final ServiceInfo serviceInfo = new ServiceInfo();
        serviceInfo.applicationInfo = new ApplicationInfo();
        serviceInfo.applicationInfo.targetSdkVersion = Build.VERSION_CODES.KITKAT;
        serviceInfo.permission = "other.permission"; // Permission not required for legacy apps

        when(mPackageManager.getServiceInfo(eq(component), anyInt())).thenReturn(serviceInfo);
        assertThat(mDreamValidator.validate(component, 0)).isTrue();
    }
}
