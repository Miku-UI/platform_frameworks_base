/*
 * Copyright 2025 The Android Open Source Project
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

import static com.android.server.personalcontext.AccessController.ACCESS_BIND_CONTEXT_PERMISSION;
import static com.android.server.personalcontext.AccessController.ACCESS_PUBLISH_INSIGHTS_ALLOWLIST;
import static com.android.server.personalcontext.AccessController.ACCESS_RECEIVE_HINTS_ALLOWLIST;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.Manifest;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.os.UserHandle;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;
import android.service.personalcontext.Flags;
import android.service.personalcontext.PersonalContextManager;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.server.personalcontext.component.Refiner;
import com.android.server.personalcontext.component.Renderer;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.UUID;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class ContextComponentManagerTest {
    @Rule
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    @Mock
    private AccessController mAccessController;


    private OperatingModeProvider mOperatingModeProvider = new OperatingModeProvider();

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        mOperatingModeProvider.setMode(PersonalContextManager.OPERATING_MODE_DEFAULT);
    }

    @Test
    @DisableFlags(Flags.FLAG_ENFORCE_PERSONAL_CONTEXT_ALLOWLIST_ACCESS_CONTROL)
    public void testRegistrationEmptyAtStart() {
        final ContextComponentManager manager = new ContextComponentManager(mock(Context.class),
                mock(UserHandle.class), mOperatingModeProvider, mAccessController);

        assertThat(manager.getRefiners()).isEmpty();
        assertThat(manager.getRenderers()).isEmpty();
    }

    @Test
    @DisableFlags(Flags.FLAG_ENFORCE_PERSONAL_CONTEXT_ALLOWLIST_ACCESS_CONTROL)
    public void testRegisterRefiner() {
        final Refiner refiner = mock(Refiner.class);
        when(refiner.getComponentId()).thenReturn(UUID.randomUUID());

        final ContextComponentManager manager = new ContextComponentManager(mock(Context.class),
                mock(UserHandle.class), mOperatingModeProvider, mAccessController);
        manager.register(refiner);

        assertThat(manager.getRefiners()).containsExactly(refiner);
    }

    @Test
    @DisableFlags(Flags.FLAG_ENFORCE_PERSONAL_CONTEXT_ALLOWLIST_ACCESS_CONTROL)
    public void testRegisterRenderer() {
        final Renderer renderer = mock(Renderer.class);
        when(renderer.getComponentId()).thenReturn(UUID.randomUUID());

        final ContextComponentManager manager = new ContextComponentManager(mock(Context.class),
                mock(UserHandle.class), mOperatingModeProvider, mAccessController);
        manager.register(renderer);

        assertThat(manager.getRenderers()).containsExactly(renderer);
    }

    @Test
    @DisableFlags(Flags.FLAG_ENFORCE_PERSONAL_CONTEXT_ALLOWLIST_ACCESS_CONTROL)
    public void testRendererFilter() {
        final Renderer nonMatchingRenderer = mock(Renderer.class);
        when(nonMatchingRenderer
                .hasProperties(anyInt()))
                .thenReturn(false);
        final UserHandle userHandle = mock(UserHandle.class);

        final Renderer matchingRenderer = mock(Renderer.class);
        when(matchingRenderer
                .hasProperties(eq(Renderer.PROPERTY_CAN_RECEIVE_NOTIFICATION_INSIGHTS)))
                .thenReturn(true);

        final ContextComponentManager manager = new ContextComponentManager(mock(Context.class),
                userHandle, mOperatingModeProvider, mAccessController);
        manager.register(nonMatchingRenderer);
        manager.register(matchingRenderer);

        assertThat(manager.getRenderersWithProperties(
                Renderer.PROPERTY_CAN_RECEIVE_NOTIFICATION_INSIGHTS))
                .containsExactly(matchingRenderer);
    }

    @Test
    @DisableFlags(Flags.FLAG_ENFORCE_PERSONAL_CONTEXT_ALLOWLIST_ACCESS_CONTROL)
    public void testRegisterAllComponentsIntent() {
        final Context context = mock(Context.class);
        final PackageManager pm = mock(PackageManager.class);
        final UserHandle userHandle = mock(UserHandle.class);

        when(context.getPackageManager()).thenReturn(pm);

        new ContextComponentManager(context, userHandle, mOperatingModeProvider, mAccessController)
                .registerComponentsForAllPackages();

        final ArgumentCaptor<Intent> intentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(pm, atLeastOnce()).queryIntentServices(intentCaptor.capture(), anyInt());

        assertThat(intentCaptor.getValue().getPackage()).isNull();
    }

    @Test
    @DisableFlags(Flags.FLAG_ENFORCE_PERSONAL_CONTEXT_ALLOWLIST_ACCESS_CONTROL)
    public void testRegisterPackageComponentsIntent() {
        final String packageName = "com.whatever";
        final Context context = mock(Context.class);
        final PackageManager pm = mock(PackageManager.class);
        final UserHandle userHandle = mock(UserHandle.class);

        when(context.getPackageManager()).thenReturn(pm);

        new ContextComponentManager(context, userHandle, mOperatingModeProvider, mAccessController)
                .registerComponentsForPackage(packageName);

        final ArgumentCaptor<Intent> intentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(pm, atLeastOnce()).queryIntentServices(intentCaptor.capture(), anyInt());

        assertThat(intentCaptor.getValue().getPackage()).isEqualTo(packageName);
    }

    @Test
    public void testRegisterNonTestComponentsInTestMode() {
        final String packageName = "com.whatever";
        final Context context = mock(Context.class);
        final PackageManager pm = mock(PackageManager.class);
        final UserHandle userHandle = mock(UserHandle.class);
        final ResolveInfo resolve = new ResolveInfo();
        resolve.serviceInfo = new ServiceInfo();
        resolve.serviceInfo.packageName = packageName;
        resolve.serviceInfo.name = "WhateverService";
        resolve.serviceInfo.applicationInfo = new ApplicationInfo();

        when(context.getPackageManager()).thenReturn(pm);
        when(context.getSystemService(eq(NotificationManager.class)))
                .thenReturn(mock(NotificationManager.class));
        when(pm.queryIntentServices(any(), anyInt())).thenReturn(List.of(resolve));

        final OperatingModeProvider operatingModeProvider = new OperatingModeProvider();
        operatingModeProvider.setMode(PersonalContextManager.OPERATING_MODE_TEST);

        final ContextComponentManager manager = new ContextComponentManager(context, userHandle,
                operatingModeProvider, mAccessController);
        manager.registerComponentsForAllPackages();

        // Nothing should be registered as the test attribute should be missing.
        assertThat(manager.getRefiners()).isEmpty();
        assertThat(manager.getRenderers()).isEmpty();
    }

    @Test
    public void testRegisterTestComponentsInTestMode() {
        final String packageName = "com.whatever";
        final Context context = mock(Context.class);
        final PackageManager pm = mock(PackageManager.class);
        final UserHandle userHandle = mock(UserHandle.class);
        final ResolveInfo resolve = new ResolveInfo();
        resolve.serviceInfo = new ServiceInfo();
        resolve.serviceInfo.packageName = packageName;
        resolve.serviceInfo.name = "WhateverService";
        resolve.serviceInfo.applicationInfo = new ApplicationInfo();
        resolve.serviceInfo.applicationInfo.flags |= ApplicationInfo.FLAG_TEST_ONLY;
        resolve.serviceInfo.permission = Manifest.permission.BIND_CONTEXT_COMPONENT_SERVICE;

        when(context.getPackageManager()).thenReturn(pm);
        when(context.getSystemService(eq(NotificationManager.class)))
                .thenReturn(mock(NotificationManager.class));
        when(pm.queryIntentServices(any(), anyInt())).thenReturn(List.of(resolve));

        when(mAccessController.isServiceAllowed(eq(resolve.serviceInfo), anyInt()))
                .thenReturn(true);

        final OperatingModeProvider operatingModeProvider = new OperatingModeProvider();
        operatingModeProvider.setMode(PersonalContextManager.OPERATING_MODE_TEST);

        final ContextComponentManager manager = new ContextComponentManager(context, userHandle,
                operatingModeProvider, mAccessController);
        manager.registerComponentsForAllPackages();

        // The above code reports the same service for all requested service types. Because
        // understanders are modeled as refiners this looks like 2 refiners, but 1 of all others.
        assertThat(manager.getRefiners().size()).isEqualTo(2);
        assertThat(manager.getRenderers().size()).isEqualTo(1);
    }

    @Test
    @EnableFlags(Flags.FLAG_ENFORCE_PERSONAL_CONTEXT_ALLOWLIST_ACCESS_CONTROL)
    @DisableFlags({Flags.FLAG_ENFORCE_PERSONAL_CONTEXT_PERMISSIONS,
            Flags.FLAG_ENFORCE_PERSONAL_CONTEXT_PCC_ACCESS_CONTROL })
    public void testRegisterPackageComponentsIntentWithAccessControl() {
        final String packageName = "com.whatever";
        final Context context = mock(Context.class);
        final PackageManager pm = mock(PackageManager.class);
        final NotificationManager notificationManager = mock(NotificationManager.class);
        final UserHandle userHandle = mock(UserHandle.class);
        final ResolveInfo resolve = new ResolveInfo();
        resolve.serviceInfo = new ServiceInfo();
        resolve.serviceInfo.packageName = packageName;
        resolve.serviceInfo.name = "WhateverService";

        when(context.getPackageManager()).thenReturn(pm);
        when(context.getSystemService(NotificationManager.class)).thenReturn(notificationManager);
        when(pm.queryIntentServices(any(), anyInt())).thenReturn(List.of(resolve));

        final ContextComponentManager manager = new ContextComponentManager(context, userHandle,
                mOperatingModeProvider, mAccessController);
        manager.registerComponentsForAllPackages();

        assertThat(manager.getRefiners()).isEmpty();
        assertThat(manager.getRenderers()).isEmpty();

        doAnswer(invocation -> {
            final @AccessController.Access int flags = invocation.getArgument(1);

            if ((flags & ACCESS_RECEIVE_HINTS_ALLOWLIST) == ACCESS_RECEIVE_HINTS_ALLOWLIST) {
                return true;
            }

            if ((flags & ACCESS_PUBLISH_INSIGHTS_ALLOWLIST)
                    == ACCESS_PUBLISH_INSIGHTS_ALLOWLIST) {
                return true;
            }

            return false;
        }).when(mAccessController).isServiceAllowed(eq(resolve.serviceInfo), anyInt());


        when(mAccessController.isClientAllowed(any(), anyInt())).thenReturn(false);
        manager.registerComponentsForAllPackages();

        assertThat(manager.getRefiners()).hasSize(2);
        assertThat(manager.getRenderers()).isEmpty();

        doAnswer(invocation -> {
            final @AccessController.Access int flags = invocation.getArgument(1);

            if ((flags & ACCESS_BIND_CONTEXT_PERMISSION) == ACCESS_BIND_CONTEXT_PERMISSION) {
                return true;
            }

            return false;
        }).when(mAccessController).isServiceAllowed(eq(resolve.serviceInfo), anyInt());

        when(mAccessController.isServiceAllowed(
                        eq(resolve.serviceInfo),
                        eq(ACCESS_BIND_CONTEXT_PERMISSION)))
                .thenReturn(true);

        manager.registerComponentsForAllPackages();

        assertThat(manager.getRenderers()).hasSize(1);
    }
}
