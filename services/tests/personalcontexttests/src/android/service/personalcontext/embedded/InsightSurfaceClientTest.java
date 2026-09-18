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

import static android.service.personalcontext.embedded.InsightSurfaceSessionException.ERROR_FAILED_TO_CREATE_SESSION;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.RemoteException;
import android.service.personalcontext.PersonalContextManager;
import android.service.personalcontext.hint.ContextHint;
import android.service.personalcontext.insight.ContextInsightWrapper;
import android.view.Display;
import android.view.SurfaceControl;
import android.view.SurfaceControlViewHost.SurfacePackage;
import android.view.View;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class InsightSurfaceClientTest {
    @Mock private Context mContext;
    @Mock private Display mDisplay;
    @Mock private Resources mResources;
    @Mock private Configuration mConfiguration;
    @Mock private SurfacePackage mSurfacePackage;
    @Mock private PersonalContextManager mPersonalContextManager;
    @Mock private InsightSurfaceClient.ClientCallback mClientCallbacks;
    @Mock private IInsightSurfaceSession mSession;
    @Mock private SurfaceControl mSurfaceControl;
    private final Executor mExecutor = Runnable::run;
    private InsightSurfaceClient mClient;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);

        when(mContext.getDisplay()).thenReturn(mDisplay);
        when(mContext.getResources()).thenReturn(mResources);
        when(mResources.getConfiguration()).thenReturn(mConfiguration);
        when(mSurfacePackage.getSurfaceControl()).thenReturn(mSurfaceControl);
        when(mSurfaceControl.isSameSurface(mSurfaceControl)).thenReturn(true);

        when(mContext.getSystemService(PersonalContextManager.class))
                .thenReturn(mPersonalContextManager);

        mClient =
                new InsightSurfaceClient.Builder(mContext)
                        .build();
    }

    @Test
    public void testSurfaceCreated() throws RemoteException {
        mClient.register(mExecutor, mClientCallbacks);
        final ArgumentCaptor<InsightSurfaceClientInfo> clientInfoCaptor =
                ArgumentCaptor.forClass(InsightSurfaceClientInfo.class);
        verify(mPersonalContextManager).registerInsightSurfaceClient(
                clientInfoCaptor.capture());

        final IInsightSurfaceClient client = clientInfoCaptor.getValue().getClient();
        client.onSurfaceCreated(mSurfacePackage, mSession);
        verify(mClientCallbacks).onSessionCreated(any(InsightSurfaceSession.class));
    }

    @Test
    public void testSurfaceDestroyed() throws RemoteException {
        mClient.register(mExecutor, mClientCallbacks);
        final ArgumentCaptor<InsightSurfaceClientInfo> clientInfoCaptor =
                ArgumentCaptor.forClass(InsightSurfaceClientInfo.class);
        verify(mPersonalContextManager).registerInsightSurfaceClient(clientInfoCaptor.capture());

        final IInsightSurfaceClient client = clientInfoCaptor.getValue().getClient();
        client.onSurfaceCreated(mSurfacePackage, mSession);
        client.onSurfaceReleased(mSurfacePackage);
        verify(mClientCallbacks).onSessionDestroyed(any(InsightSurfaceSession.class));
    }

    @Test
    public void testSurfaceUpdated() throws RemoteException {
        mClient.register(mExecutor, mClientCallbacks);

        final IInsightSurfaceClient client = mClient.getClientInfo().getClient();
        client.onSurfaceCreated(mSurfacePackage, mSession);
        client.onSurfaceUpdated(mSurfacePackage);
        verify(mClientCallbacks).onSessionUpdated(any(InsightSurfaceSession.class));
    }

    @Test
    public void testSurfaceUpdated_nullSession() throws RemoteException {
        mClient.register(mExecutor, mClientCallbacks);

        final IInsightSurfaceClient client = mClient.getClientInfo().getClient();
        client.onSurfaceCreated(mSurfacePackage, mSession);
        client.onSurfaceReleased(mSurfacePackage);
        client.onSurfaceUpdated(mSurfacePackage);
        verify(mClientCallbacks, never()).onSessionUpdated(any(InsightSurfaceSession.class));
    }

    @Test
    public void testSurfaceUpdated_nullSurfaceControl() throws RemoteException {
        mClient.register(mExecutor, mClientCallbacks);

        when(mSurfacePackage.getSurfaceControl()).thenReturn(null);

        final IInsightSurfaceClient client = mClient.getClientInfo().getClient();
        client.onSurfaceCreated(mSurfacePackage, mSession);
        client.onSurfaceUpdated(mSurfacePackage);
        verify(mClientCallbacks, never()).onSessionUpdated(any(InsightSurfaceSession.class));
    }

    @Test
    public void testSurfaceUpdated_wrongSurfaceControl() throws RemoteException {
        mClient.register(mExecutor, mClientCallbacks);

        final SurfaceControl wrongSurfaceControl = mock(SurfaceControl.class);
        final SurfacePackage wrongSurfacePackage = mock(SurfacePackage.class);
        when(wrongSurfacePackage.getSurfaceControl()).thenReturn(wrongSurfaceControl);

        when(mSurfaceControl.isSameSurface(wrongSurfaceControl)).thenReturn(false);

        final IInsightSurfaceClient client = mClient.getClientInfo().getClient();
        client.onSurfaceCreated(mSurfacePackage, mSession);

        assertThrows(
                IllegalStateException.class,
                () -> client.onSurfaceUpdated(wrongSurfacePackage));
    }

    @Test
    public void testInsightSurfaceClientCreation() {
        final InsightSurfaceClient client =
                new InsightSurfaceClient.Builder(mContext).build();

        assertThat(client.getReceivers()).isEmpty();
    }

    @Test
    public void testOnReceiveInsight() throws RemoteException {
        final boolean[] insightReceived = {false};
        final InsightSurfaceClient.InsightReceiver receiver = insight -> {
            insightReceived[0] = true;
            return true;
        };

        final InsightSurfaceClient client =
                new InsightSurfaceClient.Builder(mContext)
                        .addReceiver(receiver)
                        .build();
        client.register(mExecutor, mClientCallbacks);

        final ContextInsightWrapper insightWrapper = mock(ContextInsightWrapper.class);
        client.getClientInfo().getClient().onReceiveInsight(insightWrapper);
        assertThat(insightReceived[0]).isTrue();
    }

    @Test
    public void testOnSizeChangedCallback() throws RemoteException {
        mClient.register(mExecutor, mClientCallbacks);
        mClient.getClientInfo().getClient().onSizeChanged(0, 0);
        verify(mClientCallbacks).onSizeChanged(0, 0);
    }

    @Test
    public void testInsightSurfaceClientCreation_withMeasureSpecs() {
        final int widthMeasureSpec =
                View.MeasureSpec.makeMeasureSpec(100, View.MeasureSpec.EXACTLY);
        final int heightMeasureSpec =
                View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY);
        final InsightSurfaceClient client =
                new InsightSurfaceClient.Builder(mContext)
                        .setMeasureSpecs(widthMeasureSpec, heightMeasureSpec)
                        .build();

        assertThat(client.getMeasureSpecWidth()).isEqualTo(widthMeasureSpec);
        assertThat(client.getMeasureSpecHeight()).isEqualTo(heightMeasureSpec);
    }

    @Test
    public void testInsightSurfaceClientCreation_withBackgroundColor() {
        final Color backgroundColor = Color.valueOf(Color.RED);
        final InsightSurfaceClient client =
                new InsightSurfaceClient.Builder(mContext)
                        .setBackgroundColor(backgroundColor)
                        .build();

        assertThat(client.getBackgroundColor()).isEqualTo(backgroundColor);
    }

    @Test
    public void testInsightSurfaceClientCreation_withNestedScrollAxes() {
        final int nestedScrollAxes = View.SCROLL_AXIS_HORIZONTAL | View.SCROLL_AXIS_VERTICAL;
        final InsightSurfaceClient client =
                new InsightSurfaceClient.Builder(mContext)
                        .setNestedScrollAxes(nestedScrollAxes)
                        .build();

        assertThat(client.getNestedScrollAxes()).isEqualTo(nestedScrollAxes);
    }

    @Test
    public void testInsightSurfaceClientCreation_withNestedScrollAxisLocked() {
        final boolean nestedScrollAxisLocked = true;
        final InsightSurfaceClient client =
                new InsightSurfaceClient.Builder(mContext)
                        .setNestedScrollAxisLocked(nestedScrollAxisLocked)
                        .build();

        assertThat(client.isNestedScrollAxisLocked()).isEqualTo(nestedScrollAxisLocked);
    }

    @Test
    public void testInsightSurfaceClientCreation_withThemeResourceId() {
        final int themeResourceId = 7;
        final InsightSurfaceClient client =
                new InsightSurfaceClient.Builder(mContext)
                        .setThemeResourceId(themeResourceId)
                        .build();

        assertThat(client.getThemeResourceId()).isEqualTo(themeResourceId);
    }

    @Test
    public void testInsightSurfaceClientCreation_withReceiver() {
        final InsightSurfaceClient.InsightReceiver receiver = insight -> true;

        final InsightSurfaceClient client =
                new InsightSurfaceClient.Builder(mContext)
                        .addReceiver(receiver)
                        .build();

        assertThat(client.getReceivers()).containsExactly(receiver);
    }

    @Test
    public void testExecutorCalledForCallbacks() throws RemoteException {
        final boolean[] executorCalled = {false};
        final Executor executor = command -> {
            executorCalled[0] = true;
            command.run();
        };

        mClient.register(executor, mClientCallbacks);
        mClient.getClientInfo().getClient().onSizeChanged(0, 0);
        assertThat(executorCalled[0]).isTrue();
    }

    @Test
    public void testMainExecutorCalledForCallbacks() throws RemoteException {
        final boolean[] executorCalled = {false};
        final Executor executor = command -> {
            executorCalled[0] = true;
            command.run();
        };
        when(mContext.getMainExecutor()).thenReturn(executor);

        mClient.register(null, mClientCallbacks);
        mClient.getClientInfo().getClient().onSizeChanged(0, 0);
        assertThat(executorCalled[0]).isTrue();
    }

    @Test
    public void testCallbackNotInvokedAfterUnregister_raceCondition() throws RemoteException {
        // Use a custom executor that captures the runnable instead of executing it immediately.
        // This allows us to simulate a race condition where unregister() is called after a
        // callback has been scheduled but before it has been executed.
        final List<Runnable> capturedRunnables = new ArrayList<>();
        final Executor delayedExecutor = capturedRunnables::add;

        // Register the client with the delayed executor.
        mClient.register(delayedExecutor, mClientCallbacks);

        // Trigger a callback. This will schedule a runnable on our delayedExecutor.
        mClient.getClientInfo().getClient().onSizeChanged(100, 200);

        // At this point, the callback has been scheduled but not executed.
        assertThat(capturedRunnables).hasSize(1);
        verifyNoInteractions(mClientCallbacks);

        // Now, unregister the client. This should nullify the internal callbacks object.
        mClient.unregister();

        // Finally, execute the captured runnable.
        capturedRunnables.get(0).run();

        // Verify that the callback was NOT invoked because the client was unregistered before
        // the callback runnable was executed. This tests the null-check inside the executor's
        // runnable.
        verifyNoInteractions(mClientCallbacks);
    }

    @Test
    public void testPublishHints() {
        final InsightSurfaceClient client = new InsightSurfaceClient.Builder(mContext).build();
        final Set<ContextHint> hints = Set.of(mock(ContextHint.class));

        client.publishHints(hints);
        client.register(null, mClientCallbacks);
        client.getClientInfo().onRegistered();

        verify(mPersonalContextManager).publishInsightSurfaceHints(
                eq(hints), any(InsightSurfaceClientInfo.class));
    }

    @Test
    public void testVisualizationErrorCallsOnError() {
        mClient.register(Runnable::run, mClientCallbacks);
        mClient.publishHints(Set.of(mock(ContextHint.class)));
        mClient.getClientInfo().onVisualizationError(ERROR_FAILED_TO_CREATE_SESSION);
        verify(mClientCallbacks).onError(any());
    }
}
