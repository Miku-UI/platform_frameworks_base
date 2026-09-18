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

package com.android.server.personalcontext.component.client;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.role.RoleManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Handler;
import android.os.UserHandle;
import android.permission.PermissionManager;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;
import android.service.personalcontext.Flags;
import android.service.personalcontext.RenderToken;
import android.service.personalcontext.insight.BundleInsight;
import android.service.personalcontext.insight.PublishedContextInsight;
import android.service.personalcontext.insight.PublishedContextInsightWrapper;
import android.service.personalcontext.renderer.IInsightRenderer;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.server.personalcontext.AccessController;
import com.android.server.personalcontext.OperatingModeProvider;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.Executor;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class ServiceClientRendererTest {
    @Rule public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    private static final String TEST_PACKAGE_NAME = "com.test.package";
    @Mock
    private PackageManager mPackageManager;

    @Mock private Context mContext;
    @Mock private UserHandle mUserHandle;
    @Mock private IInsightRenderer mIInsightRenderer;
    @Mock private PermissionManager mPermissionManager;
    @Mock private Handler mHandler;
    @Mock private AccessController mAccessController;

    private final FakeExecutor mFakeExecutor = new FakeExecutor();

    private ServiceClientRenderer mServiceClientRenderer;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        when(mContext.getPackageManager()).thenReturn(mPackageManager);
        when(mContext.getSystemService(PermissionManager.class)).thenReturn(mPermissionManager);
        when(mContext.getSystemService(eq(RoleManager.class))).thenReturn(mock(RoleManager.class));

        final ServiceInfo serviceInfo = new ServiceInfo();
        serviceInfo.packageName = TEST_PACKAGE_NAME;
        serviceInfo.name = "baz";

        mServiceClientRenderer =
                new ServiceClientRenderer(
                        mContext,
                        mAccessController,
                        UUID.randomUUID(),
                        serviceInfo,
                        mUserHandle,
                        mFakeExecutor,
                        mHandler,
                        new OperatingModeProvider());
        mFakeExecutor.runAll();
    }

    @Test
    public void testRender() throws Exception {
        when(mAccessController.isClientAllowed(
                any(), anyInt())).thenReturn(true);

        BundleInsight insight = new BundleInsight.Builder().build();
        PublishedContextInsight publishedInsight =
                new PublishedContextInsight(insight, UUID.randomUUID());

        // Submit insight to render.
        mServiceClientRenderer.render(publishedInsight, new RenderToken(UUID.randomUUID(), null));
        mFakeExecutor.runAll();
        mServiceClientRenderer.onStarted(mIInsightRenderer);
        mFakeExecutor.runAll();

        // Insight is sent to the renderer service.
        ArgumentCaptor<PublishedContextInsightWrapper> insightCaptor =
                ArgumentCaptor.forClass(PublishedContextInsightWrapper.class);
        verify(mIInsightRenderer).render(any(), insightCaptor.capture(), any(), any());
        assertThat(insightCaptor.getValue().getPublishedContextInsight())
                .isEqualTo(publishedInsight);
    }

    @EnableFlags({
            Flags.FLAG_ENFORCE_PERSONAL_CONTEXT_PCC_ACCESS_CONTROL,
            Flags.FLAG_ENFORCE_PERSONAL_CONTEXT_ROLE_ACCESS_CONTROL,
    })
    @Test
    public void testRender_noPermissions_insightIgnored() throws Exception {
        BundleInsight insight = new BundleInsight.Builder().build();
        PublishedContextInsight publishedInsight =
                new PublishedContextInsight(insight, UUID.randomUUID());

        // Submit insight to render.
        mServiceClientRenderer.render(publishedInsight, new RenderToken(UUID.randomUUID(), null));
        mFakeExecutor.runAll();
        mServiceClientRenderer.onStarted(mIInsightRenderer);
        mFakeExecutor.runAll();

        // Insight is not sent to renderer.
        verify(mIInsightRenderer, never()).render(any(), any(), any(), any());
    }

    private static final class FakeExecutor implements Executor {
        private final Queue<Runnable> mQueue = new ArrayDeque<>();

        @Override
        public void execute(Runnable command) {
            mQueue.add(command);
        }

        public void runAll() {
            while (!mQueue.isEmpty()) {
                mQueue.remove().run();
            }
        }

        public void clearAll() {
            while (!mQueue.isEmpty()) {
                mQueue.remove();
            }
        }
    }
}
