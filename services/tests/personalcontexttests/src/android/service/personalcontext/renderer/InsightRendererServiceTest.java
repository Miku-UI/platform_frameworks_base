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

package android.service.personalcontext.renderer;

import static com.android.server.personalcontext.util.InsightUtils.fakePublishInsight;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import android.content.Intent;
import android.os.IBinder;
import android.os.ParcelUuid;
import android.os.RemoteException;
import android.service.personalcontext.IOpCallback;
import android.service.personalcontext.RenderToken;
import android.service.personalcontext.insight.BundleInsight;
import android.service.personalcontext.insight.InsightFilter;
import android.service.personalcontext.insight.PublishedContextInsight;
import android.service.personalcontext.insight.PublishedContextInsightWrapper;
import android.service.personalcontext.testutil.FakeExecutor;

import androidx.annotation.NonNull;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;

import java.util.UUID;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class InsightRendererServiceTest {
    private final FakeExecutor mFakeExecutor = new FakeExecutor();
    final Intent mServiceIntent = new Intent(InsightRendererService.SERVICE_INTERFACE);

    @Test
    public void testOnBind() {
        final InsightRendererService service = new InsightRendererService() {
            @NonNull
            @Override
            public InsightFilter onInitializeFilter() {
                return null;
            }

            @Override
            public void onRender(@NonNull PublishedContextInsight insight,
                    @NonNull RenderToken renderToken) {
            }
        };

        service.setExecutor(mFakeExecutor);

        assertThat(service.onBind(mServiceIntent)).isNotNull();
    }

    @Test
    public void testOnRenderCalledWithRenderToken() throws RemoteException {
        final InsightRendererService service = new InsightRendererService() {
            @NonNull
            @Override
            public InsightFilter onInitializeFilter() {
                return null;
            }

            @Override
            public void onRender(@NonNull PublishedContextInsight insight,
                    @NonNull RenderToken renderToken) {
            }
        };

        service.setExecutor(mFakeExecutor);

        final IInsightRenderer renderer = (IInsightRenderer) service.onBind(mServiceIntent);

        final PublishedContextInsight insight = fakePublishInsight(
                new BundleInsight.Builder().build());
        // prime the renderer by getting the filter first
        renderer.getFilter(new ParcelUuid(UUID.randomUUID()), mock(IGetFilterCallback.class),
                mock(IOpCallback.class));
        mFakeExecutor.runAll();

        // Should be able to fetch token now that the service is set up.
        final RenderToken renderToken = service.mintRenderToken();

        final IOpCallback callback = mock(IOpCallback.Stub.class);
        renderer.render(new ParcelUuid(UUID.randomUUID()),
                new PublishedContextInsightWrapper(insight),
                renderToken,
                callback);
        mFakeExecutor.runAll();
        verify(callback).signalCompletion();
    }

    @Test
    public void mintRenderToken_beforeConfigured_throwsIllegalStateException() {
        // This test verifies that calling mintRenderToken before the service is configured
        // (i.e., before onConnected is called) results in an IllegalStateException.
        // The service is instantiated in setUp() but not configured.

        final InsightRendererService service =
                mock(InsightRendererService.class, Answers.CALLS_REAL_METHODS);
        final IBinder binder = service.onBind(new Intent(InsightRendererService.SERVICE_INTERFACE));
        final IInsightRenderer renderer = IInsightRenderer.Stub.asInterface(binder);

        // Assert that the expected exception is thrown.
        assertThrows(
                "mintRenderToken should throw IllegalStateException if called before configure",
                IllegalStateException.class,
                () -> service.mintRenderToken("some_tag"));

        // Also test the overload without a tag.
        assertThrows(
                "mintRenderToken should throw IllegalStateException if called before configure",
                IllegalStateException.class,
                service::mintRenderToken);
    }
}
