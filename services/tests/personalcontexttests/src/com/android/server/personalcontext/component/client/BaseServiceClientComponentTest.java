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

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ServiceInfo;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.ParcelUuid;
import android.os.RemoteException;
import android.os.UserHandle;
import android.service.personalcontext.IOpCallback;
import android.util.Slog;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.server.personalcontext.AccessController;
import com.android.server.personalcontext.OperatingModeProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.Stack;
import java.util.UUID;
import java.util.concurrent.Executor;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class BaseServiceClientComponentTest {

    @Mock
    private Context mContext;
    private final FakeExecutor mFakeExecutor = new FakeExecutor();

    @Mock private UserHandle mUserHandle;
    @Mock private AccessController mAccessController;

    private HandlerThread mTestHandlerThread;

    private Handler mTestHandler;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mTestHandlerThread = new HandlerThread("TestHandlerThread");
        mTestHandlerThread.start();
        mTestHandler = new Handler(mTestHandlerThread.getLooper());
    }

    private static class TestServiceClientComponent
            extends BaseServiceClientComponent<ITestService> {
        TestServiceClientComponent(
                Context context,
                AccessController accessController,
                UUID componentId,
                ServiceInfo serviceInfo,
                UserHandle userHandle, Executor executor, Handler handler) {
            super(
                    context,
                    accessController,
                    componentId,
                    serviceInfo,
                    userHandle,
                    executor,
                    handler,
                    new OperatingModeProvider());
        }

        @Override
        protected ITestService getServiceWrapper(IBinder binder) {
            return ITestService.Stub.asInterface(binder);
        }

        @Override
        protected void initializeClient(ITestService client) throws RemoteException {
        }

        public void runOp() {
            runWithScopedBinder((binder, opCallback) -> {
                try {
                    binder.runOp(getParcelComponentId(), opCallback);
                } catch (RemoteException e) {
                    Slog.w(TAG, this + " runOp() failed", e);
                }
            });
        }
    }

    @Test
    public void unbindAfterAllCompletions() throws RemoteException {
        final ServiceInfo serviceInfo = new ServiceInfo();
        serviceInfo.packageName = "com.foo.bar";
        serviceInfo.name = "baz";
        final Stack<IOpCallback> receivedCallbacks = new Stack<>();

        final ITestService.Stub testServiceBinder = new ITestService.Stub() {
            @Override
            public void runOp(ParcelUuid componentId, IOpCallback opCallback) {
                receivedCallbacks.push(opCallback);
            }
        };

        // Request an operation on service, opening the connection.
        final TestServiceClientComponent component = new TestServiceClientComponent(
                mContext,
                mAccessController,
                UUID.randomUUID(),
                serviceInfo,
                mUserHandle,
                mFakeExecutor,
                mTestHandler);
        component.runOp();
        mFakeExecutor.runAll();
        final ArgumentCaptor<ServiceConnection> connectionCaptor = ArgumentCaptor.forClass(
                ServiceConnection.class);
        verify(mContext).bindServiceAsUser(any(), connectionCaptor.capture(), anyInt(), any());
        connectionCaptor.getValue().onServiceConnected(mock(ComponentName.class),
                testServiceBinder.asBinder());

        // Ensure request was made, capture callback
        mFakeExecutor.runAll();
        assertThat(receivedCallbacks).hasSize(1);

        // Make another request, capture this callback as well.
        component.runOp();
        mFakeExecutor.runAll();
        assertThat(receivedCallbacks).hasSize(2);

        // Close one request, ensure binding connection left open.
        receivedCallbacks.pop().signalCompletion();
        mFakeExecutor.runAll();
        verify(mContext, never()).unbindService(any());

        // Cancel last request, making sure that the connection is closed.
        receivedCallbacks.pop().signalCompletion();
        mFakeExecutor.runAll();
        verify(mContext).unbindService(any());
    }

    @Test
    public void testBindOnRequest() {
        final ServiceInfo serviceInfo = new ServiceInfo();
        serviceInfo.packageName = "com.foo.bar";
        serviceInfo.name = "baz";
        TestServiceClientComponent component = new TestServiceClientComponent(
                mContext,
                mAccessController,
                UUID.randomUUID(),
                serviceInfo,
                mUserHandle,
                mFakeExecutor,
                mTestHandler);
        component.runOp();
        mFakeExecutor.runAll();
        ArgumentCaptor<Intent> intentArgumentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(mContext).bindServiceAsUser(intentArgumentCaptor.capture(), any(), anyInt(),
                eq(mUserHandle));
        assertThat(intentArgumentCaptor.getValue().getComponent())
                .isEqualTo(serviceInfo.getComponentName());
    }

    private static class FakeExecutor implements Executor {
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
