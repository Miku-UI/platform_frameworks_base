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

package com.android.server.personalcontext.embedded;

import static com.android.server.personalcontext.util.InsightUtils.fakePublishInsight;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;
import android.service.personalcontext.Flags;
import android.service.personalcontext.RenderToken;
import android.service.personalcontext.embedded.InsightSurfaceClientInfo;
import android.service.personalcontext.insight.BundleInsight;
import android.service.personalcontext.insight.PublishedContextInsight;

import androidx.annotation.Nullable;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.internal.content.PackageMonitor;
import com.android.server.personalcontext.AccessController;
import com.android.server.personalcontext.OperatingModeProvider;

import com.google.android.collect.Lists;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class VisualizerRegistryTest {
    @Rule
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    @Mock
    private OperatingModeProvider mOperatingModeProvider;
    @Mock
    private AccessController mAccessController;

    private static final String VISUALIZER_SERVICE_NAME = "VisualizerService";
    private static final String VISUALIZER_PACKAGE_NAME = "com.test";

    @Mock
    private VisualizerConnection mVisualizerConnection;
    @Mock
    private InsightSurfaceClientInfo mClient;
    @Mock
    private Consumer<Set<UUID>> mOnVisualizerDiedCallback;

    private VisualizerRegistry mVisualizerRegistry;
    private final TestInjector mTestInjector = new TestInjector();

    private class TestInjector implements VisualizerRegistry.Injector {
        private PackageMonitor mMonitor;
        private final Map<ComponentName, VisualizerConnection> mInstalledServices = new HashMap<>();

        public void addInstalledService(
                ComponentName componentName, VisualizerConnection connection) {
            mInstalledServices.put(componentName, connection);
        }

        public PackageMonitor getPackageMonitor() {
            return mMonitor;
        }

        @Override
        public VisualizerConnection createVisualizerConnection(
                ComponentName componentName, Consumer<Set<UUID>> onVisualizerDiedCallback) {
            return mInstalledServices.get(componentName);
        }

        @Override
        public List<ServiceInfo> fetchVisualizerServiceInfos(@Nullable String packageName) {
            final List<ServiceInfo> infos = Lists.newArrayList();
            mInstalledServices.keySet().forEach(componentName -> {
                infos.add(createServiceInfo(
                        componentName.getPackageName(), componentName.getClassName()));
            });
            return infos;
        }

        @Override
        public void registerPackageMonitor(PackageMonitor monitor) {
            mMonitor = monitor;
        }

        @Override
        public void unregisterPackageMonitor(PackageMonitor monitor) {
            mMonitor = null;
        }

        @Override
        public void queueAction(Runnable action) {
            action.run();
        }
    }

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);

        mVisualizerRegistry = new VisualizerRegistry(mTestInjector);
    }

    @Test
    public void testIsEmpty() {
        assertThat(mVisualizerRegistry.isEmpty()).isTrue();
    }

    @Test
    public void testStartRegisteringVisualizers() {
        assertThat(mVisualizerRegistry.isEmpty()).isTrue();

        final ComponentName componentName =
                new ComponentName(VISUALIZER_PACKAGE_NAME, VISUALIZER_SERVICE_NAME);
        mTestInjector.addInstalledService(componentName, mVisualizerConnection);
        mVisualizerRegistry.startRegisteringVisualizers(mOnVisualizerDiedCallback);
        assertThat(mVisualizerRegistry.isEmpty()).isFalse();
        verify(mVisualizerConnection).onRegistered();
    }

    @Test
    public void testPackageInstalled() {
        // No visualizer services exist at first.
        mVisualizerRegistry.startRegisteringVisualizers(mOnVisualizerDiedCallback);
        assertThat(mVisualizerRegistry.isEmpty()).isTrue();

        final ComponentName componentName =
                new ComponentName(VISUALIZER_PACKAGE_NAME, VISUALIZER_SERVICE_NAME);
        mTestInjector.addInstalledService(componentName, mVisualizerConnection);
        mTestInjector.getPackageMonitor().onPackageAdded(VISUALIZER_PACKAGE_NAME, 0);
        assertThat(mVisualizerRegistry.isEmpty()).isFalse();
    }

    @Test
    public void testPackageUninstalled() {
        final ComponentName componentName =
                new ComponentName(VISUALIZER_PACKAGE_NAME, VISUALIZER_SERVICE_NAME);
        mTestInjector.addInstalledService(componentName, mVisualizerConnection);
        mVisualizerRegistry.startRegisteringVisualizers(mOnVisualizerDiedCallback);
        assertThat(mVisualizerRegistry.isEmpty()).isFalse();

        mTestInjector.getPackageMonitor().onPackageRemoved(VISUALIZER_PACKAGE_NAME, 0);
        assertThat(mVisualizerRegistry.isEmpty()).isTrue();
        verify(mVisualizerConnection).onUnregistered();
    }

    @Test
    public void testCreateVisualizationForClient() {
        final PublishedContextInsight insight =
                fakePublishInsight(new BundleInsight.Builder().build());
        final RenderToken renderToken = new RenderToken(UUID.randomUUID(), null);

        final ComponentName componentName =
                new ComponentName(VISUALIZER_PACKAGE_NAME, VISUALIZER_SERVICE_NAME);
        mTestInjector.addInstalledService(componentName, mVisualizerConnection);
        mVisualizerRegistry.startRegisteringVisualizers(mOnVisualizerDiedCallback);
        mVisualizerRegistry.createVisualizationForClient(insight, mClient, renderToken);
        verify(mVisualizerConnection).createVisualizationForClient(
                eq(insight), eq(mClient), eq(renderToken), any(Consumer.class));
    }

    @Test
    public void testStopRegisteringVisualizers() {
        final TestInjector testInjectorSpy = spy(mTestInjector);
        final VisualizerRegistry visualizerRegistry = new VisualizerRegistry(testInjectorSpy);
        visualizerRegistry.startRegisteringVisualizers(mOnVisualizerDiedCallback);
        verify(testInjectorSpy).registerPackageMonitor(any(PackageMonitor.class));
        visualizerRegistry.stopMonitoringPackagesForVisualizers();
        verify(testInjectorSpy).unregisterPackageMonitor(any(PackageMonitor.class));
    }

    private ServiceInfo createServiceInfo(String packageName, String serviceName) {
        final ServiceInfo serviceInfo = new ServiceInfo();
        serviceInfo.packageName = packageName;
        serviceInfo.name = serviceName;
        serviceInfo.permission = "android.permission.BIND_INSIGHT_SURFACE_VISUALIZER_SERVICE";
        return serviceInfo;
    }

    @Test
    @DisableFlags(Flags.FLAG_ENFORCE_PERSONAL_CONTEXT_ALLOWLIST_ACCESS_CONTROL)
    public void testFetchingVisualizerServices_hasPermissionAndAccess() {
        testFetchingVisualizerServices(true, true);
    }

    @Test
    @DisableFlags(Flags.FLAG_ENFORCE_PERSONAL_CONTEXT_ALLOWLIST_ACCESS_CONTROL)
    public void testFetchingVisualizerServices_serviceLacksPermission() {
        testFetchingVisualizerServices(false, true);
    }

    @Test
    @EnableFlags(Flags.FLAG_ENFORCE_PERSONAL_CONTEXT_ALLOWLIST_ACCESS_CONTROL)
    public void testFetchingVisualizerServices_accessDenied() {
        testFetchingVisualizerServices(true, false);
    }

    private void testFetchingVisualizerServices(boolean hasPermission, boolean hasAccess) {
        final Context context = mock(Context.class);
        final PackageManager packageManager = mock(PackageManager.class);
        when(context.getPackageManager()).thenReturn(packageManager);

        final ResolveInfo resolveInfo = new ResolveInfo();
        resolveInfo.serviceInfo =
                createServiceInfo(VISUALIZER_PACKAGE_NAME, VISUALIZER_SERVICE_NAME);
        if (!hasPermission) {
            resolveInfo.serviceInfo.permission = null;
        }
        when(packageManager.queryIntentServices(any(Intent.class), anyInt()))
                .thenReturn(List.of(resolveInfo));

        when(mOperatingModeProvider.filterAccessFlags(anyInt()))
                .thenAnswer(invocation -> invocation.getArguments()[0]);
        when(mAccessController.isServiceAllowed(
                any(), eq(AccessController.ACCESS_REGISTER_VISUALIZER
                        | AccessController.ACCESS_RECEIVE_INSIGHTS_PERMISSION
                        | AccessController.ACCESS_RECEIVE_INSIGHTS_ALLOWLIST)))
                .thenReturn(hasAccess);

        final VisualizerRegistry.DefaultInjector injector =
                new VisualizerRegistry.DefaultInjector(
                        context, mOperatingModeProvider, mAccessController, Runnable::run);
        final List<ServiceInfo> services =
                injector.fetchVisualizerServiceInfos(VISUALIZER_PACKAGE_NAME);
        assertThat(services).hasSize(hasPermission && hasAccess ? 1 : 0);
    }
}
