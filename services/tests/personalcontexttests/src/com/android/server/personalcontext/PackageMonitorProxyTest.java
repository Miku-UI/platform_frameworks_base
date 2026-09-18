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

package com.android.server.personalcontext;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.pm.PackageManager;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.internal.content.PackageMonitor;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Set;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class PackageMonitorProxyTest {
    @Mock private Context mContext;
    @Mock private PackageManager mPackageManager;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        when(mContext.getPackageManager()).thenReturn(mPackageManager);
    }

    @Test
    public void testRegistration() {
        final PackageMonitorProxy.PackageMonitorProvider provider =
                mock(PackageMonitorProxy.PackageMonitorProvider.class);
        final PackageMonitorProxy proxy = new PackageMonitorProxy(mContext);
        verify(mPackageManager, never()).registerPackageMonitorCallback(any(), anyInt());

        proxy.addProvider(provider);

        verify(mPackageManager).registerPackageMonitorCallback(any(), anyInt());
        clearInvocations(mPackageManager);
        final PackageMonitorProxy.PackageMonitorProvider provider2 =
                mock(PackageMonitorProxy.PackageMonitorProvider.class);

        proxy.addProvider(provider2);
        verify(mPackageManager, never()).registerPackageMonitorCallback(any(), anyInt());

        proxy.removeProvider(provider2);
        verify(mPackageManager, never()).unregisterPackageMonitorCallback(any());
        proxy.removeProvider(provider);
        verify(mPackageManager).unregisterPackageMonitorCallback(any());
    }

    @Test
    public void testPropagation() {
        final PackageMonitorProxy.PackageMonitorProvider provider =
                mock(PackageMonitorProxy.PackageMonitorProvider.class);
        final PackageMonitorProxy.PackageMonitorProvider provider2 =
                mock(PackageMonitorProxy.PackageMonitorProvider.class);
        final PackageMonitorProxy proxy = new PackageMonitorProxy(mContext);
        verify(mPackageManager, never()).registerPackageMonitorCallback(any(), anyInt());

        proxy.addProvider(provider);
        proxy.addProvider(provider2);

        final PackageMonitor monitor1 = mock(PackageMonitor.class);
        final PackageMonitor monitor2 = mock(PackageMonitor.class);
        final PackageMonitor monitor3 = mock(PackageMonitor.class);

        final int uid = 3000;

        when(provider.getProxiesByUid(eq(3000))).thenReturn(Set.of(monitor1, monitor2));
        when(provider2.getProxiesByUid(eq(3000))).thenReturn(Set.of(monitor3));

        final String packageName = "com.foo.bar";

        proxy.onPackageAdded(packageName, uid);

        verify(monitor1).onPackageAdded(eq(packageName), eq(uid));
        verify(monitor2).onPackageAdded(eq(packageName), eq(uid));
        verify(monitor3).onPackageAdded(eq(packageName), eq(uid));
    }
}
