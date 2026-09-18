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

package android.service.personalcontext.embedded;

import static android.view.View.MeasureSpec.makeMeasureSpec;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteException;
import android.service.personalcontext.IOpCallback;
import android.service.personalcontext.RenderToken;
import android.service.personalcontext.insight.BundleInsight;
import android.service.personalcontext.insight.PublishedContextInsight;
import android.service.personalcontext.insight.PublishedContextInsightWrapper;
import android.service.personalcontext.testutil.FakeExecutor;
import android.view.Display;
import android.view.SurfaceControlViewHost;
import android.view.View;
import android.view.ViewTreeObserver;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.UUID;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class InsightSurfaceVisualizerServiceTest {
    @Mock private InsightSurfaceClientInfo mClientInfo;
    @Mock private TestInsightSurfaceVisualizerService.Monitor mMonitor;
    @Mock private IVisualizationResult mResult;
    @Mock private Context mContext;
    @Mock private Display mDisplay;
    @Mock private InsightSurfaceVisualizerService.RootView mRootView;
    @Mock private SurfaceControlViewHost mSurfaceControlViewHost;
    @Mock ViewTreeObserver mViewTreeObserver;

    @Mock private IOpCallback mOpCallback;
    private final PublishedContextInsightWrapper mInsight =
            new PublishedContextInsightWrapper(new PublishedContextInsight(
                    new BundleInsight.Builder().build(), UUID.randomUUID()));
    private final RenderToken mRenderToken = new RenderToken(UUID.randomUUID(), null);

    private final FakeExecutor mFakeExecutor = new FakeExecutor();

    private final InsightSurfaceVisualizerService.Injector mInjector =
            new InsightSurfaceVisualizerService.Injector() {
                @Override
                public Context getDisplayContext() {
                    return mContext;
                }

                @Override
                public Display getDisplay() {
                    return mDisplay;
                }

                @Override
                public InsightSurfaceVisualizerService.SurfaceControlViewHostFactory
                        getSurfaceControlViewHostFactory() {
                    return (context, display, inputTransferToken) -> mSurfaceControlViewHost;
                }

                @Override
                public InsightSurfaceVisualizerService.RootView createRootView(
                        Context context,
                        InsightSurfaceClientInfo clientInfo,
                        SurfaceControlViewHost host) {
                    return mRootView;
                }
            };

    private static class TestInsightSurfaceVisualizerService
            extends InsightSurfaceVisualizerService {
        /**
         * An interface implemented to be informed when the corresponding methods in
         * {@link TestInsightSurfaceVisualizerService} are invoked.
         */
        interface Monitor {
            void onClientConnected(InsightSurfaceClientInfo client);
            void onClientDisconnected(InsightSurfaceClientInfo client);
            void onCreateEmbeddedView(InsightSurfaceClientInfo client);
        }

        private final Monitor mMonitor;
        private View mView;

        TestInsightSurfaceVisualizerService(Monitor monitor, Injector injector, View view) {
            super(injector);
            mMonitor = monitor;
            mView = view;
        }

        public void setView(View view) {
            mView = view;
        }

        @Override
        public void onClientConnected(@NonNull InsightSurfaceClientInfo client) {
            mMonitor.onClientConnected(client);
        }

        @Override
        public void onClientDisconnected(@NonNull InsightSurfaceClientInfo client) {
            mMonitor.onClientDisconnected(client);
        }

        @Override
        public View onCreateEmbeddedView(
                @NonNull Context context,
                @NonNull PublishedContextInsight insights,
                @NonNull RenderToken renderToken,
                @NonNull InsightSurfaceClientInfo client) {
            mMonitor.onCreateEmbeddedView(client);
            return mView;
        }
    }

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        when(mClientInfo.getId()).thenReturn(UUID.randomUUID());
        when(mSurfaceControlViewHost.getView()).thenReturn(mRootView);
        when(mRootView.getViewTreeObserver()).thenReturn(mViewTreeObserver);
    }


    @Test
    public void testOnBind() {
        final TestInsightSurfaceVisualizerService service =
                new TestInsightSurfaceVisualizerService(mMonitor, mInjector, null);
        service.onCreate();

        final Intent serviceIntent = new Intent(InsightSurfaceVisualizerService.SERVICE_INTERFACE);
        assertThat(service.onBind(serviceIntent)).isNotNull();

        Intent wrongIntent = new Intent("wrongIntent");
        assertThat(service.onBind(wrongIntent)).isNull();
    }

    @Test
    public void testOnClientConnectedCalled() throws RemoteException {
        final TestInsightSurfaceVisualizerService service = createVisualizer(mock(View.class));
        final IInsightSurfaceVisualizer visualizer = bind(service);
        visualizer.createVisualizationForClient(mInsight, mClientInfo, mRenderToken, mResult,
                mOpCallback);
        mFakeExecutor.runAll();
        verify(mMonitor).onClientConnected(mClientInfo);
    }

    @Test
    public void testOnClientDisconnectedCalled() throws RemoteException {
        final TestInsightSurfaceVisualizerService service = createVisualizer(mock(View.class));
        final IInsightSurfaceVisualizer visualizer = bind(service);
        visualizer.createVisualizationForClient(mInsight, mClientInfo, mRenderToken, mResult,
                mOpCallback);
        mFakeExecutor.runAll();
        visualizer.onClientDisconnected(mClientInfo, mOpCallback);
        mFakeExecutor.runAll();
        verify(mMonitor).onClientDisconnected(mClientInfo);
    }

    @Test
    public void testOnCreateEmbeddedView() throws RemoteException {
        final TestInsightSurfaceVisualizerService service = createVisualizer();
        final IInsightSurfaceVisualizer visualizer = bind(service);

        visualizer.createVisualizationForClient(mInsight, mClientInfo, mRenderToken, mResult,
                mOpCallback);
        mFakeExecutor.runAll();
        verify(mMonitor).onCreateEmbeddedView(mClientInfo);
    }

    @Test
    public void testOnCreateEmbeddedView_nullView_disconnectsClient() throws RemoteException {
        final TestInsightSurfaceVisualizerService service = createVisualizer(mock(View.class));
        final IInsightSurfaceVisualizer visualizer = bind(service);

        // First, return a view so that the client becomes connected.
        visualizer.createVisualizationForClient(
                mInsight, mClientInfo, mRenderToken, mResult, mOpCallback);
        mFakeExecutor.runAll();

        // Now return null, so the client can be disconnected.
        service.setView(null);
        visualizer.createVisualizationForClient(
                mInsight, mClientInfo, mRenderToken, mResult, mOpCallback);
        mFakeExecutor.runAll();

        verify(mClientInfo).onSurfaceReleased(any());
        verify(mMonitor).onClientDisconnected(mClientInfo);
    }

    @Test
    public void testOnCreateEmbeddedView_nonNullView_doesNotDisconnectClient()
            throws RemoteException {
        final TestInsightSurfaceVisualizerService service = createVisualizer(mock(View.class));
        final IInsightSurfaceVisualizer visualizer = bind(service);

        visualizer.createVisualizationForClient(
                mInsight, mClientInfo, mRenderToken, mResult, mOpCallback);
        mFakeExecutor.runAll();
        verify(mMonitor).onCreateEmbeddedView(mClientInfo);
        mFakeExecutor.runAll();
        verify(mMonitor, never()).onClientDisconnected(mClientInfo);
    }

    @Test
    public void testOnCreateEmbeddedView_callsSurfaceCreated()
            throws RemoteException {
        final TestInsightSurfaceVisualizerService service = createVisualizer(mock(View.class));
        final IInsightSurfaceVisualizer visualizer = bind(service);

        visualizer.createVisualizationForClient(
                mInsight, mClientInfo, mRenderToken, mResult, mOpCallback);
        mFakeExecutor.runAll();

        final ArgumentCaptor<ViewTreeObserver.OnPreDrawListener>
                onPreDrawListenerArgumentCaptor =
                ArgumentCaptor.forClass(ViewTreeObserver.OnPreDrawListener.class);
        verify(mViewTreeObserver).addOnPreDrawListener(
                onPreDrawListenerArgumentCaptor.capture());
        final ViewTreeObserver.OnPreDrawListener listener =
                onPreDrawListenerArgumentCaptor.getValue();
        listener.onPreDraw();

        verify(mClientInfo).onSurfaceCreated(any(), any());
    }

    @Test
    public void testOnCreateEmbeddedView_returnsDifferentView_callsSurfaceUpdated()
            throws RemoteException {
        final TestInsightSurfaceVisualizerService service = createVisualizer(mock(View.class));
        final IInsightSurfaceVisualizer visualizer = bind(service);

        visualizer.createVisualizationForClient(
                mInsight, mClientInfo, mRenderToken, mResult, mOpCallback);
        mFakeExecutor.runAll();
        // Calling a second time with a different view will call onSurfaceUpdated again.
        service.setView(mock(View.class));
        visualizer.createVisualizationForClient(
                mInsight, mClientInfo, mRenderToken, mResult, mOpCallback);
        mFakeExecutor.runAll();
        verify(mClientInfo).onSurfaceUpdated(any());
    }

    @Test
    public void testOnCreateEmbeddedView_returnsSameView_callsSurfaceUpdated()
            throws RemoteException {
        final View view = mock(View.class);
        final TestInsightSurfaceVisualizerService service = createVisualizer(view);
        final IInsightSurfaceVisualizer visualizer = bind(service);

        // The SurfaceControlViewHost has to return the same view.
        when(mSurfaceControlViewHost.getView()).thenReturn(view);

        // Calling a second time will return the same view.
        visualizer.createVisualizationForClient(
                mInsight, mClientInfo, mRenderToken, mResult, mOpCallback);
        mFakeExecutor.runAll();
        visualizer.createVisualizationForClient(
                mInsight, mClientInfo, mRenderToken, mResult, mOpCallback);
        mFakeExecutor.runAll();
        verify(mClientInfo).onSurfaceUpdated(any());
    }

    @Test
    public void testCallbackOnResult_false() throws RemoteException {
        final TestInsightSurfaceVisualizerService service = createVisualizer();
        // By not passing a View to createVisualizer(), we cause it to call the callback with
        // "false" (because it has no View to return).
        final IInsightSurfaceVisualizer visualizer = bind(service);

        visualizer.createVisualizationForClient(mInsight, mClientInfo, mRenderToken, mResult,
                mOpCallback);
        mFakeExecutor.runAll();
        verify(mMonitor).onCreateEmbeddedView(mClientInfo);
        // Since the visualizer was created without a View to create, it will call the callback
        // with "false" to indicate that View creation failed.
        verify(mResult).onResult(false);
    }

    @Test
    public void testCallbackOnResult_true() throws RemoteException {
        final TestInsightSurfaceVisualizerService service = createVisualizer(mock(View.class));
        // By passing a View to createVisualizer(), we cause it to call the callback with "true"
        // (because it has a View to return).
        final IInsightSurfaceVisualizer visualizer = bind(service);
        visualizer.createVisualizationForClient(mInsight, mClientInfo, mRenderToken, mResult,
                mOpCallback);
        mFakeExecutor.runAll();
        verify(mMonitor).onCreateEmbeddedView(mClientInfo);
        // Since the visualizer was created with a View to create, it will call the callback
        // with "true" to indicate that View creation succeeded.
        verify(mResult).onResult(true);
    }

    @Test
    public void testOnSizeChanged_whenViewSizeChanges() {
        final InsightSurfaceVisualizerService.RootView rootView =
                new InsightSurfaceVisualizerService.RootView(
                        ApplicationProvider.getApplicationContext(),
                        mClientInfo,
                        mSurfaceControlViewHost);
        when(mClientInfo.getMeasureSpecWidth()).thenReturn(
                makeMeasureSpec(100, View.MeasureSpec.EXACTLY));
        when(mClientInfo.getMeasureSpecHeight()).thenReturn(
                makeMeasureSpec(100, View.MeasureSpec.EXACTLY));

        final View childView = new View(ApplicationProvider.getApplicationContext());
        rootView.setContentView(childView);
        rootView.executeOnRequestLayoutAction();

        verify(mClientInfo).onSizeChanged(anyInt(), anyInt());
    }

    @Test
    public void testSetView_setsFocusableFalse() throws RemoteException {
        final TestInsightSurfaceVisualizerService service = createVisualizer(mock(View.class));
        final IInsightSurfaceVisualizer visualizer = bind(service);
        visualizer.createVisualizationForClient(mInsight, mClientInfo, mRenderToken, mResult,
                mOpCallback);
        mFakeExecutor.runAll();

        ArgumentCaptor<SurfaceControlViewHost.LayoutParams> layoutParamsCaptor =
                ArgumentCaptor.forClass(SurfaceControlViewHost.LayoutParams.class);
        verify(mSurfaceControlViewHost).setView(any(), layoutParamsCaptor.capture());
        assertThat(layoutParamsCaptor.getValue().isFocusable()).isFalse();
    }

    private TestInsightSurfaceVisualizerService createVisualizer() {
        return createVisualizer(null);
    }

    private TestInsightSurfaceVisualizerService createVisualizer(View view) {
        final TestInsightSurfaceVisualizerService service =
                new TestInsightSurfaceVisualizerService(mMonitor, mInjector, view);
        service.setExecutor(mFakeExecutor);
        service.onCreate();

        return service;
    }

    private IInsightSurfaceVisualizer bind(TestInsightSurfaceVisualizerService service) {
        final IBinder binder = service.onBind(
                new Intent(InsightSurfaceVisualizerService.SERVICE_INTERFACE));
        return IInsightSurfaceVisualizer.Stub.asInterface(binder);
    }
}
